# 第五阶段：Jenkins 接入操作说明

> 文档版本：S2.0
> 更新日期：2026-10-28
> 当前状态：Jenkins 控制器、管理员、插件、构建 Agent、三个凭据和首个 GitHub Pipeline Job 已准备；待完成实际流水线验证。

## 1. 本期目标与边界

【本期必须】

- 在本机 Docker Desktop 部署单实例 Jenkins。
- 接入 Maven 测试、Nexus 制品发布、Docker 镜像构建与 K3s 部署。
- 用最小权限凭据管理 Nexus、K3s 和 GitHub 接入信息。

【本期不做】

- Jenkins 高可用、分布式 Agent、企业单点登录、外部通知和生产部署。

## 2. 关联文档

- [实施计划](../../docs/IMPLEMENTATION_PLAN.md)
- [K3s 操作说明](../k3s/README.md)
- [Nexus 部署说明](../nexus/README.md)
- [端口总表](../../docs/PORT_INVENTORY.md)
- [项目记忆](../../PROJECT_MEMORY.md)

## 3. 标准流水线

```text
GitHub main 分支
  ↓
Jenkins 读取 Jenkinsfile.example
  ↓
devops-build Agent 检出、测试与构建
  ↓
Nexus 保存 Maven 制品和 Docker 镜像
  ↓
Helm 通过 K3s 凭据部署到 dev 或 test
  ↓
健康检查与构建记录
```

## 4. 端口与持久化规划

| 用途 | 宿主机端口 | 容器端口 | 当前状态 |
|---|---:|---:|---|
| Jenkins Web 页面与 API | `18090` | `8080` | 已运行，`/login` 返回 HTTP `200` |
| 入站 Agent 通信 | `-` | `50000` | 不对宿主机公开 |
| Jenkins 配置与构建记录 | Docker 卷 | `/var/jenkins_home` | `devops-jenkins-home` 已创建并完成重启验证 |

Jenkins 不得使用宿主机 `8080`，以避免与平台后端和 Nacos 容器端口混淆。端口最终以 [端口总表](../../docs/PORT_INVENTORY.md) 为准。

## 5. 安装前检查

```bash
docker info
docker ps --format 'table {{.Names}}\t{{.Ports}}'
lsof -nP -iTCP:18090 -sTCP:LISTEN
docker volume inspect devops-jenkins-home
```

实际部署已拉取固定镜像 `jenkins/jenkins:2.580.1-lts-jdk21`、创建 `devops-jenkins-home` 数据卷，并生成一次性管理员密码。管理员密码、Nexus 发布凭据、K3s 凭据和 GitHub Token 只写入已忽略的本地受保护配置或 Jenkins Credentials，绝不写入 `Jenkinsfile.example`、`settings.xml.example` 和版本控制。首次管理员创建后，一次性解锁密码不再作为正式登录凭据使用。

## 6. 配置前提与凭据

| Jenkins Credentials 标识 | 类型 | 用途 | 状态 |
|---|---|---|---|
| `nexus-maven-publish` | 用户名密码 | 发布 Maven Release、Snapshot 制品 | 已创建，待流水线验证 |
| `nexus-docker-push` | 用户名密码 | 向 Nexus `docker-hosted` 推送镜像 | 已创建，待流水线验证 |
| `k3s-kubeconfig` | Secret file | 部署 `dev`、`test` Namespace | 已于 2026-10-24 轮换为 7 天令牌，至 2026-10-31 01:39:09（北京时间）到期 |
| `github-webhook-secret` | Secret text | 校验 GitHub Webhook | 待创建；仅 GitHub 直接触发 Jenkins 时需要 |
| `devops-jenkins-callback-secret` | Secret text | 向平台回写构建状态和镜像制品 | 待创建；值必须与平台的 `DEVOPS_JENKINS_CALLBACK_SECRET` 相同 |

`Jenkinsfile.example` 与 `settings.xml.example` 是模板，真实地址、镜像仓库路径和凭据 ID 必须在安装后按实际集群信息替换并验证。

