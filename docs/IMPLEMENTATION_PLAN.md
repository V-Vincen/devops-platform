# DevOps 平台实施路线与执行清单

> 文档版本：F1.7  
> 更新日期：2026-10-05  
> 当前状态：第二、三、四阶段已完成本期验收；第五阶段 Jenkins 控制器、首次管理员与建议插件已完成，待接入最小权限凭据和流水线  
> 适用目录：`/Users/vincent/Projects/AiCode/devops/devops-platform`

## 1. 文档目的

本文件是项目后续开发、基础设施搭建和验收的执行依据。后续工作按阶段推进，每个阶段完成验收后再进入下一阶段。

项目面向少量开发人员的内部测试场景，目标是支持多套微服务项目的构建、镜像管理、配置管理和测试环境部署。

本项目当前不追求生产级高可用，暂不建设多集群、灰度发布和完整可观测性平台。

## 2. 当前决策

| 事项 | 当前决定 |
|---|---|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia |
| 后端 | Spring Boot、JDK 25、Maven 3.9.x、MyBatis-Plus、Druid |
| 数据库 | PostgreSQL，使用 Flyway 管理数据库迁移 |
| 本地依赖 | Docker Desktop 启动 PostgreSQL 和 Redis |
| 制品仓库 | Nexus Repository，承载 Maven 私服和 Docker 私有镜像仓库 |
| 代码仓库 | Gitee |
| 持续集成 | Jenkins |
| 容器编排 | K3s/Kubernetes 和 Helm |
| Nacos | 当前不作为平台启动依赖，部署微服务时再启用 |
| Redis | 当前作为基础设施保留，业务场景出现后逐步接入 |
| 平台架构 | 第一阶段采用模块化单体，不拆分平台微服务 |
| 高可用 | 当前不建设集群、Sentinel、Redis Cluster 和多副本高可用 |

## 3. 阶段总览

| 阶段 | 内容 | 状态 | 完成标志 |
|---|---|---|---|
| 第一阶段 | 本地开发闭环 | 已完成 | PostgreSQL、Redis、后端和前端可以运行 |
| 第二阶段 | 项目、环境、微服务管理 | 已完成 | “项目 → 环境 → 微服务”主链路、登录权限和页面交互均已验收确认 |
| 第三阶段 | Nexus 私服和镜像仓库 | 已完成 | Maven 和 Docker 制品可以上传、下载并在重启后保留 |
| 第四阶段 | K3s 测试集群 | 已完成 | 单节点集群、Namespace、Nexus 拉取、HTTP Ingress 已验收；平台工作负载待镜像和集群基础 Service 就绪 |
| 第五阶段 | Jenkins 自动构建部署 | 进行中 | Jenkins 控制器、首次管理员与建议插件已验收；Gitee 提交完整流水线待凭据、构建 Agent 和外部回调接入 |
| 第六阶段 | Nacos 配置和服务发现 | 待开始 | 业务微服务可以注册、发现和读取配置 |
| 第七阶段 | Redis 业务能力 | 待开始 | 缓存、锁、幂等等场景按需接入 |
| 第八阶段 | 权限、审计、监控和回滚 | 待开始 | 平台具备基本运维和安全能力 |

## 4. 第一阶段：本地开发闭环

### 4.1 已完成内容

- Docker Desktop 可以启动 PostgreSQL 和 Redis。
- Spring Boot 后端可以启动。
- Vue 前端可以启动。
- Flyway 可以初始化平台数据库。
- 项目列表和项目创建接口已经具备。
- 前端工作台和项目列表页面已经具备。
- 后端测试和前端构建已经通过。

### 4.2 本地启动方式

```bash
cd /Users/vincent/Projects/AiCode/devops/devops-platform/infra/local
cp .env.example .env
docker compose up -d postgres redis
```

启动后端：

```bash
cd /Users/vincent/Projects/AiCode/devops/devops-platform/backend
export DB_PASSWORD="$(sed -n 's/^POSTGRES_PASSWORD=//p' ../infra/local/.env | head -n 1)"
mvn spring-boot:run
```

启动前端：

```bash
cd /Users/vincent/Projects/AiCode/devops/devops-platform/frontend
npm ci
npm run dev
```

### 4.3 第一阶段验收标准

