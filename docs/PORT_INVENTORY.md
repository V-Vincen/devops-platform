# DevOps 平台端口总表

> 文档版本：F1.5  
> 更新日期：2026-10-05  
> 适用目录：`/Users/vincent/Projects/AiCode/devops/devops-platform`  
> 维护规则：新增、修改或启停带网络端口的组件后，只更新下表对应行；未配置或不适用的值统一填写 `-`。

| 分类 | 名称 | 当前状态 | 宿主机端口 | 容器 / Pod 端口 | Kubernetes Service 端口 | 预留端口 | 健康检查或核验方式 | 数据来源、质量与核验状态 | 结论 |
|---|---|---|---|---|---|---|---|---|---|
| 平台 | 后端 API 与 Actuator 健康检查 | 本地已配置，当前未运行 | `8080` | `-` | `8080` | `-` | `GET /actuator/health`；就绪：`/actuator/health/readiness`；存活：`/actuator/health/liveness` | A：后端配置、Helm；本机监听已核验 | 本地与 K3s 不共享宿主机网络，可同用 `8080` |
| 平台 | 前端 Vite 开发服务 | 本地已配置，当前未运行 | `5173` | `-` | `-` | `-` | 浏览器访问首页；无独立健康检查 | A：`frontend/vite.config.ts`；本机监听已核验 | 与现有端口不冲突 |
| 数据库 | PostgreSQL（平台） | Docker 已运行且健康 | `15432` | `5432` | `5432` | `-` | `pg_isready -U devops_user -d devops_platform` | A：`infra/local/docker-compose.yml`、Docker 容器；已实际核验 | 宿主机使用非默认端口，与 Nacos 数据库独立 |
| 数据库 | PostgreSQL（Nacos 数据库） | 本机原生 PostgreSQL 正在监听 | `5432` | `-` | `-` | `-` | 已核验 TCP 监听；Nacos 启动后再核验数据库连接 | A：本机监听、`/Users/vincent/Documents/nacos/.env`；已实际核验 | 供 Nacos 使用，地址为 `host.docker.internal:5432/nacos` |
| 缓存 | Redis（平台） | Docker 已运行且健康 | `16379` | `6379` | `6379` | `-` | `redis-cli -a <密码> ping` | A：`infra/local/docker-compose.yml`、Docker 容器；已实际核验 | 宿主机使用非默认端口 |
| 微服务 | 业务服务端口字段 | 数据模型默认示例值 | `-` | 默认示例 `8080` | 由业务 Service 决定 | `-` | 由具体服务定义并单独验证 | A：平台数据模型；已读取 | 不应将多个微服务都映射到同一个宿主机端口 |
| Nacos 3.2.2 | 控制台 | 本机已配置，容器当前停止（退出码 `143`） | `127.0.0.1:18080` | `8080` | 按未来集群 Service 配置 | `-` | 启动后：`GET /v3/console/health/readiness` | A：`/Users/vincent/Documents/nacos/compose.yaml`、`application.properties`；配置已核验，运行未核验 | 不与平台后端 `8080` 或 Nexus `18081` 冲突，仅本机可访问 |
| Nacos 3.2.2 | HTTP API | 本机已配置，当前停止 | `127.0.0.1:8848` | `8848` | `8848` | `-` | 启动后：`GET /nacos/v3/admin/core/state/readiness` | A：本机 Nacos 配置；配置已核验，运行未核验 | 后端后续启用 Nacos 时使用 `localhost:8848` |
| Nacos 3.2.2 | 客户端 gRPC | 本机已配置，当前停止 | `127.0.0.1:9848` | `9848` | `9848` | `-` | 启动后进行 TCP 连通性验证 | A：本机 Nacos Compose；配置已核验，运行未核验 | Nacos 客户端长连接端口 |
| Nacos 3.2.2 | 服务端 gRPC | 单机模式未发布 | `-` | `9849` | `9849` | `9849` | 集群部署后验证节点间连通性 | A：本机 Nacos 配置、官方端口说明；未实施 | 仅 Nacos 集群节点间使用，不向客户端开放 |
| Nacos 3.2.2 | JRaft | 单机模式未发布 | `-` | `7848` | `7848` | `7848` | 集群部署后验证节点间连通性 | A：本机 Nacos 配置、官方端口说明；未实施 | 仅 Nacos 集群节点间使用，不向客户端开放 |
| Nacos 3.2.2 | 内置 DNS | 未启用 | `-` | 默认 `5353`（TCP/UDP） | 按实际 Service 配置 | `-` | 启用后执行 DNS 查询验证 | A：项目配置、本机监听；已核验 `5353/UDP` 被其他应用占用 | 不启用；如未来启用，必须改端口并重新核验 |
| Nexus 3.96.3 | 管理界面、REST API、Maven Public / Releases / Snapshots | Docker 已运行且健康 | `127.0.0.1:18081` | `8081` | `-` | `-` | `GET /service/rest/v1/status`；Maven Central 代理下载；Release / Snapshot 发布与下载 | A：`infra/nexus/docker-compose.yml`、Docker 容器与实际制品验收；已核验 | Maven 私服按路径区分，不另占端口；仅本机可访问 |
| Nexus 3.96.3 | Docker Hosted | Docker 已运行且健康 | `127.0.0.1:18082` | `8082` | `-` | `-` | `docker login`、`jenkins-publisher` 推送测试镜像 | A：Compose、Docker 容器与实际推送验收；已核验 | Jenkins 后续推送内部镜像的入口 |
| Nexus 3.96.3 | Docker Proxy | Docker 已运行且健康 | `127.0.0.1:18083` | `8083` | `-` | `-` | `k3s-puller` 直接拉取 Docker Hub `hello-world:latest` | A：Compose、Docker 容器与实际代理拉取验收；已核验 | 缓存 Docker Hub 等上游镜像 |
| Nexus 3.96.3 | Docker Group | Docker 已运行且健康 | `127.0.0.1:18084` | `8084` | `-` | `-` | `docker login`、`k3s-puller` 拉取 hosted 与代理镜像 | A：Compose、Docker 容器与实际拉取验收；已核验 | K3s 后续统一拉取镜像的入口 |
| CI/CD | Jenkins Web 页面与 API | Docker 已运行，登录和重启持久化已核验 | `127.0.0.1:18090` | `8080` | `-` | `-` | `GET /login` | A：Compose、Docker 容器、回环 HTTP 检查；已核验 | 不占用宿主机 `8080`；入站 Agent `50000` 不对宿主机公开 |
| Kubernetes | K3s API Server（k3d 本机方案） | 集群已运行，节点 Ready | `127.0.0.1:16443` | `6443` | `6443` | `-` | `kubectl get nodes` | A：k3d、kubectl 与实际节点；已核验 | macOS 通过 k3d 运行 K3s；测试 Linux 服务器可改为原生 K3s |
| Kubernetes | K3s Ingress HTTP（k3d 本机方案） | Traefik 已运行，HTTP 路由已核验 | `127.0.0.1:18088` | `80` | `80` | `-` | 带 Host 的 HTTP 请求 | A：Traefik、Deployment、Service、Ingress 与实际 HTTP 请求；已核验 | 不直接占用宿主机 `80`，与 Nacos `18080` 不冲突 |
| Kubernetes | K3s Ingress HTTPS（k3d 本机方案） | Traefik 已运行，尚未配置证书 | `127.0.0.1:18443` | `443` | `443` | `-` | 配置证书后执行 HTTPS 请求 | A：k3d 端口映射；HTTPS 路由未核验 | 证书与域名未配置，不作为本期安装阻塞项 |
| Nexus | 隔离恢复验证 | 已通过，恢复容器已停止 | `-` | `8081`、`8082`、`8083`、`8084` | `-` | `127.0.0.1:28081`、`28082`、`28083`、`28084` | 健康检查、仓库 REST API、数据库表数量 | A：2026-10-05 实际恢复演练通过 | 专用于临时恢复验证；非长期服务端口 |

说明：`A` 表示已从本机配置、项目配置或运行状态实际核验；`C` 表示设计或预留信息，尚未实施。Nexus 的 `18081` 至 `18084` 已由 `devops-nexus` 绑定到 `127.0.0.1`，并完成健康检查、Maven 发布下载、Docker 推送拉取、重启持久化和隔离恢复验证；与已运行的 K3s、Jenkins、既有 PostgreSQL、Redis 和 Nacos 端口不存在冲突。

### 术语注解

**宿主机端口**¹：本机或服务器对外监听的端口，例如 Docker 映射左侧端口。  
**容器 / Pod 端口**²：Docker 容器或 Kubernetes Pod 内部进程监听的端口。  
**Kubernetes Service**³：为 Kubernetes 集群内工作负载提供稳定访问地址和端口的网络对象。  
**gRPC**⁴：Nacos 客户端维持长连接所使用的远程调用协议。  
**JRaft**⁵：Nacos 集群节点之间进行一致性同步的通信机制。