K3s 专用配置保存在 `~/.kube/jenkins-devops-test-kubeconfig`，权限为 `600`；已验证 Jenkins 控制器容器到集群的 TLS 证书，以及该身份在 `dev`、`test` 修改 Deployment 和不能直接读取 Secret、列出节点的权限。2026-10-24 已轮换为 7 天令牌，并替换 Jenkins 中同名凭据；新令牌于 2026-10-31 01:39:09（北京时间）到期。具备修改工作负载权限仍可能间接接触其可挂载的数据，应保护凭据及流水线。

### 6.1 构建 Agent 与镜像仓库前提

`Jenkinsfile.example` 已改为真实本机测试地址，并要求使用标签为 `devops-build` 的专用 Agent。该 Agent 必须具备 JDK 25、Maven 3.9、Docker CLI、Helm、kubectl、`curl` 和支持 `-hmac-env` 的 OpenSSL，以及受控的 Docker 构建能力；其中 `curl` 和 OpenSSL 用于不在命令行参数中暴露共享密钥的 HMAC 回调。控制器不得执行构建，也不得挂载 Docker Socket。

本机 Nexus Docker Hosted 当前只提供 HTTP `host.docker.internal:18082`。Docker Desktop 仅将回环地址列为不安全镜像仓库，尚未信任该主机名。应优先为 Nexus 配置 TLS；若仅限本机测试而决定使用 HTTP，需要管理员确认后再调整 Docker Desktop 的不安全镜像仓库设置并重新验证推送。该安全设置本轮未修改。

### 6.2 专用构建 Agent 配置

已新增 `infra/jenkins/agent` 构建配置，固定 Maven `3.9.12`、JDK `25`、Docker CLI `29.2.1`、kubectl `1.36.1` 和 Helm `4.3.0`。构建时仅支持 `amd64`、`arm64`，并随目标架构下载对应 kubectl 和 Helm；下载均校验官方 SHA-256。Agent 在启动时从本 Jenkins 控制器下载匹配版本的 `agent.jar`。

Agent 仅在显式使用 `--profile agent` 时创建，默认不会启动。Docker Socket 仅挂载到 Agent，控制器继续不挂载；因此 Agent 具有 Docker 守护进程的高权限，只能运行受信任项目的构建任务。

启动前提：管理员在 Jenkins 中注册名为 `devops-build` 的入站 Agent，并将其 JNLP 密钥仅写入 Git 忽略、权限为 `600` 的 `infra/jenkins/.env`。之后可执行：

```bash
docker compose --profile agent build agent
docker compose --profile agent up -d agent
```

2026-10-06 已完成 Compose 与入口脚本语法校验。构建过程曾两次因 Docker Hub 镜像元数据请求超时失败；网络恢复后的重试已成功生成 `devops-jenkins-agent:local`。当前本机实际构建为原生 `arm64` 镜像。2026-10-24，Jenkins 节点注册和 JNLP 连接均已完成；Agent 已启动并保持在线。Agent 仅挂载 Docker Socket，控制器未挂载。

## 7. 操作总览与职责

第五阶段的最小可用链路如下：

```text
开发者提交代码
    ↓
Jenkins Job 接收代码位置与构建参数
    ↓
devops-build Agent 检出代码、运行 Maven 测试、构建镜像
    ↓
Nexus 保存 Maven 制品和 Docker 镜像
    ↓
Helm 通过 K3s 凭据部署到 dev 或 test
    ↓
K3s 从 Nexus 拉取镜像并等待工作负载健康
```

| 组件 | 职责 | 不承担的职责 |
|---|---|---|
| Jenkins 控制器 | 保存 Job、凭据引用、构建记录和任务调度 | 不执行 Maven、Docker、kubectl 或 Helm 构建任务；不挂载 Docker Socket |
| `devops-build` Agent | 执行 Maven、Docker、kubectl 和 Helm 命令 | 不保存 Nexus、K3s 或 GitHub 的明文密码 |
| Nexus | 保存 Maven 制品和 Docker 镜像 | 不负责触发构建或部署 |
| K3s | 运行 `dev`、`test` 工作负载 | 不允许 Jenkins 管理节点、Namespace 或 Secret |
| Jenkins Credentials | 受保护保存发布账号和 K3s 配置文件 | 不写入 Jenkinsfile、Git 或构建日志 |