- PostgreSQL 容器状态为 `running`。
- Redis 容器状态为 `running`。
- `http://localhost:8080/actuator/health` 返回成功。
- `http://localhost:5173` 可以打开前端页面。
- 项目列表可以正常加载。
- 新增项目后，刷新页面仍然可以查询到项目。
- 停止并重新启动数据库后，项目数据仍然存在。

## 5. 第二阶段：项目、环境和微服务管理

第二阶段开发与前端人工验收均已完成，已形成项目、环境、微服务的完整管理闭环。

### 5.1 第二阶段目标

打通以下业务链路：

```text
创建项目
    ↓
创建 test 环境
    ↓
绑定微服务
    ↓
配置 Gitee 仓库
    ↓
保存服务构建和部署参数
```

### 5.2 第二阶段开发顺序

#### 第 1 步：完善项目管理

后端：

- 项目详情查询。
- 项目修改。
- 项目停用和启用。
- 项目逻辑删除。
- 项目负责人字段。
- 项目状态校验。
- 项目编码唯一性校验。

业务规则：

- 项目编码创建后不可修改。
- 启用中的项目不能直接删除，必须先停用。
- 删除使用逻辑删除，默认列表不再展示已删除项目。
- 重复提交相同启用或停用状态时保持幂等。

前端：

- 项目详情页。
- 项目编辑弹窗。
- 项目状态切换。
- 项目操作确认。
- 项目操作错误提示。

验收标准：

- 可以创建项目。
- 可以修改项目名称和描述。
- 停用项目后不能继续新增服务。
- 已删除项目不会出现在默认列表中。
- 重复项目编码会返回明确错误。

#### 第 2 步：新增环境管理

建议第一期支持：

```text
dev
test
staging
```

环境字段建议：

| 字段 | 说明 |
|---|---|
| project_id | 所属项目 |
| code | 环境编码 |
| name | 环境名称 |
| namespace | Kubernetes Namespace |
| cluster_name | Kubernetes 集群名称 |
| status | 环境状态 |
| description | 环境说明 |
| created_at | 创建时间 |
| updated_at | 修改时间 |
| deleted | 逻辑删除标记 |

后端：

- 新增环境表和 Flyway 迁移脚本。
- 新增环境创建、查询、修改和停用接口。
- 校验同一个项目下环境编码不能重复。
- 校验环境 Namespace 不能为空。

前端：

- 环境列表页。
- 新增环境弹窗。
- 环境详情页。
- 项目和环境关联展示。

验收标准：

- 一个项目可以拥有多个环境。
- 同一项目不能创建重复环境编码。
- 环境可以停用和恢复。
- 删除项目时不能绕过环境关联校验。

#### 第 3 步：新增微服务管理

微服务字段建议：

| 字段 | 说明 |
|---|---|
| project_id | 所属项目 |
| service_code | 服务编码 |
| service_name | 服务名称 |
| repository_url | Gitee 仓库地址 |
| branch_name | 构建分支 |
| build_type | Maven、Node.js 等构建类型 |
| image_repository | Docker 镜像地址 |
| port | 服务端口 |
| replicas | 副本数量 |
| cpu_limit | CPU 限制 |
| memory_limit | 内存限制 |
| status | 服务状态 |
| description | 服务说明 |

后端：

- 新增微服务表和 Flyway 迁移脚本。
- 新增微服务创建、查询、修改和停用接口。
- 校验同一项目下服务编码不能重复。
- 校验仓库地址和分支信息。
- 为后续 Jenkins 和 Helm 保留构建字段。

前端：

- 服务列表页。
- 新增服务页面。
- 服务详情页。
- 仓库配置表单。
- 镜像和资源配置表单。

验收标准：

- 一个项目可以拥有多个微服务。
- 微服务可以绑定 Gitee 仓库和分支。
- 微服务可以配置镜像仓库地址。
- 微服务可以配置副本、CPU 和内存。
- 停用的微服务不能进入后续部署流程。

#### 第 4 步：打通项目、环境和微服务关联

在项目、环境和微服务分别具备基础管理能力后，补齐三者之间的业务关联：

- 微服务必须归属于一个项目。
- 微服务可以配置允许部署的环境。
- 服务列表支持从项目进入，并按环境查看服务。
- 创建、修改和停用服务时校验项目和环境状态。
- 停用的项目或环境不能新增服务，也不能进入后续部署流程。
- 为后续 Jenkins 构建和 Kubernetes 部署保留项目、环境、服务三层上下文。

