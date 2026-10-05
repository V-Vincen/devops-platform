#!/usr/bin/env python3
"""为指定 K3s Namespace 创建或更新 Nexus Docker Group 拉取凭据。

账号密码只在内存中生成 Kubernetes Secret 清单并通过标准输入交给 kubectl；
不会写入项目文件、终端输出或命令行参数。
"""

from __future__ import annotations

import argparse
import base64
import json
import subprocess
import sys
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[3]
NEXUS_ENV_PATH = PROJECT_ROOT / "infra" / "nexus" / ".env"
DEFAULT_CONTEXT = "k3d-devops-test"
DEFAULT_REGISTRY = "host.docker.internal:18084"
SECRET_NAME = "nexus-docker-group"


def load_env(path: Path) -> dict[str, str]:
    """读取受保护 Nexus 配置，不记录其中的值。"""
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
    """避免把空账号或默认配置写进集群。"""
    value = values.get(key, "").strip()
    if not value:
        raise RuntimeError(f"配置项 {key} 不能为空")
    return value


def run(command: list[str], *, input_bytes: bytes | None = None) -> None:
    """执行 kubectl，失败时不输出可能包含凭据的清单内容。"""
    result = subprocess.run(
        command,
        input=input_bytes,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        message = result.stderr.decode("utf-8", errors="replace").strip().splitlines()
        detail = message[-1] if message else "未返回错误信息"
        raise RuntimeError(f"kubectl 执行失败：{detail}")


def docker_config(registry: str, username: str, password: str) -> str:
    """按 Kubernetes dockerconfigjson 格式创建内存中的认证数据。"""
    token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    content = {"auths": {registry: {"username": username, "password": password, "auth": token}}}
    return base64.b64encode(json.dumps(content, separators=(",", ":")).encode("utf-8")).decode("ascii")


def create_secrets(arguments: argparse.Namespace) -> int:
    """创建 Namespace 级镜像拉取凭据，重复执行仅更新同名 Secret。"""
    values = load_env(NEXUS_ENV_PATH)
    password = required(values, "K3S_PULLER_PASSWORD")
    encoded_config = docker_config(arguments.registry, "k3s-puller", password)
    for namespace in arguments.namespaces:
        run(["kubectl", "--context", arguments.context, "get", "namespace", namespace])
        manifest = {
            "apiVersion": "v1",
            "kind": "Secret",
            "metadata": {"name": SECRET_NAME, "namespace": namespace},
            "type": "kubernetes.io/dockerconfigjson",
            "data": {".dockerconfigjson": encoded_config},
        }
        run(
            ["kubectl", "--context", arguments.context, "apply", "-f", "-"],
            input_bytes=json.dumps(manifest).encode("utf-8"),
        )
        print(f"Namespace {namespace}：Nexus 镜像拉取凭据已创建或更新")
    return 0


def parse_arguments() -> argparse.Namespace:
    """限定默认上下文和 Namespace，避免误写入其他 Kubernetes 集群。"""
    parser = argparse.ArgumentParser(description="创建 Nexus Docker Group 的 K3s 拉取凭据")
    parser.add_argument("--context", default=DEFAULT_CONTEXT, help="目标 Kubernetes 上下文")
    parser.add_argument("--registry", default=DEFAULT_REGISTRY, help="镜像仓库地址")
    parser.add_argument("--namespaces", nargs="+", default=["dev", "test"], help="目标 Namespace")
    return parser.parse_args()


if __name__ == "__main__":
    try:
        raise SystemExit(create_secrets(parse_arguments()))
    except Exception as error:  # noqa: BLE001 - 对终端输出统一脱敏。
        print(f"创建 Nexus 镜像拉取凭据失败：{error}", file=sys.stderr)
        raise SystemExit(1)