`devops-build` 是专门执行 Maven 编译、镜像构建和 K3s 部署的入站 Agent。它将 Docker Socket 限定在构建容器内；Jenkins 控制器不承担构建任务，避免控制器同时拥有任务调度、凭据保存和 Docker 守护进程访问权。

## 8. 配置与操作步骤

同一主体的页面字段、操作和验证放在同一小节。每节通过其成功标志后，再进入下一节。

### 第 1 步：启动 Jenkins 控制器与基础服务

1. 启动 PostgreSQL、Nexus、Jenkins 和 K3s 集群 `devops-test`。
2. 确认 Jenkins `http://127.0.0.1:18090/login` 和 Nexus 状态接口均返回 HTTP `200`，K3s 节点为 `Ready`。
3. Redis、Nacos 不是构建 Agent 注册前置条件；仅在后续平台工作负载或业务能力接入时启动。

成功标志：控制器、Nexus 和 K3s 均可用，Jenkins 控制器不挂载 Docker Socket。

### 第 2 步：配置 K3s 部署身份与 Jenkins Credentials

先应用 [jenkins-deployer-rbac.yaml](../k3s/manifests/jenkins-deployer-rbac.yaml)，再生成令牌、更新 kubeconfig、最后上传 Jenkins 凭据。

| K3s 配置项 | 当前值 |
|---|---|
| kubectl Context | `k3d-devops-test` |
| Namespace / ServiceAccount | `platform-infra` / `jenkins-deployer` |
| 令牌时长 | `168h`，即 7 天 |
| Audience | `https://kubernetes.default.svc.cluster.local`、`k3s` |
| kubeconfig 文件 | `~/.kube/jenkins-devops-test-kubeconfig`，权限 `600` |
| API Server / TLS Server Name | `https://host.docker.internal:16443` / `localhost` |
| Current Context | `jenkins-devops-test` |

令牌生成命令如下。令牌输出只能写入 kubeconfig，不能复制到聊天、日志或 Git：

```bash
kubectl --context k3d-devops-test -n platform-infra create token jenkins-deployer \
  --duration=168h \
  --audience=https://kubernetes.default.svc.cluster.local \
  --audience=k3s
```

保留 kubeconfig 中的 CA、API Server、TLS Server Name 和 Context，只替换 `users[0].user.token`。随后验证：

```bash
kubectl --kubeconfig ~/.kube/jenkins-devops-test-kubeconfig --server https://127.0.0.1:16443 auth can-i patch deployments -n dev
kubectl --kubeconfig ~/.kube/jenkins-devops-test-kubeconfig --server https://127.0.0.1:16443 auth can-i get secrets -n dev
```

预期结果依次为 `yes`、`no`。

接着进入 `Manage Jenkins → Credentials → System → Global credentials`，按下表创建或维护凭据：

| ID | Kind | 页面填写值 | 用途 |
|---|---|---|---|
| `nexus-maven-publish` | Username with password | Username 为 `jenkins-publisher`；密码为 Nexus 发布账号密码；Description 为“发布 Maven 制品到 Nexus” | Maven 发布。 |
| `nexus-docker-push` | Username with password | Username 为 `jenkins-publisher`；密码为 Nexus 发布账号密码；Description 为“推送 Docker 镜像到 Nexus” | Docker 登录和推送。 |
| `k3s-kubeconfig` | Secret file | Scope 为 `Global`；File 为 `~/.kube/jenkins-devops-test-kubeconfig`；ID 为 `k3s-kubeconfig`；Description 为“部署到 K3s 测试集群” | Helm 和 kubectl 部署。 |
| `devops-jenkins-callback-secret` | Secret text | Scope 为 `Global`；Secret 为随机生成且不低于 32 字符的共享密钥；ID 为 `devops-jenkins-callback-secret`；Description 为“签名回写 DevOps 平台构建状态” | 流水线用 HMAC 签名回调平台；不得用 `github-webhook-secret` 代替。 |