验收标准：

- 可以从项目进入环境，再进入环境下的微服务列表。
- 不属于当前项目的环境或微服务不能通过接口关联。
- 项目、环境或微服务处于停用状态时，接口返回明确的业务错误。
- 刷新页面后，项目、环境和微服务的关联关系仍然正确。

#### 第 5 步：补充基础登录和权限

内部自用场景先实现基础版本：

- 用户登录。
- 管理员角色。
- 开发人员角色。
- 项目访问权限。
- 环境操作权限。

权限模块应在 Jenkins、Nexus 和 Kubernetes 操作接口开放前完成。

验收标准：

- 未登录用户不能访问平台管理页面和写入接口。
- 管理员可以管理用户、项目、环境和微服务。
- 开发人员只能访问授权项目，并按权限执行环境操作。
- 登录失败、无权限和会话失效都有明确提示。

#### 第 6 步：完成第二阶段验收

第二阶段验收以“项目 → 环境 → 微服务”为主链路，至少完成以下检查：

- 项目、环境和微服务的新增、查询、修改、停用流程正常。
- 项目、环境和微服务的关联关系可以正常创建、查询和校验。
- 重复编码、跨项目关联、停用状态操作等失败场景有明确提示。
- 登录和基础权限校验生效。
- 数据库迁移脚本、接口注释、页面操作说明和回滚方式已经记录。
- 后端测试、前端构建和关键接口验证通过。

### 5.2.1 当前完成情况（2026-10-05）

| 内容 | 状态 | 实际结果 |
|---|---|---|
| 项目详情、修改、停用和删除 | 已完成 | 项目接口和页面功能已实现，停用及逻辑删除规则已验证 |
| 环境管理 | 已完成 | 环境增删改查、状态切换和项目归属校验已验证 |
| 微服务管理 | 已完成 | 微服务增删改查、状态切换和项目归属校验已验证 |
| 项目 → 环境 → 微服务关联 | 已完成 | 关联创建、查询、重复提交幂等和跨项目校验已验证 |
| 基础登录和权限 | 已完成 | 管理员、开发人员、项目授权、未登录和无权限场景已验证 |
| 后端和数据库验收 | 已完成 | 26 个单元测试通过；关键接口验证通过；Flyway 第 5 个迁移版本校验通过 |
| 前端构建 | 已完成 | `npm run build` 通过；存在单个产物体积偏大的性能提示 |
| 浏览器交互验收 | 已完成 | 用户已确认登录、项目、环境、微服务及其跳转与关联页面通过验收 |

本次后端接口验收共通过 59 项断言，覆盖健康检查、登录、项目、环境、微服务、关联、权限隔离和退出登录。数据库只读复核确认测试数据状态、逻辑删除、关联关系和用户项目授权均符合预期。

以下内容不属于第二阶段已完成范围：Nexus、Jenkins、K3s、Nacos 实际部署，Docker 镜像自动构建，以及业务 Redis 接入。

### 5.2.2 前端人工确认记录（已完成）

2026-10-05，用户已完成第二阶段人工验收并确认通过，第二阶段正式闭环。

确认范围：

- 登录、退出登录和基础权限提示。
- 项目、环境、微服务的管理页面和主链路跳转。
- 项目跳转至对应环境的筛选结果。
- 项目、环境、微服务的关联展示与操作。

验收后已停止本地前端和后端进程；PostgreSQL、Redis 和 Nexus 保持运行，便于后续基础设施工作。

### 5.3 第二阶段数据库表

建议新增：

```text
environment
service
service_environment
sys_user
sys_user_project
```

已有：

```text
project
```

后续再增加：

```text
jenkins_job
pipeline_run
registry_repository
image_artifact
```

所有数据库结构变更必须通过 Flyway 版本脚本完成，并为重要结构准备回滚说明。

### 5.4 第二阶段不做的内容

- Jenkins API 实际触发。
- Kubernetes 实际部署。
- Nacos 配置发布。
- Docker 镜像自动构建。
- 多集群管理。
- 灰度发布。
- Redis Cluster 和 Sentinel。

