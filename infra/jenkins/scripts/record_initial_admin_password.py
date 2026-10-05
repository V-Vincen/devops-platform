#!/usr/bin/env python3
"""将 Jenkins 首次解锁密码写入已忽略的本机凭据清单。

脚本只从本地 Docker 容器读取一次性密码，并以 600 权限原子更新
``LOCAL_CREDENTIALS.txt``。密码不出现在命令行参数、标准输出或项目配置中。
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[3]
CREDENTIALS_PATH = PROJECT_ROOT / "LOCAL_CREDENTIALS.txt"
START_MARKER = "<!-- JENKINS_BOOTSTRAP_START -->"
END_MARKER = "<!-- JENKINS_BOOTSTRAP_END -->"


def read_initial_password() -> str:
    """读取容器内首次密码，拒绝空值以防写入无效凭据记录。"""
    result = subprocess.run(
        ["docker", "exec", "devops-jenkins", "cat", "/var/jenkins_home/secrets/initialAdminPassword"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError("无法读取 Jenkins 初始密码；请确认容器已完成首次启动")
    password = result.stdout.decode("utf-8", errors="strict").strip()
    if not password:
        raise RuntimeError("Jenkins 初始密码为空，已停止更新凭据清单")
    return password


def replace_bootstrap_section(content: str, password: str) -> str:
    """重复执行时仅替换 Jenkins 区段，不影响用户已有的其他凭据记录。"""
    section = "\n".join(
        (
            START_MARKER,
            "六、Jenkins 本机初始管理员",
            "",
            "访问地址：http://127.0.0.1:18090",
            "账号：admin",
            f"初始解锁密码：{password}",
            "用途/权限：仅用于首次解锁 Jenkins 并创建正式管理员；不得用于 Nexus、K3s、数据库或流水线。",
            "配置来源：infra/jenkins/docker-compose.yml；密码来源：devops-jenkins 容器内 /var/jenkins_home/secrets/initialAdminPassword。",
            "维护规则：首次解锁并创建正式管理员后，更新本段账号信息；如初始密码失效，删除本段而不是尝试重置现有 Jenkins 数据卷。",
            END_MARKER,
        )
    )
    pattern = re.compile(re.escape(START_MARKER) + r".*?" + re.escape(END_MARKER), re.DOTALL)
    if pattern.search(content):
        return pattern.sub(section, content)
    return content.rstrip() + "\n\n" + section + "\n"


def main() -> int:
    """以原子替换更新受保护凭据文件，避免中断时留下半写入文件。"""
    if not CREDENTIALS_PATH.is_file():
        raise RuntimeError(f"未找到本机凭据清单：{CREDENTIALS_PATH}")
    password = read_initial_password()
    updated = replace_bootstrap_section(CREDENTIALS_PATH.read_text(encoding="utf-8"), password)
    descriptor, temporary_name = tempfile.mkstemp(prefix="credentials-", suffix=".tmp", dir=CREDENTIALS_PATH.parent)
    temporary_path = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as temporary_file:
            temporary_file.write(updated)
            temporary_file.flush()
            os.fsync(temporary_file.fileno())
        os.chmod(temporary_path, 0o600)
        os.replace(temporary_path, CREDENTIALS_PATH)
    finally:
        if temporary_path.exists():
            temporary_path.unlink()
    print("Jenkins 初始管理员记录已写入已忽略的本机凭据清单。")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # noqa: BLE001 - 防止将密码或容器输出写入终端。
        print(f"更新 Jenkins 本机凭据记录失败：{error}", file=sys.stderr)
        raise SystemExit(1)