轮换 kubeconfig 时，打开 `k3s-kubeconfig → Update credential`，勾选 `Replace`，选择新文件，保持 ID 不变后点击 `Save`。当前令牌于 2026-10-31 01:39:09（北京时间）到期。

成功标志：四个凭据 ID 存在；K3s 权限校验为 `yes`、`no`。`devops-jenkins-callback-secret` 仅保存在 Jenkins Credentials 和平台受保护环境变量中，不写入 Git、Jenkinsfile 或构建日志。

### 第 3 步：注册并启动 `devops-build` 构建 Agent

先进入 `Manage Jenkins → Nodes → New Node`，选择 Permanent Agent，再按下表填写：

| 页面字段 | 当前填写值 | 说明 |
|---|---|---|
| Name | `devops-build` | 节点名称也是 Jenkins 自动提供的标签。 |
| Description | `专用构建 Agent，执行 Maven、镜像和 K3s 部署任务` | 仅说明用途。 |
| Number of executors | `1` | 避免共享 Docker Socket 的并发干扰。 |
| Remote root directory | `/var/jenkins-agent` | 容器实际存在的工作目录。 |
| Labels | 留空 | 使用节点名称自动标签 `devops-build`。 |
| Usage | `Use this node as much as possible` | 优先使用专用节点。 |
| Launch method | `Launch agent by connecting it to the controller` | 入站 JNLP 连接。 |
| Availability | `Keep this agent online as much as possible` | 保持在线。 |
| Node Properties | 全部不勾选 | 不额外注入页面级配置。 |

保存后，将节点连接页的 JNLP 密钥仅写入 Git 忽略、权限为 `600` 的 `infra/jenkins/.env` 中的 `JENKINS_AGENT_SECRET`。然后启动 Agent：

```bash
docker compose --env-file infra/jenkins/.env -f infra/jenkins/docker-compose.yml --profile agent up -d agent
```

成功标志：`devops-jenkins-agent` 为 `running`、日志包含 `Connected`，节点页显示 `devops-build` 在线；JDK、Maven、Docker CLI、kubectl 和 Helm 均可执行。JNLP 密钥不能写入文档、聊天记录或 Git。

### 第 4 步：创建 `devops-platform-server` Pipeline Job

在 Jenkins 首页选择 `New Item`，创建 Pipeline 类型 Job，名称填 `devops-platform-server`。进入 `Configure` 后按下表填写：

| 页面字段 | 当前填写值 | 说明 |
|---|---|---|
| Definition | `Pipeline script from SCM` | 从 GitHub 读取流水线脚本。 |
| SCM | `Git` | 使用 Git 插件。 |
| Repository URL | `https://github.com/V-Vincen/devops-platform.git` | 当前公开仓库 HTTPS 地址。 |
| Credentials | `- none -` | 仓库公开可读；私有化后再配置最小权限 GitHub 凭据。 |
| Branch Specifier | `*/main` | 只使用 `main` 分支。 |
| Repository browser | `(Auto)` | 保持默认。 |
| Script Path | `infra/jenkins/Jenkinsfile.example` | 仓库内流水线文件。 |
| Lightweight checkout | 已勾选 | 优先读取流水线脚本。 |
| GitHub hook trigger for GITScm polling | 不勾选 | 本机 Jenkins 没有公网 HTTPS 回调地址。 |

保存后先确认配置页面仍显示以上值。当前构建 #1 在保存流水线定义前启动，报 `No flow definition, cannot run`，未进入检出或发布阶段。

### 第 5 步：配置平台自动派发与 Jenkins 回调

平台在提交构建记录后才向 Jenkins 派发任务，避免外部构建已经启动但平台事务回滚。以下变量配置在平台后端的受保护环境中；变量为空或 `DEVOPS_JENKINS_ENABLED=false` 时，不会调用 Jenkins，构建记录保持 `PENDING`，可由人工或后续执行器处理。