## 6. 第三阶段：搭建 Nexus

Nexus 是后续构建和部署的制品基础设施。第二阶段代码开发不依赖它，但在接入 Jenkins、制作镜像和部署 K3s 前必须完成。

### 6.0 本期搭建方式

本期采用低资源、单实例的本地方案：

- 使用 Docker Desktop 运行一个 `sonatype/nexus3` 容器。
- Nexus 使用独立持久化数据卷 `devops-nexus-data`（Compose 卷名为 `nexus-data`）。
- 复用现有 PostgreSQL 容器，但为 Nexus 单独创建数据库、数据库用户和 Schema，不使用平台的 `devops_user`。
- PostgreSQL 开启 `pg_trgm` 扩展，Nexus 通过外部 PostgreSQL 保存元数据；Docker 镜像和 Maven 制品保存到 Nexus Blob Store。
- 不在本阶段接入 Jenkins、K3s、Nacos，也不配置高可用。

本机已固定使用镜像版本 `sonatype/nexus3:3.96.3-alpine`，不使用 `latest`。后续升级前必须再次核对 Sonatype 官方兼容性说明，并在非生产环境完成回归验证。

选择外部 PostgreSQL 的原因是后续需要保留数据并接入 Jenkins、K3s；平台 PostgreSQL 容器已经存在，复用数据库服务可以少启动一个容器，但 Nexus 数据库必须和平台数据库隔离。

### 6.1 Maven 仓库

```text
maven-releases
maven-snapshots
maven-central
maven-public
```

### 6.2 Docker 仓库

```text
docker-hosted
docker-proxy
docker-group
```

### 6.3 权限和存储要求

- Jenkins 使用独立的发布账号。
- K3s 使用独立的镜像拉取账号。
- 禁止流水线使用 Nexus 管理员账号。
- Nexus 数据目录使用独立数据盘。
- Docker 镜像设置清理策略。
- Maven 快照设置保留策略。
- 配置 Nexus 数据备份。

建议资源：

```text
Nexus 容器：至少 2 CPU，JVM 堆不低于 2703 MB
Docker Desktop：建议分配至少 8 GB，总体建议 10~12 GB
本地磁盘：至少预留 40 GB，长期使用按 Maven 和 Docker 制品增长扩容
长期测试服务器：2~4 vCPU、8 GB 以上内存、160 GB 以上 SSD
```

### 6.4 实际实施结果与操作说明

已在本地 Docker Desktop 部署单实例 Nexus，只用于开发人员共享测试，不建设高可用。容器名称为 `devops-nexus`，所有端口仅绑定 `127.0.0.1`。

#### 6.4.1 部署前检查

- 确认 Docker Desktop 正常运行。
- 已确认并使用宿主机 `18081`、`18082`、`18083`、`18084`，分别映射 Nexus 容器 `8081`、`8082`、`8083`、`8084`。
- 确认 Docker Desktop 资源至少满足本节的 Nexus 内存要求；如果本机资源不足，先停止不必要的容器。
- 为 Nexus 数据准备 Docker 持久化卷，不能使用临时容器目录。
- 管理员密码、Jenkins 发布账号和 K3s 拉取账号分别保存，不写入 Git。
- 本地先使用 HTTP 验证链路；后续正式服务器再配置域名和 HTTPS。

#### 6.4.2 PostgreSQL 准备

在现有 `devops-postgres` 中新增独立的 Nexus 数据库，执行顺序如下：

1. 创建数据库用户 `nexus`，密码从本地受保护配置读取，不写入命令行历史和 Git。
2. 创建数据库 `nexus`，并将数据库所有者设置为 `nexus`。
3. 创建 Schema `nexus`，授权给 `nexus` 用户。
4. 以 `nexus` 用户在该数据库中创建 `pg_trgm` 扩展。
5. 记录 JDBC 地址，例如 `jdbc:postgresql://host.docker.internal:15432/nexus?currentSchema=nexus`。

数据库初始化属于结构变更，实际执行时使用 Python 数据库驱动脚本，执行前单独备份并验证回滚，不使用本地 `psql` 作为前置依赖。

以上对象已由 `infra/nexus/scripts/init_nexus_database.py` 创建并复核；Nexus 连接池已限制为 10，避免默认连接池耗尽 PostgreSQL 的连接上限。

