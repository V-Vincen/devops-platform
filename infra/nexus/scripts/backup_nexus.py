#!/usr/bin/env python3
"""创建可恢复的 Nexus 一致性备份集。

备份会短暂停止 ``devops-nexus``，使 Blob Store、Nexus 运行目录和外部
PostgreSQL 元数据位于同一个恢复点。脚本只创建备份文件，不删除原容器、
原数据卷或原数据库；密码只在进程内读取，绝不写入标准输出或清单文件。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import UTC, datetime
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[3]
NEXUS_DIR = PROJECT_ROOT / "infra" / "nexus"
NEXUS_ENV_PATH = NEXUS_DIR / ".env"
DEFAULT_BACKUP_ROOT = Path.home() / ".codex" / "backups"
NEXUS_CONTAINER = "devops-nexus"
POSTGRES_CONTAINER = "devops-postgres"
VOLUME_NAME = "devops-nexus-data"
FILE_PREFIX = "nexus"
IDENTIFIER_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,62}$")


def load_env(path: Path) -> dict[str, str]:
    """读取受保护配置，不记录其值。"""
    if not path.is_file():
        raise RuntimeError(f"未找到受保护配置：{path}")

    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def required(values: dict[str, str], key: str) -> str:
    """拒绝缺失的关键配置，避免误备份错误数据库。"""
    value = values.get(key, "").strip()
    if not value:
        raise RuntimeError(f"配置项 {key} 不能为空")
    return value


def identifier(value: str, field: str) -> str:
    """限制数据库标识符，防止配置错误扩大操作范围。"""
    if not IDENTIFIER_PATTERN.fullmatch(value):
        raise RuntimeError(f"{field} 不是合法 PostgreSQL 标识符")
    return value


def run(command: list[str], *, env: dict[str, str] | None = None, stdout=None) -> subprocess.CompletedProcess[bytes]:
    """执行子进程；失败时仅返回命令类别，不回显敏感环境变量。"""
    result = subprocess.run(
        command,
        check=False,
        env=env,
        stdout=stdout if stdout is not None else subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        message = result.stderr.decode("utf-8", errors="replace").strip().splitlines()
        detail = message[-1] if message else "未返回错误信息"
        raise RuntimeError(f"命令执行失败（{command[0]}）：{detail}")
    return result


def docker_inspect_running(container: str) -> bool:
    """读取容器运行状态，不改变 Docker 状态。"""
    result = run(["docker", "inspect", "--format", "{{.State.Running}}", container])
    return result.stdout.decode("utf-8").strip().lower() == "true"


def sha256(path: Path) -> str:
    """计算归档校验和，供恢复前检测损坏或误选文件。"""
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def ensure_backup_root(path: Path) -> Path:
    """只允许把本项目备份直接写入统一备份根目录。"""
    resolved = path.expanduser().resolve()
    expected = DEFAULT_BACKUP_ROOT.expanduser().resolve()
    if resolved != expected:
        raise RuntimeError(f"备份目录必须是统一目录：{expected}")
    resolved.mkdir(mode=0o700, parents=True, exist_ok=True)
    return resolved


def wait_for_nexus(bind_address: str, timeout_seconds: int = 420) -> None:
    """等待原 Nexus 恢复健康，避免将容器刚启动误当成备份成功。"""
    status_url = f"http://{bind_address}:18081/service/rest/v1/status"
    deadline = time.monotonic() + timeout_seconds
    last_error = "未开始检查"
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(status_url, timeout=5) as response:
                if response.status == 200:
                    return
                last_error = f"HTTP {response.status}"
        except (OSError, urllib.error.URLError) as error:
            last_error = str(error)
        time.sleep(5)
    raise RuntimeError(f"Nexus 在 {timeout_seconds} 秒内未恢复健康：{last_error}")


def compose_command(*arguments: str) -> list[str]:
    """统一指定 Compose 配置，避免误操作其他 Docker 项目。"""
    return [
        "docker",
        "compose",
        "--env-file",
        str(NEXUS_ENV_PATH),
        "-f",
        str(NEXUS_DIR / "docker-compose.yml"),
        *arguments,
    ]


def create_backup(arguments: argparse.Namespace) -> int:
    """按“停止—归档—校验—恢复启动”顺序生成一致性备份。"""
    backup_root = ensure_backup_root(arguments.backup_root)
    nexus_env = load_env(NEXUS_ENV_PATH)
    database = identifier(required(nexus_env, "NEXUS_DB_NAME"), "NEXUS_DB_NAME")
    database_user = identifier(required(nexus_env, "NEXUS_DB_USER"), "NEXUS_DB_USER")
    database_password = required(nexus_env, "NEXUS_DB_PASSWORD")
    bind_address = required(nexus_env, "NEXUS_BIND_ADDRESS")
    image = required(nexus_env, "NEXUS_IMAGE")

    if bind_address not in {"127.0.0.1", "localhost"}:
        raise RuntimeError("本地备份脚本只允许回环地址 Nexus，避免误操作远程实例")

    run(["docker", "info", "--format", "{{.ServerVersion}}"])
    run(["docker", "volume", "inspect", VOLUME_NAME])
    was_running = docker_inspect_running(NEXUS_CONTAINER)
    if not was_running:
        raise RuntimeError("devops-nexus 当前未运行；请先确认实例状态后再执行一致性备份")

    timestamp = datetime.now(UTC).strftime("%Y%m%d-%H%M%SZ")
    volume_archive = backup_root / f"{FILE_PREFIX}--devops-nexus-data--{timestamp}-backup.tar"
    database_dump = backup_root / f"{FILE_PREFIX}--nexus-database--{timestamp}-backup.dump"
    config_copy = backup_root / f"{FILE_PREFIX}--infra-nexus-.env--{timestamp}-backup.env"
    manifest_path = backup_root / f"{FILE_PREFIX}--manifest--{timestamp}-backup.json"
    output_paths = (volume_archive, database_dump, config_copy, manifest_path)
    if any(path.exists() for path in output_paths):
        raise RuntimeError("本次备份文件名已存在，已停止以避免覆盖历史备份")

    print("备份准备完成：将短暂停止 Nexus，原数据库、原数据卷和制品均不会删除。")
    if arguments.dry_run:
        print("演练模式：未写入备份、未停止 Nexus。")
        return 0

    stopped = False
    try:
        run(compose_command("stop", "nexus"))
        stopped = True
        print("Nexus 已停止，开始归档数据卷和外部数据库。")

        # 使用已固定版本的 Nexus 镜像读取卷，避免引入额外工具镜像或宿主机权限差异。
        run(
            [
                "docker",
                "run",
                "--rm",
                "--user",
                "0",
                "-v",
                f"{VOLUME_NAME}:/source:ro",
                "-v",
                f"{backup_root}:/backup",
                image,
                "tar",
                "--numeric-owner",
                "--xattrs",
                "--acls",
                "-C",
                "/source",
                "-cf",
                f"/backup/{volume_archive.name}",
                ".",
            ]
        )
        os.chmod(volume_archive, 0o600)

        # pg_dump 的自定义归档可由 pg_restore 精确恢复；密码只通过子进程环境传递。
        postgres_env = os.environ.copy()
        postgres_env["PGPASSWORD"] = database_password
        with database_dump.open("wb") as dump_file:
            run(
                [
                    "docker",
                    "exec",
                    "-i",
                    "-e",
                    "PGPASSWORD",
                    POSTGRES_CONTAINER,
                    "pg_dump",
                    "-h",
                    "127.0.0.1",
                    "-U",
                    database_user,
                    "-d",
                    database,
                    "-Fc",
                    "-Z",
                    "9",
                ],
                env=postgres_env,
                stdout=dump_file,
            )
        os.chmod(database_dump, 0o600)
        if database_dump.stat().st_size == 0:
            raise RuntimeError("PostgreSQL 归档为空，已停止生成不完整备份")

        shutil.copyfile(NEXUS_ENV_PATH, config_copy)
        os.chmod(config_copy, 0o600)

        files = {
            "volume_archive": volume_archive.name,
            "database_dump": database_dump.name,
            "protected_config": config_copy.name,
        }
        manifest = {
            "format_version": 1,
            "created_at_utc": datetime.now(UTC).isoformat(),
            "source": {
                "nexus_container": NEXUS_CONTAINER,
                "nexus_data_volume": VOLUME_NAME,
                "postgres_container": POSTGRES_CONTAINER,
                "database": database,
                "database_user": database_user,
                "image": image,
            },
            "files": files,
            "sha256": {
                key: sha256(backup_root / filename)
                for key, filename in files.items()
            },
            "recovery_notes": [
                "恢复必须使用同一备份集中的数据卷归档、数据库归档和受保护配置。",
                "恢复验证仅允许写入隔离数据库、隔离数据卷和临时 Nexus 容器。",
                "本清单不保存任何密码、令牌或受保护配置内容。",
            ],
        }
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        os.chmod(manifest_path, 0o600)
        print("备份集已创建：")
        for path in output_paths:
            print(f"- {path}")
        return 0
    finally:
        if stopped:
            try:
                run(compose_command("up", "-d", "nexus"))
                wait_for_nexus(bind_address)
                print("原 Nexus 已恢复健康。")
            except Exception as error:  # noqa: BLE001 - 必须保留原始恢复异常。
                print(f"警告：原 Nexus 自动恢复失败：{error}", file=sys.stderr)
                raise


def parse_arguments() -> argparse.Namespace:
    """解析显式参数，默认不允许写入非统一备份目录。"""
    parser = argparse.ArgumentParser(description="创建 Nexus 一致性备份集")
    parser.add_argument(
        "--backup-root",
        type=Path,
        default=DEFAULT_BACKUP_ROOT,
        help="统一备份目录，默认 ~/.codex/backups",
    )
    parser.add_argument("--dry-run", action="store_true", help="只检查前置条件，不停止服务、不创建文件")
    return parser.parse_args()


if __name__ == "__main__":
    try:
        raise SystemExit(create_backup(parse_arguments()))
    except Exception as error:  # noqa: BLE001 - 对终端输出统一脱敏。
        print(f"Nexus 备份失败：{error}", file=sys.stderr)
        raise SystemExit(1)