| 平台环境变量 | 当前应填写的值 | 作用 |
|---|---|---|
| `DEVOPS_JENKINS_ENABLED` | `true` | 显式启用平台向 Jenkins 派发参数化构建。 |
| `JENKINS_BASE_URL` | Jenkins 对平台后端可访问的基础地址，例如 `http://host.docker.internal:18090` | 调用 Jenkins 队列 API。 |
| `JENKINS_USERNAME` | Jenkins 专用服务账号 | 不使用个人管理员账号。 |
| `JENKINS_API_TOKEN` | 上述服务账号生成的 API Token | 认证 Jenkins 队列 API。 |
| `JENKINS_JOB_NAME` | `devops-platform-server` | 目标 Pipeline Job 名称；由平台受保护配置决定，不接受外部请求传入。 |
| `DEVOPS_JENKINS_CALLBACK_URL` | `http(s)://<平台地址>/api/v1/integrations/jenkins/callback` | Jenkins 回写状态的完整地址；Jenkins Agent 必须可访问。 |
| `DEVOPS_JENKINS_CALLBACK_SECRET` | 与 Jenkins `devops-jenkins-callback-secret` 完全相同的随机密钥 | 校验 Jenkins 回调的 HMAC 签名。 |

保存 Job 后，使用 `Build with Parameters` 做首次验证。平台派发时会自动填写以下参数；人工验证可填写与某条实际构建记录一致的值。`PROJECT_ID`、`SERVICE_ID`、`PIPELINE_RUN_ID` 三者必须是正整数，且 `REPOSITORY_URL`、镜像仓库和路径必须与该服务发布配置一致。

| 参数 | 来源或填写规则 | 用途 |
|---|---|---|
| `PROJECT_ID`、`SERVICE_ID`、`PIPELINE_RUN_ID` | 平台构建记录 | 将 Jenkins 状态准确关联回项目、服务和构建记录。 |
| `REPOSITORY_URL`、`GIT_REF`、`COMMIT_SHA` | 服务仓库配置与 GitHub 事件 | 显式检出本次构建对应代码。 |
| `BUILD_TYPE`、`BUILD_WORKDIR`、`DOCKERFILE_PATH`、`DOCKER_CONTEXT_PATH` | 服务构建定义 | 控制 Maven、Dockerfile 和构建上下文。 |
| `IMAGE_REPOSITORY` | 服务构建定义 | 推送镜像的目标仓库；流水线产生的 tag 与 digest 会回写平台。 |
| `ENVIRONMENT_CODE`、`K8S_NAMESPACE` | 服务环境策略 | 记录目标环境；仅在以后启用部署时作为部署目标。 |
| `CALLBACK_URL` | 平台自动注入 | 接收签名后的 `RUNNING`、`SUCCESS` 或 `FAILED` 状态；人工构建必须填平台回调地址才会回写。 |
| `DEPLOY_ENABLED` | 保持 `false` | 当前必须保持关闭，避免没有服务级 Helm 定义时错误部署。 |

当前流水线在 `SUCCESS` 回调中同时提交 `IMAGE_REPOSITORY`、镜像 tag 和 sha256 digest；平台只在三项完整且 digest 格式正确时创建并标记镜像制品为已发布。回调地址、签名密钥和 Jenkins 服务账号均不得填入 Job 参数或源码。

成功标志：平台构建记录先获得 Jenkins 队列地址；日志出现 `Notify Platform: Running`、`Notify Platform: Success`；平台记录最终为 `SUCCESS`，且镜像制品记录为 `PUBLISHED`。若 Jenkins 派发失败，平台记录会标记为 `FAILED` 并保留安全的失败摘要。

### 第 6 步：手工验证流水线、回滚与 GitHub 回调

首次运行前先解决 `host.docker.internal:18082` 的 HTTP 信任问题或改用 TLS；否则 Docker 登录或推送必定失败。手工构建的阶段与通过标志如下：