#### 6.4.3 Nexus Compose 配置

已创建并投入使用以下文件：

```text
infra/nexus/docker-compose.yml
infra/nexus/.env.example
infra/nexus/README.md
infra/nexus/scripts/init_nexus_database.py
infra/nexus/scripts/bootstrap_nexus.py
```

Compose 配置必须包含：

- 固定的 `sonatype/nexus3` 镜像版本。
- `8081` 管理和 Maven 服务端口。
- `8082`、`8083`、`8084` Docker 仓库连接器端口。
- `nexus-data:/nexus-data` 持久化挂载。
- `NEXUS_DATASTORE_NEXUS_JDBCURL`、`NEXUS_DATASTORE_NEXUS_USERNAME`、`NEXUS_DATASTORE_NEXUS_PASSWORD` 数据库连接配置。
- `-Dnexus.datastore.enabled=true` 和受控的 JVM 内存参数。
- `restart: unless-stopped`。

首次部署命令：

```bash
cd /Users/vincent/Projects/AiCode/devops/devops-platform
docker compose --env-file infra/nexus/.env -f infra/nexus/docker-compose.yml up -d
python3 infra/nexus/scripts/bootstrap_nexus.py
docker compose --env-file infra/nexus/.env -f infra/nexus/docker-compose.yml ps
```

#### 6.4.4 建议的本地仓库端口

| 用途 | Nexus 仓库 | 建议端口 |
|---|---|---:|
| Nexus 管理页面和 Maven 服务 | Nexus | `127.0.0.1:18081` |
| Docker 私有镜像推送 | `docker-hosted` | `127.0.0.1:18082` |
| Docker 公共镜像代理 | `docker-proxy` | `127.0.0.1:18083` |
| Docker 统一拉取入口 | `docker-group` | `127.0.0.1:18084` |

#### 6.4.5 Nexus 初始化和仓库配置

1. 访问 `http://127.0.0.1:18081`，等待管理页面和健康检查可用。
2. `bootstrap_nexus.py` 会在首次执行时读取一次性 `admin.password`，并立即替换为 `infra/nexus/.env` 中的 `NEXUS_ADMIN_PASSWORD`；后续不会重置已存在账号密码。
3. 已由管理员在管理界面确认 Nexus Community EULA；未确认前，Maven 和 Docker 仓库内容访问会被 Nexus 拒绝。
4. Docker Bearer Token Realm 已启用；Maven Hosted、Proxy、Group 仓库和 Docker Hosted、Proxy、Group 仓库均已创建。
5. 已创建最小权限账号：`jenkins-publisher` 可发布 Maven 制品并推送 Docker 镜像，`k3s-puller` 仅可读取 Docker 镜像；两者均不具备 Nexus 管理权限。
6. 本地只使用端口连接器访问 Docker 仓库；同一 Nexus 实例不能同时混用端口连接器、路径路由和子域名路由。
7. Maven、Docker 和管理员密码仅保存在受保护的 `.env` 与本机 `LOCAL_CREDENTIALS.txt`，不得写入 Git 或 Jenkinsfile。

#### 6.4.6 Nexus 验证顺序

1. Nexus REST 健康检查、仓库 REST 接口，以及 Maven Central 经 `maven-public` 的代理下载均返回 HTTP `200`。
2. 使用 `jenkins-publisher` 成功向 `maven-releases` 发布唯一 Release 测试制品、向 `maven-snapshots` 发布唯一 Snapshot 测试制品；两者均经 `maven-public` 下载并通过 SHA-256 内容校验。
3. 使用 `jenkins-publisher` 成功登录 `127.0.0.1:18082` 并推送测试镜像到 `docker-hosted`；使用 `k3s-puller` 成功从 `127.0.0.1:18084` 的 `docker-group` 拉取该镜像。
4. 使用 `k3s-puller` 成功经 `127.0.0.1:18083` 的 `docker-proxy` 和 `127.0.0.1:18084` 的 `docker-group` 拉取 Docker Hub `hello-world:latest`，验证上游代理链路与两个连接器均可用。
5. 重启 `devops-nexus` 后，容器健康状态恢复为 `healthy`；此前发布的 Maven Release 制品仍可下载且 SHA-256 内容一致。
6. 本机 Docker Desktop 已可访问回环地址的 HTTP Docker 仓库，未额外修改 Docker Engine 配置；部署到测试服务器时仍必须使用 HTTPS 反向代理。
7. Nexus 验收已通过，下一步执行本计划 5.2.2 的前端人工验收；完成后再进入 K3s。

