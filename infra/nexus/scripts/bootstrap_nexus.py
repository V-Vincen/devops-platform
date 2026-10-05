#!/usr/bin/env python3
"""初始化 Nexus 仓库、认证 Realm 与后续自动化账号。

首次执行会从容器中读取一次性 admin.password，并立即替换为 infra/nexus/.env
中的 NEXUS_ADMIN_PASSWORD。后续重复执行使用该受保护配置中的密码，只补齐缺失对象，
不会重置既有账号密码或覆盖不一致的仓库配置。
"""

from __future__ import annotations

import base64
import json
import subprocess
import sys
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


NEXUS_DIR = Path(__file__).resolve().parents[1]
NEXUS_ENV_PATH = NEXUS_DIR / ".env"
NEXUS_API_BASE = "http://127.0.0.1:18081/service/rest/v1"


class NexusApiError(RuntimeError):
    """封装不包含认证信息的 Nexus API 错误。"""


def load_env(path: Path) -> dict[str, str]:
    """读取本地私有配置，不向终端输出任何凭据。"""
    if not path.is_file():
        raise RuntimeError(f"未找到配置文件：{path}")

    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def required(values: dict[str, str], key: str) -> str:
    """拒绝空凭据，避免初始化为不可恢复的半配置状态。"""
    value = values.get(key, "").strip()
    if not value:
        raise RuntimeError(f"配置项 {key} 不能为空")
    return value


def request_api(
    method: str,
    path: str,
    password: str,
    body: Any | None = None,
    content_type: str = "application/json",
) -> Any:
    """调用本机 Nexus API，失败时只保留可诊断的 HTTP 信息。"""
    authorization = base64.b64encode(f"admin:{password}".encode("utf-8")).decode("ascii")
    headers = {"Accept": "application/json", "Authorization": f"Basic {authorization}"}
    payload: bytes | None = None
    if body is not None:
        if content_type == "application/json":
            payload = json.dumps(body).encode("utf-8")
        else:
            payload = str(body).encode("utf-8")
        headers["Content-Type"] = f"{content_type}; charset=utf-8"

    request = Request(f"{NEXUS_API_BASE}{path}", data=payload, headers=headers, method=method)
    try:
        with urlopen(request, timeout=30) as response:
            raw_body = response.read().decode("utf-8")
            if not raw_body:
                return None
            return json.loads(raw_body)
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:500]
        raise NexusApiError(f"{method} {path} 返回 HTTP {error.code}：{detail}") from error
    except URLError as error:
        raise NexusApiError(f"无法连接 Nexus API：{error.reason}") from error


def can_authenticate(password: str) -> bool:
    """通过仓库列表验证管理员密码是否已经生效。"""
    try:
        request_api("GET", "/repositories", password)
        return True
    except NexusApiError as error:
        if "HTTP 401" in str(error):
            return False
        raise


def read_initial_admin_password() -> str:
    """在内存中读取 Nexus 只在首次启动时生成的临时管理员密码。"""
    result = subprocess.run(
        ["docker", "compose", "exec", "-T", "nexus", "cat", "/nexus-data/admin.password"],
        cwd=NEXUS_DIR,
        capture_output=True,
        check=False,
        text=True,
    )
    password = result.stdout.strip()
    if result.returncode != 0 or not password:
        raise RuntimeError("无法读取 Nexus 初始管理员密码；请确认容器首次启动已完成且密码尚未被手工修改")
    return password


def ensure_admin_password(configured_password: str) -> None:
    """首次启动后立即淘汰临时管理员密码，后续执行不重复修改密码。"""
    if can_authenticate(configured_password):
        print("管理员密码：已是受保护配置中的密码")
        return

    initial_password = read_initial_admin_password()
    request_api(
        "PUT",
        "/security/users/admin/change-password",
        initial_password,
        configured_password,
        "text/plain",
    )
    if not can_authenticate(configured_password):
        raise RuntimeError("管理员密码替换后认证失败，初始化已停止")
    print("管理员密码：已替换一次性初始密码")