| 阶段 | 通过标志 | 失败后处理 |
|---|---|---|
| Checkout | 日志显示 Git 提交 ID | 停止，不发布制品或镜像。 |
| Maven Test | `mvn clean verify` 通过 | 停止，不发布制品或镜像。 |
| Publish Maven Artifact | Nexus 可查询制品和校验和 | 不构建镜像、不部署。 |
| Build and Push Image | Nexus `docker-hosted` 有目标标签 | 不部署。 |
| Deploy to K3s | 当前不自动执行 | 发布配置尚未保存每个服务的 Helm Chart 路径和 Release 名称，`DEPLOY_ENABLED` 必须保持 `false`。 |

Job 保留最近 `30` 次构建记录。回滚前不删除镜像、Maven 制品或构建记录；部署失败时先检查 Helm 历史、Pod 状态和事件。

手工构建通过后再接入 GitHub Webhook：创建 `github-webhook-secret` 的 Secret text 凭据，为 Jenkins 准备可被 GitHub 访问的公网 HTTPS 地址，在 Job 中启用触发器，再在 GitHub Webhooks 页面填入同一地址和密钥。推送测试提交后应只触发一次构建。GitHub 触发 Jenkins 与 Jenkins 回调平台是两条独立链路，密钥不得混用。

自动 K3s 部署属于后续工作：需要先为每个服务增加并审核 Helm Chart 路径、Release 名称和目标值文件，再由平台派发这些受控参数。未完成该数据模型与审批策略前，不得将 `DEPLOY_ENABLED` 默认改为 `true`。

## 9. 实际部署顺序

1. 创建 Jenkins Compose 配置与受保护环境变量文件。
2. 完成首次管理员初始化并安装建议插件。
3. 已安装并复核 Pipeline、Git、Credentials Binding、Docker Pipeline、Kubernetes CLI、Maven Integration 等最小插件。
4. 建立上述凭据，不使用 Nexus `admin` 账号。
5. 应用 `infra/k3s/manifests/jenkins-deployer-rbac.yaml`，以 `kubectl auth can-i` 复核 Jenkins 仅能操作 `dev`、`test` 内的部署资源；流水线设置 `HELM_DRIVER=configmap`，不授予读取 Kubernetes Secret 的权限。
6. 将 `settings.xml.example` 替换为指向本机 Nexus `maven-public`、`maven-releases`、`maven-snapshots` 的受控配置。
7. 配置标签为 `devops-build` 的专用构建 Agent；控制器不执行构建，也不挂载 Docker Socket。
8. 创建测试流水线，顺序验证：检出、Maven 测试、制品发布、镜像推送、Helm 校验、K3s 部署与健康检查。
9. 设定保留 30 次构建记录；首次构建失败后验证不会发布 Maven 制品或 Docker 镜像。

## 10. 验收记录模板

| 验收项 | 结果 | 证据 |
|---|---|---|
| Jenkins 容器与登录页可用 | 通过 | `devops-jenkins` 与 `GET /login` HTTP `200` |
| Jenkins 主配置持久化 | 通过 | 重启后登录入口恢复，`devops-jenkins-home` 仍挂载 |
| 首次管理员与建议插件 | 通过（用户人工验收） | 用户提供的 Jenkins 控制台截图，质量等级 B |
| 流水线最小插件 | 通过 | Pipeline、Git、Credentials Binding、Docker Pipeline、Kubernetes CLI、Maven Integration 均已安装，重启后控制器可用 |
| 流水线模板真实地址与权限边界 | 通过静态复核 | 控制器不构建；仅 `dev`、`test`；`HELM_DRIVER=configmap` |
| 构建 Agent Compose、入口脚本与镜像工具 | 通过 | `docker compose config --quiet`、`sh -n` 均通过；`devops-jenkins-agent:local` 已构建并实际复核 JDK、Maven、Docker CLI、kubectl 与 Helm 版本 |
| 构建 Agent 连接与运行状态 | 通过 | Jenkins 节点页显示 `devops-build` 在线；容器为 `running`、重启次数为 `0` |
| Jenkins 部署 RBAC | 通过 | `platform-infra/jenkins-deployer` 可修改 `dev`、`test` Deployment，不能读取 Secret、Namespace 或节点 |
| Maven 测试失败会停止 | 待 Job 创建 | 构建日志 |
| Release / Snapshot 制品发布 | 待流水线验证 | Nexus 仓库与校验和 |
| Docker 镜像推送 | 待流水线验证 | `docker-hosted` 仓库 |
| K3s 部署与健康检查 | 待 Agent 和流水线验证 | Jenkins 构建记录、Kubernetes 状态 |
| 失败后回滚 | 待 Helm Release 创建 | Helm 历史与回滚记录 |