#### 6.4.7 Nexus 阶段验收标准

- [x] Nexus 重启后仓库、账号和 Maven 测试制品仍然存在。
- [x] Maven 依赖可以从 `maven-public` 下载，正式包和快照包可以分别发布。
- [x] Docker 镜像可以推送到 `docker-hosted`，并可以从 `docker-group` 拉取。
- [x] Jenkins 发布账号与 K3s 拉取账号均已按最小权限创建，并已分别完成发布和拉取验证；两者访问 Nexus 管理接口均返回 HTTP `403`。
- [x] Nexus 数据目录、Blob Store、外部 PostgreSQL 数据库和受保护配置均已纳入备份范围。
- [x] 已制定 Maven、Docker 代理缓存和私有制品的分级清理策略；策略当前为草案，尚未关联仓库或执行删除。

#### 6.4.8 清理策略与可恢复备份演练记录

1. 已新增 [Nexus 运维说明](../infra/nexus/OPERATIONS.md)，明确 Maven Snapshot、Maven Central 缓存、Docker Proxy 缓存、私有 Docker 镜像和 Maven Release 的不同处理规则；任何策略首次关联前必须通过 Nexus 管理界面预览。
2. 已于 2026-10-05 创建同一恢复点的 Nexus 数据卷归档、独立 PostgreSQL 自定义归档、受保护配置副本及不含密码的 SHA-256 清单，统一保存在 `~/.codex/backups/`。
3. 已在隔离资源中完成恢复验证：`nexus_restore_20261004200121` 数据库恢复后包含 `204` 张表，临时 Nexus 健康接口及 `10` 个仓库均可查询。
4. 原 `devops-nexus` 已自动恢复为 `healthy`；未删除原数据库、原数据卷或任何制品。
5. 验证副本容器 `devops-nexus-restore-20261004200121` 已停止，隔离数据库和数据卷保留待复查；删除这些隔离资源属于破坏性操作，未执行。

## 7. 第四阶段：搭建 K3s 测试集群

第一期采用单节点 K3s，运行测试环境和业务微服务。

### 7.0 当前实施记录

- 已按 [K3s 操作说明](../infra/k3s/README.md) 在 macOS Docker Desktop 上安装 `k3d 5.9.0`、`helm 4.3.0`，创建单节点 `devops-test` 集群（K3s `v1.35.5+k3s1`）。
- `16443`、`18088`、`18443` 分别已映射 API、HTTP、HTTPS；节点、五个 Namespace、Nexus `imagePullSecret`、Docker Group 镜像拉取和 HTTP Ingress 均已实际验证。
- 项目 Helm Chart 通过 `helm lint`；当前未部署平台工作负载，原因是尚无可部署的平台镜像以及集群内 PostgreSQL、Redis Service。

### 7.1 基础组件

- K3s。
- Ingress。
- StorageClass。
- Namespace。
- Kubernetes Secret。
- Nexus 镜像拉取凭证。
- 日志和资源限制。

### 7.2 Namespace 规划

```text
platform-infra
devops-platform
dev
test
staging
```

### 7.3 部署顺序

```text
K3s
  ↓
Ingress 和存储
  ↓
PostgreSQL
  ↓
Redis
  ↓
DevOps 平台
  ↓
业务微服务
```

## 8. 第五阶段：接入 Jenkins

Jenkins 负责统一构建、测试、制作镜像和部署。

### 8.0 当前实施记录

