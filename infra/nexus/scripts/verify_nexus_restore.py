#!/usr/bin/env python3
"""在隔离的数据库、数据卷和临时容器中验证 Nexus 备份可恢复。

脚本不会接触原 ``nexus`` 数据库或 ``devops-nexus-data`` 数据卷。验证完成后仅
停止临时 Nexus 容器，保留隔离容器、卷与数据库供人工复查；清理这些副本必须由
维护人另行确认后执行。
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import UTC, datetime
from pathlib import Path

import psycopg
from psycopg import sql


PROJECT_ROOT = Path(__file__).resolve().parents[3]
NEXUS_DIR = PROJECT_ROOT / "infra" / "nexus"
LOCAL_ENV_PATH = PROJECT_ROOT / "infra" / "local" / ".env"
NEXUS_ENV_PATH = NEXUS_DIR / ".env"
DEFAULT_BACKUP_ROOT = Path.home() / ".codex" / "backups"
POSTGRES_CONTAINER = "devops-postgres"
IDENTIFIER_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,62}$")
RESTORE_PORTS = (28081, 28082, 28083, 28084)


def load_env(path: Path) -> dict[str, str]:
    """读取本机受保护配置，禁止在记录中输出值。"""
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
    """确保恢复不会使用空配置或默认账号。"""
    value = values.get(key, "").strip()
    if not value:
        raise RuntimeError(f"配置项 {key} 不能为空")
    return value


def identifier(value: str, field: str) -> str:
    """将数据库名称、角色和 Schema 收窄到安全标识符。"""
    if not IDENTIFIER_PATTERN.fullmatch(value):
        raise RuntimeError(f"{field} 不是合法 PostgreSQL 标识符")
    return value


def run(command: list[str], *, env: dict[str, str] | None = None, stdin=None) -> subprocess.CompletedProcess[bytes]:
    """统一执行 Docker 与 PostgreSQL 原生恢复工具，不输出环境变量。"""
    result = subprocess.run(
        command,
        check=False,
        env=env,
        stdin=stdin,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        lines = result.stderr.decode("utf-8", errors="replace").strip().splitlines()
        detail = lines[-1] if lines else "未返回错误信息"
        raise RuntimeError(f"命令执行失败（{command[0]}）：{detail}")
    return result


def sha256(path: Path) -> str:
    """恢复前校验文件完整性，拒绝不完整或被替换的备份集。"""
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def require_backup_root(path: Path) -> Path:
    """恢复只接受统一备份目录内的文件，避免误选任意归档。"""
    backup_root = DEFAULT_BACKUP_ROOT.expanduser().resolve()
    candidate = path.expanduser().resolve()
    if candidate.parent != backup_root:
        raise RuntimeError(f"备份清单必须直接位于统一备份目录：{backup_root}")
    return candidate


def load_manifest(path: Path) -> tuple[dict[str, object], dict[str, Path]]:
    """读取并验证备份清单，返回已经通过校验的归档文件。"""
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise RuntimeError(f"备份清单不是有效 JSON：{error}") from error

    if manifest.get("format_version") != 1:
        raise RuntimeError("不支持的备份清单版本")
    file_names = manifest.get("files")
    expected_hashes = manifest.get("sha256")
    if not isinstance(file_names, dict) or not isinstance(expected_hashes, dict):
        raise RuntimeError("备份清单缺少文件或校验和信息")

    files: dict[str, Path] = {}
    for key in ("volume_archive", "database_dump", "protected_config"):
        filename = file_names.get(key)
        expected = expected_hashes.get(key)
        if not isinstance(filename, str) or not isinstance(expected, str):
            raise RuntimeError(f"备份清单缺少 {key} 信息")
        candidate = path.parent / filename
        if candidate.parent != path.parent or not candidate.is_file():
            raise RuntimeError(f"备份文件不存在：{filename}")
        if sha256(candidate) != expected:
            raise RuntimeError(f"备份文件校验失败：{filename}")
        files[key] = candidate
    return manifest, files


def require_free_ports() -> None:
    """隔离恢复固定使用专用端口，避免抢占原 Nexus 的监听地址。"""
    occupied: list[int] = []
    for port in RESTORE_PORTS:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.settimeout(0.2)
            if probe.connect_ex(("127.0.0.1", port)) == 0:
                occupied.append(port)
    if occupied:
        joined = ", ".join(str(port) for port in occupied)
        raise RuntimeError(f"隔离恢复端口已被占用：{joined}")


def restore_jdbc_url(original_url: str, original_database: str, restore_database: str) -> str:
    """仅替换 JDBC URL 的数据库段，保留现有主机、端口和 Schema 参数。"""
    prefix, separator, query = original_url.partition("?")
    marker = f"/{original_database}"
    if not prefix.endswith(marker):
        raise RuntimeError("JDBC URL 与 Nexus 数据库名称不一致，已停止隔离恢复")
    replaced = f"{prefix[:-len(marker)]}/{restore_database}"
    return f"{replaced}{separator}{query}" if separator else replaced


def wait_for_restore(container: str, admin_password: str, timeout_seconds: int = 420) -> tuple[int, tuple[str, ...]]:
    """以管理员 REST API 验证隔离副本的服务和关键仓库已恢复。"""
    status_url = "http://127.0.0.1:28081/service/rest/v1/status"
    repositories_url = "http://127.0.0.1:28081/service/rest/v1/repositories"
    expected_repositories = {
        "maven-releases",
        "maven-snapshots",
        "maven-public",
        "docker-hosted",
        "docker-group",
    }
    authorization = base64.b64encode(f"admin:{admin_password}".encode("utf-8")).decode("ascii")
    deadline = time.monotonic() + timeout_seconds
    last_error = "未开始检查"
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(status_url, timeout=5) as response:
                if response.status != 200:
                    last_error = f"状态接口返回 HTTP {response.status}"
                    time.sleep(5)
                    continue
            request = urllib.request.Request(repositories_url, headers={"Authorization": f"Basic {authorization}"})
            with urllib.request.urlopen(request, timeout=10) as response:
                repositories = json.loads(response.read().decode("utf-8"))
            names = tuple(sorted(item["name"] for item in repositories if isinstance(item, dict) and "name" in item))
            missing = sorted(expected_repositories.difference(names))
            if not missing:
                return len(names), names
            last_error = f"缺少关键仓库：{', '.join(missing)}"
        except (OSError, urllib.error.URLError, urllib.error.HTTPError, json.JSONDecodeError) as error:
            last_error = str(error)
        time.sleep(5)
    raise RuntimeError(f"隔离 Nexus 在 {timeout_seconds} 秒内未达到恢复验证条件：{last_error}")


def create_restore_database(
    local_env: dict[str, str], nexus_env: dict[str, str], source_database: str, restore_database: str
) -> tuple[str, str]:
    """使用 Python 驱动创建唯一数据库，再交给 pg_restore 还原二进制归档。"""
    host = required(nexus_env, "NEXUS_DB_BOOTSTRAP_HOST")
    port = required(nexus_env, "NEXUS_DB_BOOTSTRAP_PORT")
    admin_database = required(local_env, "POSTGRES_DB")
    admin_user = required(local_env, "POSTGRES_USER")
    admin_password = required(local_env, "POSTGRES_PASSWORD")
    if not port.isdecimal() or not 1 <= int(port) <= 65535:
        raise RuntimeError("NEXUS_DB_BOOTSTRAP_PORT 不是有效端口")

    connection = psycopg.connect(
        host=host,
        port=int(port),
        dbname=admin_database,
        user=admin_user,
        password=admin_password,
        connect_timeout=5,
        autocommit=True,
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute("SET statement_timeout = '15s'")
            cursor.execute("SET lock_timeout = '5s'")
            cursor.execute("SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = %s", (source_database,))
            source = cursor.fetchone()
            if source is None:
                raise RuntimeError("源 Nexus 数据库不存在，已停止恢复")
            owner = identifier(str(source[0]), "源数据库所有者")
            cursor.execute("SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = %s)", (restore_database,))
            exists, = cursor.fetchone()
            if exists:
                raise RuntimeError("隔离恢复数据库已存在，已停止以避免覆盖历史恢复副本")
            cursor.execute(
                sql.SQL("CREATE DATABASE {} OWNER {} TEMPLATE template0")
                .format(sql.Identifier(restore_database), sql.Identifier(owner))
            )
            return admin_user, admin_password
    finally:
        connection.close()


def verify_restored_database(
    local_env: dict[str, str], nexus_env: dict[str, str], restore_database: str, schema: str
) -> int:
    """用业务结构交叉验证数据库归档，而不仅检查 pg_restore 的退出码。"""
    connection = psycopg.connect(
        host=required(nexus_env, "NEXUS_DB_BOOTSTRAP_HOST"),
        port=int(required(nexus_env, "NEXUS_DB_BOOTSTRAP_PORT")),
        dbname=restore_database,
        user=required(local_env, "POSTGRES_USER"),
        password=required(local_env, "POSTGRES_PASSWORD"),
        connect_timeout=5,
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = %s)", (schema,))
            schema_exists, = cursor.fetchone()
            if not schema_exists:
                raise RuntimeError("恢复数据库缺少 Nexus Schema")
            cursor.execute("SELECT count(*) FROM pg_tables WHERE schemaname = %s", (schema,))
            table_count, = cursor.fetchone()
            if table_count <= 0:
                raise RuntimeError("恢复数据库的 Nexus Schema 没有表")
            return int(table_count)
    finally:
        connection.close()


def verify_restore(arguments: argparse.Namespace) -> int:
    """执行不可覆盖原环境的完整恢复演练，并生成无敏感信息的结果记录。"""
    manifest_path = require_backup_root(arguments.manifest)
    manifest, files = load_manifest(manifest_path)
    source = manifest.get("source")
    if not isinstance(source, dict):
        raise RuntimeError("备份清单缺少源实例信息")
    source_database = identifier(str(source.get("database", "")), "备份源数据库")
    source_volume = str(source.get("nexus_data_volume", ""))
    if source_volume != "devops-nexus-data":
        raise RuntimeError("备份清单的数据卷不是本项目 Nexus 数据卷")

    local_env = load_env(LOCAL_ENV_PATH)
    nexus_env = load_env(NEXUS_ENV_PATH)
    current_config_hash = sha256(NEXUS_ENV_PATH)
    backup_config_hash = sha256(files["protected_config"])
    if current_config_hash != backup_config_hash and not arguments.allow_config_drift:
        raise RuntimeError("当前受保护配置与备份配置不一致；请先确认配置差异或使用 --allow-config-drift")

    run(["docker", "info", "--format", "{{.ServerVersion}}"])
    run(["docker", "inspect", "devops-postgres"])
    require_free_ports()

    timestamp = datetime.now(UTC).strftime("%Y%m%d%H%M%S")
    restore_database = identifier(f"nexus_restore_{timestamp}", "隔离恢复数据库")
    restore_volume = f"devops-nexus-restore-{timestamp}"
    restore_container = f"devops-nexus-restore-{timestamp}"
    schema = identifier(required(nexus_env, "NEXUS_DB_SCHEMA"), "NEXUS_DB_SCHEMA")
    image = required(nexus_env, "NEXUS_IMAGE")
    restore_jdbc = restore_jdbc_url(
        required(nexus_env, "NEXUS_DATASTORE_NEXUS_JDBCURL"), source_database, restore_database
    )
    record_path = manifest_path.parent / f"nexus--restore-check--{timestamp}-record.json"
    stopped = False

    print("开始隔离恢复：只创建带 restore 标识的新数据库、数据卷与临时 Nexus 容器。")
    try:
        admin_user, admin_password = create_restore_database(local_env, nexus_env, source_database, restore_database)
        postgres_env = os.environ.copy()
        postgres_env["PGPASSWORD"] = admin_password
        with files["database_dump"].open("rb") as dump_file:
            run(
                [
                    "docker",
                    "exec",
                    "-i",
                    "-e",
                    "PGPASSWORD",
                    POSTGRES_CONTAINER,
                    "pg_restore",
                    "-h",
                    "127.0.0.1",
                    "-U",
                    admin_user,
                    "-d",
                    restore_database,
                    "--exit-on-error",
                ],
                env=postgres_env,
                stdin=dump_file,
            )
        table_count = verify_restored_database(local_env, nexus_env, restore_database, schema)

        run(["docker", "volume", "create", restore_volume])
        run(
            [
                "docker",
                "run",
                "--rm",
                "--user",
                "0",
                "-v",
                f"{restore_volume}:/target",
                "-v",
                f"{manifest_path.parent}:/backup:ro",
                image,
                "tar",
                "--numeric-owner",
                "--xattrs",
                "--acls",
                "-C",
                "/target",
                "-xf",
                f"/backup/{files['volume_archive'].name}",
            ]
        )

        jvm_parameters = " ".join(
            (
                f"-Xms{required(nexus_env, 'NEXUS_JVM_HEAP')}",
                f"-Xmx{required(nexus_env, 'NEXUS_JVM_HEAP')}",
                f"-XX:MaxDirectMemorySize={required(nexus_env, 'NEXUS_JVM_DIRECT_MEMORY')}",
                "-Djava.util.prefs.userRoot=/nexus-data/javaprefs",
                "-Dnexus.datastore.enabled=true",
            )
        )
        run(
            [
                "docker",
                "run",
                "-d",
                "--name",
                restore_container,
                "--restart",
                "no",
                "--ulimit",
                "nofile=65536:65536",
                "-p",
                "127.0.0.1:28081:8081",
                "-p",
                "127.0.0.1:28082:8082",
                "-p",
                "127.0.0.1:28083:8083",
                "-p",
                "127.0.0.1:28084:8084",
                "-v",
                f"{restore_volume}:/nexus-data",
                "-e",
                f"NEXUS_DATASTORE_NEXUS_JDBCURL={restore_jdbc}",
                "-e",
                f"NEXUS_DATASTORE_NEXUS_USERNAME={required(nexus_env, 'NEXUS_DB_USER')}",
                "-e",
                f"NEXUS_DATASTORE_NEXUS_PASSWORD={required(nexus_env, 'NEXUS_DB_PASSWORD')}",
                "-e",
                f"NEXUS_DATASTORE_NEXUS_ADVANCED=maximumPoolSize={required(nexus_env, 'NEXUS_DB_MAX_POOL_SIZE')}",
                "-e",
                f"INSTALL4J_ADD_VM_PARAMS={jvm_parameters}",
                image,
            ]
        )
        repository_count, repository_names = wait_for_restore(restore_container, required(nexus_env, "NEXUS_ADMIN_PASSWORD"))

        record = {
            "format_version": 1,
            "verified_at_utc": datetime.now(UTC).isoformat(),
            "result": "passed",
            "backup_manifest": manifest_path.name,
            "backup_config_matches_current": current_config_hash == backup_config_hash,
            "verification": {
                "database_schema": schema,
                "database_table_count": table_count,
                "repository_count": repository_count,
                "repository_names": repository_names,
                "health_endpoint": "http://127.0.0.1:28081/service/rest/v1/status",
            },
            "isolated_resources_retained": {
                "container": restore_container,
                "volume": restore_volume,
                "database": restore_database,
                "status_after_verification": "container_stopped; volume_and_database_retained",
            },
            "cleanup": "隔离恢复资源未自动删除；删除前必须单独确认。",
        }
        record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        os.chmod(record_path, 0o600)
        print("隔离恢复验证通过：")
        print(f"- 数据库表数量：{table_count}")
        print(f"- 恢复仓库数量：{repository_count}")
        print(f"- 验证记录：{record_path}")
        return 0
    finally:
        container_exists = subprocess.run(
            ["docker", "container", "inspect", restore_container],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        ).returncode == 0
        if container_exists:
            run(["docker", "stop", restore_container])
            stopped = True
        if stopped:
            print("隔离 Nexus 容器已停止；隔离数据库和数据卷已保留，未执行删除。")


def parse_arguments() -> argparse.Namespace:
    """要求明确指定备份清单，避免误恢复最新之外的文件。"""
    parser = argparse.ArgumentParser(description="验证 Nexus 备份的隔离恢复能力")
    parser.add_argument("--manifest", type=Path, required=True, help="统一备份目录中的 Nexus 清单文件")
    parser.add_argument(
        "--allow-config-drift",
        action="store_true",
        help="仅在人工确认配置差异后使用；默认要求备份配置与当前配置完全一致",
    )
    return parser.parse_args()


if __name__ == "__main__":
    try:
        raise SystemExit(verify_restore(parse_arguments()))
    except Exception as error:  # noqa: BLE001 - 对终端统一输出脱敏错误。
        print(f"Nexus 隔离恢复验证失败：{error}", file=sys.stderr)
        raise SystemExit(1)
