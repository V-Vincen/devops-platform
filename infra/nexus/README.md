# Nexus Repository 本地部署

Nexus 同时承载 Maven 私服和 Docker 私有镜像仓库。本期仅用于本机开发测试，所有服务仅绑定 `127.0.0.1`；部署到测试服务器时必须改为 HTTPS 反向代理方案。

## 1. 本期端口与存储

| 用途 | 宿主机端口 | Nexus 容器端口 | 仓库或入口 |
|---|---:|---:|---|
| 管理页面、REST API、Maven | `18081` | `8081` | `maven-releases`、`maven-snapshots`、`maven-central`、`maven-public` |
| Docker 推送 | `18082` | `8082` | `docker-hosted` |
| Docker 公共镜像代理 | `18083` | `8083` | `docker-proxy` |
| Docker 统一拉取 | `18084` | `8084` | `docker-group` |

元数据使用项目 Docker PostgreSQL 的独立 `nexus` 数据库与 `nexus` Schema；Blob Store 与 Nexus 本地运行数据保存在 Docker 具名卷 `devops-nexus-data`。管理员和自动化账号密码的源配置保存在受保护的 `infra/nexus/.env`，并按本机维护要求同步记录到已忽略的 `LOCAL_CREDENTIALS.txt`；不会使用 Nacos 的原生 PostgreSQL `5432`。

## 2. 首次启动

在 `infra/nexus` 目录执行：

```bash
cp .env.example .env
# 在 .env 中设置数据库、管理员、Jenkins 发布账号和 K3s 拉取账号的随机强密码。
python3 scripts/init_nexus_database.py
docker compose config
docker compose up -d
python3 scripts/bootstrap_nexus.py
docker compose ps
```

当前 Docker Desktop 分配约 8 GB 内存，使用了 Nexus 官方允许的最小 JVM 配置：堆内存与直接内存各 `2703m`。如果容器因为内存不足反复重启，先将 Docker Desktop 内存提高到至少 10 GB，再重新启动 Nexus。

健康检查地址：

```text
http://127.0.0.1:18081/service/rest/v1/status
http://127.0.0.1:18081/service/rest/v1/status/writable
```

首次执行 `bootstrap_nexus.py` 时，脚本会在内存中读取容器的一次性管理员密码，并立即替换为 `.env` 中的 `NEXUS_ADMIN_PASSWORD`。之后使用 `admin` 与该受保护配置中的密码登录 `http://127.0.0.1:18081`，在引导页面完成 Nexus Community EULA 确认。

不要将管理员密码、数据库密码或后续 Jenkins、K3s 账号密码写入文档、代码或版本控制；本机凭据清单仅保存在已忽略的 `LOCAL_CREDENTIALS.txt`。

## 3. 初始化仓库

Maven 仓库：

```text
maven-releases   Hosted
maven-snapshots  Hosted
maven-central    Proxy
maven-public     Group（成员顺序：maven-releases、maven-snapshots、maven-central）
```

Docker 仓库：

```text
docker-hosted    Hosted，HTTP 连接器 8082
docker-proxy     Proxy，HTTP 连接器 8083
docker-group     Group，HTTP 连接器 8084，成员顺序：docker-hosted、docker-proxy
```

初始化时启用 Docker Bearer Token Realm。端口连接器是本期唯一的 Docker 路由方式，不能与路径路由或子域名路由混用。

## 4. 账号用途与使用入口

| 账号 | 用途 | 可使用的入口 | 禁止事项 |
|---|---|---|---|
| `admin` | 管理员完成 EULA、仓库、角色和系统维护 | `http://127.0.0.1:18081` | 不得用于 Jenkins 或 K3s 自动化 |
| `jenkins-publisher` | CI/CD 发布 Maven 制品、推送 Docker 镜像 | Maven `maven-releases` / `maven-snapshots`；`127.0.0.1:18082` | 不得进行 Nexus 系统管理 |
| `k3s-puller` | K3s 的 `imagePullSecret` 拉取镜像 | `127.0.0.1:18084` | 不得推送镜像、发布 Maven 制品或管理 Nexus |

所有密码见本机已忽略的 `LOCAL_CREDENTIALS.txt`，不要复制到 `settings.xml`、Dockerfile、Jenkinsfile 或 Kubernetes 清单中。

## 5. 当前验收结果

- EULA 已确认，Nexus REST 健康检查与仓库管理接口均返回 HTTP `200`。
- Maven Central 可经 `maven-public` 代理下载；Release、Snapshot 测试制品均可由 `jenkins-publisher` 发布，并经 `maven-public` 下载后通过内容校验。
- `jenkins-publisher` 可向 `docker-hosted` 推送镜像；`k3s-puller` 可通过 `docker-group` 拉取私有镜像，并可通过 `docker-proxy` 和 `docker-group` 拉取 Docker Hub 代理镜像。
- `jenkins-publisher` 与 `k3s-puller` 访问 Nexus 管理接口均返回 HTTP `403`，已验证二者不具备 Nexus 管理权限。
- 重启 `devops-nexus` 后，容器恢复为 `healthy`，此前发布的 Maven 制品仍可下载且内容一致。
- 本机回环地址可直接使用 HTTP 连接器；测试服务器和生产环境必须改用 HTTPS 反向代理。

验证期间生成的 Maven 测试制品坐标为 `com.vincent.devops.verification:nexus-verification`，Docker 测试镜像仓库为 `devops-platform-verification`；二者均保留在 Nexus 中作为本期验收记录。后续配置清理策略后可按这些标识清理。

## 6. 制品生命周期、备份与恢复

Nexus 制品清理策略、一次性备份、隔离恢复验证和后续维护命令统一记录在 [OPERATIONS.md](OPERATIONS.md)。备份对象仍固定为 `devops-nexus-data` 数据卷、独立 `nexus` 数据库和受保护的 `infra/nexus/.env`。

执行一致性备份：

```bash
python3 scripts/backup_nexus.py
```

脚本会短暂停止并自动重新启动原 Nexus，归档完成后会等待健康检查恢复；不会删除原数据卷、原数据库或任何制品。恢复验证使用隔离数据库、隔离数据卷和端口 `28081` 至 `28084`，不会覆盖正在运行的实例。

删除原 Nexus 容器、原数据、隔离恢复资源或制品都属于破坏性操作，必须先完成并核对备份后单独确认。