- 已使用固定镜像 `jenkins/jenkins:2.580.1-lts-jdk21` 启动 `devops-jenkins`，并创建独立 `devops-jenkins-home` 数据卷；`127.0.0.1:18090/login` 和重启持久化均已通过。
- Jenkins 初始解锁密码曾安全同步至 `LOCAL_CREDENTIALS.txt`；正式管理员已于 2026-10-05 创建后，该一次性记录应由管理员手工更新或清除。该文件及 Jenkins `.env` 均受 Git 忽略且权限为 600。
- 用户提供的界面截图确认：首次管理员创建完成、已进入 Jenkins 控制台，且初始化时已选择安装建议插件；该项证据质量等级为 B。
- 已安装并重启复核 Jenkins 流水线最小插件：Pipeline、Git、Credentials Binding、Docker Pipeline、Kubernetes CLI、Maven Integration；控制器恢复后登录页返回 HTTP `200`，证据质量等级为 A。
- 插件安装前已备份 Jenkins Home；安装后的控制器与登录入口均恢复正常，未发生回滚。
- 已应用并验证 Namespace 级 Jenkins 部署 RBAC 清单；该清单只覆盖 `dev`、`test` 的应用部署资源，未创建长期访问令牌。流水线将使用 `HELM_DRIVER=configmap` 保存发布记录，避免授予 Jenkins 读取 Kubernetes Secret 的权限。
- `Jenkinsfile.example`、`settings.xml.example` 已切换为本机 Nexus 地址、专用构建 Agent 和受限 `dev`、`test` 部署方式；尚未在 Jenkins 中创建 Job 执行。
- Nexus / K3s Credentials、构建 Agent、首条流水线与 Gitee Webhook 尚未接入。Docker Desktop 对 `host.docker.internal:18082` 的 HTTP 信任、Gitee 外部 Token、仓库地址与回调配置均属待确认项。

### 8.1 标准流水线

```text
Checkout
  ↓
Maven 编译和测试
  ↓
发布 Maven 制品到 Nexus
  ↓
构建 Docker 镜像
  ↓
推送镜像到 Nexus
  ↓
Helm 校验
  ↓
部署 K3s
  ↓
健康检查
```

### 8.2 Jenkins 接入内容

- Gitee Webhook。
- Jenkins Credentials。
- Maven settings.xml。
- Nexus Maven 地址。
- Nexus Docker 地址。
- K3s 部署凭证。
- Jenkins 构建日志。
- 构建记录保存策略。
- 构建失败通知。

### 8.3 流水线验收标准

- Gitee 提交可以触发 Jenkins。
- Maven 测试失败时流水线停止。
- 构建成功后 Maven 制品上传到 Nexus。
- Docker 镜像上传到 Nexus。
- K3s 可以从 Nexus 拉取镜像。
- 部署后健康检查可以通过。
- 部署记录可以查询。

## 9. 第六阶段：启用 Nacos

Nacos 当前不作为 DevOps 平台启动依赖。只有在业务微服务需要配置中心和服务发现时启用。

### 9.1 Nacos 部署方式

- 单实例。
- 使用外部 PostgreSQL。
- 使用持久化存储。
- 部署在 `platform-infra` Namespace。
- 开启认证。
- 仅允许内网访问。

### 9.2 配置迁移范围

迁移到 Nacos：

- 业务开关。
- 业务超时时间。
- 服务地址。
- 环境相关的普通配置。

保留在环境变量或 Kubernetes Secret：

- 数据库密码。
- Redis 密码。
- Nacos 密码。
- Token。
- 私钥和证书。
- 平台启动所需的基础连接信息。

### 9.3 配置组织方式

```text
Namespace：dev、test、staging
Group：项目编码
DataId：服务配置文件名
```

## 10. 第七阶段：Redis 业务能力

Redis 当前已作为本地和测试环境基础设施准备，但平台第一期不强制使用。

后续按实际业务接入：

- 缓存。
- Session。
- 分布式锁。
- 幂等控制。
- 临时任务状态。

Key 命名建议：

```text
devops:*
project-a:dev:*
project-a:test:*
```

平台账号和业务项目账号分开，避免不同项目互相读取数据。

## 11. 第八阶段：安全和运维

### 11.1 安全

- 登录认证。
- RBAC 权限控制。
- 项目级权限隔离。
- 环境级权限隔离。
- Jenkins、Nexus、Kubernetes 使用不同账号。
- 敏感配置使用 Secret。
- 对外访问统一使用 HTTPS。
- PostgreSQL、Redis、Nacos 只允许内网访问。

### 11.2 审计

记录以下信息：

- 操作人。
- 操作时间。
- 操作对象。
- 操作类型。
- 操作结果。
- 请求来源。
- 失败原因。

### 11.3 备份