def repository_payloads() -> dict[str, tuple[str, str, dict[str, Any]]]:
    """定义本期唯一允许自动创建的 Maven 与 Docker 仓库。"""
    storage = {"blobStoreName": "default", "strictContentTypeValidation": True}
    return {
        "maven-releases": (
            "maven2",
            "hosted",
            {
                "name": "maven-releases",
                "online": True,
                "storage": {**storage, "writePolicy": "ALLOW"},
                "cleanup": {"policyNames": []},
                "maven": {"versionPolicy": "RELEASE", "layoutPolicy": "STRICT", "contentDisposition": "INLINE"},
            },
        ),
        "maven-snapshots": (
            "maven2",
            "hosted",
            {
                "name": "maven-snapshots",
                "online": True,
                "storage": {**storage, "writePolicy": "ALLOW"},
                "cleanup": {"policyNames": []},
                "maven": {"versionPolicy": "SNAPSHOT", "layoutPolicy": "STRICT", "contentDisposition": "INLINE"},
            },
        ),
        "maven-central": (
            "maven2",
            "proxy",
            {
                "name": "maven-central",
                "online": True,
                "storage": storage,
                "cleanup": {"policyNames": []},
                "proxy": {"remoteUrl": "https://repo1.maven.org/maven2/", "contentMaxAge": 1440, "metadataMaxAge": 1440},
                "negativeCache": {"enabled": True, "timeToLive": 1440},
                "httpClient": {"blocked": False, "autoBlock": True},
                "maven": {"versionPolicy": "RELEASE", "layoutPolicy": "PERMISSIVE", "contentDisposition": "INLINE"},
            },
        ),
        "maven-public": (
            "maven2",
            "group",
            {
                "name": "maven-public",
                "online": True,
                "storage": storage,
                "group": {"memberNames": ["maven-releases", "maven-snapshots", "maven-central"]},
            },
        ),
        "docker-hosted": (
            "docker",
            "hosted",
            {
                "name": "docker-hosted",
                "online": True,
                "storage": {**storage, "writePolicy": "ALLOW", "latestPolicy": True},
                "cleanup": {"policyNames": []},
                "docker": {"v1Enabled": False, "forceBasicAuth": True, "httpPort": 8082},
            },
        ),
        "docker-proxy": (
            "docker",
            "proxy",
            {
                "name": "docker-proxy",
                "online": True,
                "storage": storage,
                "cleanup": {"policyNames": []},
                "docker": {"v1Enabled": False, "forceBasicAuth": True, "httpPort": 8083},
                "dockerProxy": {"indexType": "HUB"},
                "proxy": {"remoteUrl": "https://registry-1.docker.io", "contentMaxAge": 1440, "metadataMaxAge": 1440},
                "negativeCache": {"enabled": True, "timeToLive": 1440},
                "httpClient": {"blocked": False, "autoBlock": True},
            },
        ),
        "docker-group": (
            "docker",
            "group",
            {
                "name": "docker-group",
                "online": True,
                "storage": storage,
                "group": {"memberNames": ["docker-hosted", "docker-proxy"]},
                "docker": {"v1Enabled": False, "forceBasicAuth": True, "httpPort": 8084},
            },
        ),
    }


def ensure_repositories(password: str) -> None:
    """只创建缺失仓库；遇到同名不同格式或类型时停止，避免覆盖人工配置。"""
    existing = {item["name"]: item for item in request_api("GET", "/repositories", password)}
    for name, (repository_format, repository_type, payload) in repository_payloads().items():
        current = existing.get(name)
        if current is not None:
            if current.get("format") != repository_format or current.get("type") != repository_type:
                raise RuntimeError(f"仓库 {name} 已存在，但格式或类型不符合本期方案；为避免覆盖人工配置已停止")
            print(f"仓库 {name}：已存在")
            continue
        endpoint_format = "maven" if repository_format == "maven2" else repository_format
        request_api("POST", f"/repositories/{endpoint_format}/{repository_type}", password, payload)
        print(f"仓库 {name}：已创建")


def ensure_docker_token_realm(password: str) -> None:
    """启用 Docker Bearer Token Realm，使 Docker 客户端能够完成 Token 认证。"""
    active_realms = request_api("GET", "/security/realms/active", password)
    if "DockerToken" not in active_realms:
        available_realms = request_api("GET", "/security/realms/available", password)
        available_ids = {item if isinstance(item, str) else item.get("id") for item in available_realms}
        if "DockerToken" not in available_ids:
            raise RuntimeError("当前 Nexus 实例不提供 DockerToken Realm，无法安全启用 Docker 认证")
        request_api("PUT", "/security/realms/active", password, [*active_realms, "DockerToken"])
        active_realms = request_api("GET", "/security/realms/active", password)
    if "DockerToken" not in active_realms:
        raise RuntimeError("DockerToken Realm 写入后未生效")
    print("Docker Token Realm：已启用")


