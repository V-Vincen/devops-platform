#!/usr/bin/env python3
"""为本项目的 Nexus 实例创建独立 PostgreSQL 数据库。

脚本只读取 infra/local/.env 和 infra/nexus/.env 中的连接信息；不会输出密码。
重复执行只校验既有对象，不会重置密码、删除数据库或覆盖其他项目的数据。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

import psycopg
from psycopg import sql


PROJECT_ROOT = Path(__file__).resolve().parents[3]
LOCAL_ENV_PATH = PROJECT_ROOT / "infra" / "local" / ".env"
NEXUS_ENV_PATH = PROJECT_ROOT / "infra" / "nexus" / ".env"
IDENTIFIER_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,62}$")


def load_env(path: Path) -> dict[str, str]:
    """读取简单的 KEY=VALUE 文件，并拒绝缺失的运行配置。"""
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
    """确保必填项存在，避免缺失配置时连接到错误的默认数据库。"""
    value = values.get(key, "").strip()
    if not value:
        raise RuntimeError(f"配置项 {key} 不能为空")
    return value


def identifier(value: str, field_name: str) -> str:
    """仅允许 PostgreSQL 标识符，防止数据库名或角色名扩大 SQL 操作范围。"""
    if not IDENTIFIER_PATTERN.fullmatch(value):
        raise RuntimeError(f"{field_name} 必须是 1 至 63 位的字母、数字或下划线，且不能以数字开头")
    return value


def query_one(cursor: psycopg.Cursor, statement: str, params: tuple[object, ...]) -> tuple[object, ...]:
    """统一处理单行查询，避免空结果被误判为可继续初始化。"""
    cursor.execute(statement, params)
    row = cursor.fetchone()
    if row is None:
        raise RuntimeError("数据库返回了空结果，初始化已停止")
    return row


def main() -> int:
    local_env = load_env(LOCAL_ENV_PATH)
    nexus_env = load_env(NEXUS_ENV_PATH)

    admin_database = required(local_env, "POSTGRES_DB")
    admin_user = required(local_env, "POSTGRES_USER")
    admin_password = required(local_env, "POSTGRES_PASSWORD")
    host = required(nexus_env, "NEXUS_DB_BOOTSTRAP_HOST")
    port = required(nexus_env, "NEXUS_DB_BOOTSTRAP_PORT")
    database = identifier(required(nexus_env, "NEXUS_DB_NAME"), "NEXUS_DB_NAME")
    database_user = identifier(required(nexus_env, "NEXUS_DB_USER"), "NEXUS_DB_USER")
    database_password = required(nexus_env, "NEXUS_DB_PASSWORD")
    schema = identifier(required(nexus_env, "NEXUS_DB_SCHEMA"), "NEXUS_DB_SCHEMA")

    if not port.isdecimal() or not 1 <= int(port) <= 65535:
        raise RuntimeError("NEXUS_DB_BOOTSTRAP_PORT 必须是 1 至 65535 的端口号")

    # CREATE ROLE 和 CREATE DATABASE 不能放在同一个事务中，因此先以自动提交模式完成对象创建。
    admin_connection = psycopg.connect(
        host=host,
        port=port,
        dbname=admin_database,
        user=admin_user,
        password=admin_password,
        connect_timeout=5,
        autocommit=True,
    )
    try:
        with admin_connection.cursor() as cursor:
            is_superuser, = query_one(
                cursor,
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user",
                (),
            )
            if not is_superuser:
                raise RuntimeError("本地 PostgreSQL 管理账号不是超级用户，不能创建 Nexus 数据库")

            role_exists, = query_one(
                cursor,
                "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = %s)",
                (database_user,),
            )
            if not role_exists:
                cursor.execute(
                    sql.SQL("CREATE ROLE {} LOGIN PASSWORD {}")
                    .format(sql.Identifier(database_user), sql.Literal(database_password))
                )
                print("数据库角色：已创建")
            else:
                print("数据库角色：已存在，未重置密码")

            database_exists, owner = query_one(
                cursor,
                """
                SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = %s),
                       COALESCE((SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = %s), '')
                """,
                (database, database),
            )
            if not database_exists:
                cursor.execute(
                    sql.SQL("CREATE DATABASE {} OWNER {}")
                    .format(sql.Identifier(database), sql.Identifier(database_user))
                )
                print("数据库：已创建")
            elif owner != database_user:
                raise RuntimeError(f"数据库 {database} 已存在，但所有者不是 {database_user}；为避免影响既有数据已停止")
            else:
                print("数据库：已存在且所有者正确")
    finally:
        admin_connection.close()

    # 按 Nexus 官方要求，使用 Nexus 自身数据库账号创建 Schema 和 pg_trgm 扩展，使其拥有对应对象。
    nexus_connection = psycopg.connect(
        host=host,
        port=port,
        dbname=database,
        user=database_user,
        password=database_password,
        connect_timeout=5,
    )
    try:
        with nexus_connection.cursor() as cursor:
            cursor.execute("SET LOCAL lock_timeout = '5s'")
            cursor.execute(
                sql.SQL("CREATE SCHEMA IF NOT EXISTS {} AUTHORIZATION {}")
                .format(sql.Identifier(schema), sql.Identifier(database_user))
            )
            schema_owner, = query_one(
                cursor,
                "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = %s",
                (schema,),
            )
            if schema_owner != database_user:
                raise RuntimeError(f"Schema {schema} 已存在，但所有者不是 {database_user}；为避免影响既有数据已停止")
            cursor.execute(
                sql.SQL("CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA {}")
                .format(sql.Identifier(schema))
            )
            extension_schema, = query_one(
                cursor,
                """
                SELECT namespace.nspname
                FROM pg_extension extension
                JOIN pg_namespace namespace ON namespace.oid = extension.extnamespace
                WHERE extension.extname = 'pg_trgm'
                """,
                (),
            )
            if extension_schema != schema:
                raise RuntimeError(f"pg_trgm 已存在于 Schema {extension_schema}，预期为 {schema}；为避免改变既有扩展已停止")
        nexus_connection.commit()
    except Exception:
        nexus_connection.rollback()
        raise
    finally:
        nexus_connection.close()

    print(f"初始化完成：PostgreSQL {host}:{port}/{database}，Schema {schema}，扩展 pg_trgm 已就绪")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        # 不输出配置值、SQL 文本或堆栈，避免密码和内部连接信息泄露到终端日志。
        print(f"Nexus 数据库初始化失败：{error}", file=sys.stderr)
        raise SystemExit(1)