- PostgreSQL 每日备份。
- Nexus 数据目录定期备份。
- Helm values 和 Kubernetes 清单纳入 Git。
- Jenkins 配置定期备份。
- 保留最近 7~30 天备份。

### 11.4 监控

初期关注：

- CPU 使用率。
- 内存使用率。
- 磁盘使用率。
- PostgreSQL 状态。
- Redis 状态。
- Nacos 状态。
- Jenkins 构建状态。
- Kubernetes Pod 状态。
- 服务健康检查。

## 12. 当前执行清单

当前按照以下统一顺序执行：

第二阶段与第三阶段均已完成验收；第四阶段开始前先完成 Nexus 清理策略和备份演练。

### 第二阶段：项目、环境和微服务管理

1. [x] 完善项目详情、修改、停用和删除。
2. [x] 新增环境管理。
   - [x] 新增 `environment` 表和后端接口。
   - [x] 开发环境管理页面。
3. [x] 新增微服务管理。
   - [x] 新增 `service` 表和后端接口。
   - [x] 开发微服务管理页面。
4. [x] 打通项目、环境、微服务关联链路。
5. [x] 补充基础登录和权限。
6. [x] 完成第二阶段最终验收。
   - [x] 后端接口、数据库状态和前端构建验收。
   - [x] 浏览器人工登录、页面跳转和退出登录验收。

### 后续阶段

7. [x] 完成第三阶段：搭建并验收 Nexus。
8. [x] 记录第二阶段前端人工验收结果。
9. [x] 制定 Nexus 清理策略，并完成一致性备份和隔离恢复演练。
10. [x] 完成第四阶段：单节点 K3s、Namespace、Nexus 镜像拉取和 HTTP Ingress 已验收。
11. [~] 第五阶段进行中：Jenkins 控制器、端口、持久化、首次管理员和建议插件已验收；待最小权限 Credentials、构建 Agent 与流水线接入。
12. [ ] 开始第六阶段：启用 Nacos。
13. [ ] 开始第七阶段：按实际场景接入 Redis。

## 13. 阶段完成定义

每个阶段必须同时满足以下条件才算完成：

1. 代码或基础设施配置已经提交到项目目录。
2. 关键接口或服务可以实际运行。
3. 正常流程已经验证。
4. 重复执行不会产生错误数据。
5. 失败场景有明确提示。
6. 数据库变更有版本脚本。
7. 关键操作有回滚或恢复方法。
8. 文档已经记录启动方式、配置项和验收结果。

## 14. 下一步执行计划

1. 创建 `nexus-maven-publish`、`nexus-docker-push`、`k3s-kubeconfig` Jenkins Credentials，基于 [Jenkinsfile.example](../infra/jenkins/Jenkinsfile.example) 验证 Maven、镜像、Helm 流水线。
2. 部署标签为 `devops-build` 的最小构建 Agent，构建并发布平台镜像；补齐集群内 PostgreSQL、Redis Service 后部署 Helm Chart，验证健康检查和 Helm 回滚。
3. 将 `host.docker.internal:18082` 调整为受信任的 TLS 镜像仓库，或经确认后配置 Docker Desktop 的 HTTP 例外，再验证镜像推送。
4. Gitee Webhook、外部 Token、仓库地址、域名和 HTTPS 等依赖外部账号或决策的内容，若未提供则跳过并记录为待确认。

本轮下一步操作入口：接入 Jenkins 最小权限凭据、构建 Agent 与基础流水线。第二、三、四阶段与 Nexus 恢复演练均已完成。

## 15. 需要后续确认的事项

- 是否采用一台服务器运行全部测试基础设施。
- Nexus 是否单独使用一台服务器。
- Gitee 组织和仓库命名规范。
- 测试环境域名和 HTTPS 证书方式。
- 是否需要接入企业微信、钉钉或邮件通知。
- 是否需要优先实现用户登录和权限。

### 术语注解

**Nexus**¹：用于保存 Maven 制品和 Docker 镜像的私服。  
**K3s**²：轻量级 Kubernetes，适合单节点测试环境。  
**Jenkins**³：用于自动构建、测试和部署的持续集成工具。  
**Nacos**⁴：用于微服务配置管理、注册和服务发现。  
**Redis**⁵：用于缓存、分布式锁、Session 和幂等控制。  
**RBAC**⁶：基于角色的权限控制机制。