def ensure_roles_and_users(config: dict[str, str], password: str) -> None:
    """创建 Jenkins 发布与 K3s 拉取的最小权限角色和独立账号。"""
    available_privileges = {item["name"] for item in request_api("GET", "/security/privileges", password)}
    definitions = {
        "devops-jenkins-publisher": {
            "name": "DevOps Jenkins Publisher",
            "description": "发布 Maven 制品并推送 Docker 镜像，不具备 Nexus 管理权限。",
            "privileges": [
                "nx-repository-view-maven2-maven-public-browse",
                "nx-repository-view-maven2-maven-public-read",
                "nx-repository-view-maven2-maven-central-browse",
                "nx-repository-view-maven2-maven-central-read",
                "nx-repository-view-maven2-maven-releases-add",
                "nx-repository-view-maven2-maven-releases-browse",
                "nx-repository-view-maven2-maven-releases-edit",
                "nx-repository-view-maven2-maven-releases-read",
                "nx-repository-view-maven2-maven-snapshots-add",
                "nx-repository-view-maven2-maven-snapshots-browse",
                "nx-repository-view-maven2-maven-snapshots-edit",
                "nx-repository-view-maven2-maven-snapshots-read",
                "nx-repository-view-docker-docker-group-browse",
                "nx-repository-view-docker-docker-group-read",
                "nx-repository-view-docker-docker-hosted-add",
                "nx-repository-view-docker-docker-hosted-browse",
                "nx-repository-view-docker-docker-hosted-edit",
                "nx-repository-view-docker-docker-hosted-read",
                "nx-repository-view-docker-docker-proxy-browse",
                "nx-repository-view-docker-docker-proxy-read",
            ],
            "user": {
                "userId": "jenkins-publisher",
                "firstName": "Jenkins",
                "lastName": "Publisher",
                "emailAddress": "jenkins-publisher@devops.local",
                "password": required(config, "JENKINS_PUBLISHER_PASSWORD"),
            },
        },
        "devops-k3s-puller": {
            "name": "DevOps K3s Puller",
            "description": "仅允许从 Docker Group 拉取测试镜像，不具备写入或管理权限。",
            "privileges": [
                "nx-repository-view-docker-docker-group-browse",
                "nx-repository-view-docker-docker-group-read",
                "nx-repository-view-docker-docker-hosted-browse",
                "nx-repository-view-docker-docker-hosted-read",
                "nx-repository-view-docker-docker-proxy-browse",
                "nx-repository-view-docker-docker-proxy-read",
            ],
            "user": {
                "userId": "k3s-puller",
                "firstName": "K3s",
                "lastName": "Puller",
                "emailAddress": "k3s-puller@devops.local",
                "password": required(config, "K3S_PULLER_PASSWORD"),
            },
        },
    }

    current_roles = {item["id"]: item for item in request_api("GET", "/security/roles", password)}
    current_users = {item["userId"]: item for item in request_api("GET", "/security/users", password)}
    for role_id, definition in definitions.items():
        missing_privileges = set(definition["privileges"]) - available_privileges
        if missing_privileges:
            names = ", ".join(sorted(missing_privileges))
            raise RuntimeError(f"Nexus 未提供所需权限 {names}；为避免创建权限不完整的账号已停止")

        role_payload = {
            "id": role_id,
            "name": definition["name"],
            "description": definition["description"],
            "privileges": definition["privileges"],
            "roles": [],
        }
        if role_id not in current_roles:
            request_api("POST", "/security/roles", password, role_payload)
            print(f"角色 {role_id}：已创建")
        else:
            existing_privileges = set(current_roles[role_id].get("privileges", []))
            if existing_privileges != set(definition["privileges"]):
                raise RuntimeError(f"角色 {role_id} 已存在但权限不符合本期最小权限方案；为避免覆盖人工配置已停止")
            print(f"角色 {role_id}：已存在")

        user = definition["user"]
        if user["userId"] not in current_users:
            request_api(
                "POST",
                "/security/users",
                password,
                {**user, "status": "active", "roles": [role_id]},
            )
            print(f"账号 {user['userId']}：已创建")
        else:
            existing_roles = set(current_users[user["userId"]].get("roles", []))
            if role_id not in existing_roles:
                raise RuntimeError(f"账号 {user['userId']} 已存在但未绑定 {role_id}；为避免覆盖人工配置已停止")
            print(f"账号 {user['userId']}：已存在，未重置密码")


def main() -> int:
    config = load_env(NEXUS_ENV_PATH)
    admin_password = required(config, "NEXUS_ADMIN_PASSWORD")
    ensure_admin_password(admin_password)
    ensure_repositories(admin_password)
    ensure_docker_token_realm(admin_password)
    ensure_roles_and_users(config, admin_password)
    print("Nexus 初始化完成：仓库、认证 Realm、Jenkins 发布账号和 K3s 拉取账号均已就绪")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        # 不记录请求正文、Authorization 头或堆栈，避免凭据泄露到终端与日志。
        print(f"Nexus 初始化失败：{error}", file=sys.stderr)
        raise SystemExit(1)