## 11. 当前实施记录

1. `devops-jenkins` 已通过 [docker-compose.yml](docker-compose.yml) 启动，且只监听 `127.0.0.1:18090`。
2. `/login` 返回 HTTP `200`；重启控制器后入口仍可访问，说明 Jenkins Home 已由独立数据卷持久化。
3. 初始解锁密码曾由脚本写入项目根目录已忽略的 `LOCAL_CREDENTIALS.txt`，文件与 `infra/jenkins/.env` 均为 600 权限；首次管理员创建完成后，应由管理员手工更新或清除该一次性记录，文档不记录密码值。
4. 用户于 2026-10-05 完成首次管理员创建并进入 Jenkins 控制台；安装界面已选择“安装建议插件”。该项证据来自用户提供的界面截图，质量等级为 B。
5. 已安装 Pipeline、Git、Credentials Binding、Docker Pipeline、Kubernetes CLI、Maven Integration，并完成 Jenkins 重启后的插件文件与控制器可用性复核。
6. 插件安装前已将 Jenkins Home 备份至 `~/.codex/backups/jenkins--devops-jenkins-home--20261005-174500-before-plugin-install.tar.gz`，权限为 600；安装后控制器恢复正常，未执行回滚。
7. 2026-10-24 已注册并启动 `devops-build` 入站 Agent。Jenkins 节点页显示其为在线的 Linux `aarch64` 节点，时钟同步、响应时间 74ms；容器运行且无重启。镜像内已复核 JDK 25、Maven 3.9.12、Docker CLI 29.2.1、kubectl 1.36.1 和 Helm 4.3.0；控制器未挂载 Docker Socket。节点远程根目录已更正为容器实际存在的 `/var/jenkins-agent`。已确认当前仓库可公开 HTTPS 读取，GitHub 回调仍需公网 HTTPS 入口。
8. 2026-10-23 用户创建三个 Nexus / K3s 凭据，Jenkins 凭据页面已确认其 ID 和显示类型；2026-10-24 已将 `k3s-kubeconfig` 轮换为 7 天令牌，至 2026-10-31 01:39:09（北京时间）到期。凭据尚未通过实际发布或部署验证。
9. 2026-10-24 已创建 `devops-platform-server` Pipeline Job，使用 GitHub `main` 分支的 `infra/jenkins/Jenkinsfile.example`。构建 #1 在保存流水线定义前启动，报 `No flow definition, cannot run`；没有执行检出、发布、镜像或部署阶段。
10. 2026-10-28 已将流水线改造成接收平台构建参数并以 HMAC 回写状态和镜像制品的模板；平台已具备提交后自动派发和回调登记代码，后端 47 项测试通过。`devops-jenkins-callback-secret`、平台 Jenkins 环境变量及真实 Job 验证仍待配置。当前 `DEPLOY_ENABLED` 默认关闭，因为服务级 Helm Chart 路径和 Release 名称尚未纳入发布配置。

### 术语注解

**Jenkins**¹：用于自动执行构建、测试、制品发布和部署流程的持续集成工具。  
**Credentials**²：Jenkins 受保护保存的用户名密码、令牌、证书或配置文件。  
**Webhook**³：代码平台在提交事件发生时主动通知 Jenkins 的回调机制。  
**Helm**⁴：以可版本化配置安装、升级和回滚 Kubernetes 应用的工具。
