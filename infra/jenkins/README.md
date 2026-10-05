# 第五阶段：Jenkins 接入操作说明

> 文档版本：S1.1  
> 更新日期：2026-10-05  
> 当前状态：Jenkins 控制器已部署并通过重启持久化验证；首次管理员与建议插件已完成，待接入最小权限凭据和首条流水线。

## 1. 本期目标与边界

【本期必须】

- 在本机 Docker Desktop 部署单实例 Jenkins。
- 接入 Maven 测试、Nexus 制品发布、Docker 镜像构建与 K3s 部署。
- 用最小权限凭据管理 Nexus、K3s 和 Gitee 接入信息。

【本期不做】

- Jenkins 高可用、分布式 Agent、企业单点登录、外部通知和生产部署。

## 2. 端口与持久化规划

| 用途 | 宿主机端口 | 容器端口 | 当前状态 |
|---|---:|---:|---|
| Jenkins Web 页面与 API | `18090` | `8080` | 已运行，`/login` 返回 HTTP `200` |
| 入站 Agent 通信 | `-` | `50000` | 不对宿主机公开 |
| Jenkins 配置与构建记录 | Docker 卷 | `/var/jenkins_home` | `devops-jenkins-home` 已创建并完成重启验证 |

Jenkins 不得使用宿主机 `8080`，以避免与平台后端和 Nacos 容器端口混淆。端口最终以 [端口总表](../../docs/PORT_INVENTORY.md) 为准。

## 3. 安装前检查

```bash
docker info
docker ps --format 'table {{.Names}}\t{{.Ports}}'
lsof -nP -iTCP:18090 -sTCP:LISTEN
docker volume inspect devops-jenkins-home
```

实际部署已拉取固定镜像 `jenkins/jenkins:2.580.1-lts-jdk21`、创建 `devops-jenkins-home` 数据卷，并生成一次性管理员密码。管理员密码、Nexus 发布凭据、K3s 凭据和 Gitee Token 只写入已忽略的本地受保护配置或 Jenkins Credentials，绝不写入 `Jenkinsfile.example`、`settings.xml.example` 和版本控制。首次管理员创建后，一次性解锁密码不再作为正式登录凭据使用。

## 3.1 本次部署记录

1. `devops-jenkins` 已通过 [docker-compose.yml](docker-compose.yml) 启动，且只监听 `127.0.0.1:18090`。
2. `/login` 返回 HTTP `200`；重启控制器后入口仍可访问，说明 Jenkins Home 已由独立数据卷持久化。
3. 初始解锁密码曾由脚本写入项目根目录已忽略的 `LOCAL_CREDENTIALS.txt`，文件与 `infra/jenkins/.env` 均为 600 权限；首次管理员创建完成后，应由管理员手工更新或清除该一次性记录，文档不记录密码值。
4. 用户于 2026-10-05 完成首次管理员创建并进入 Jenkins 控制台；安装界面已选择“安装建议插件”。该项证据来自用户提供的界面截图，质量等级为 B。
5. 已安装 Pipeline、Git、Credentials Binding、Docker Pipeline、Kubernetes CLI、Maven Integration，并完成 Jenkins 重启后的插件文件与控制器可用性复核。
6. 插件安装前已将 Jenkins Home 备份至 `~/.codex/backups/jenkins--devops-jenkins-home--20261005-174500-before-plugin-install.tar.gz`，权限为 600；安装后控制器恢复正常，未执行回滚。
7. Nexus / K3s Jenkins Credentials、专用构建 Agent、首条流水线和 Gitee Webhook 尚未配置。Gitee 外部 Token、仓库地址与回调配置仍按“需要额外决定时先跳过”处理。

## 4. 凭据名称与用途

| Jenkins Credentials 标识 | 类型 | 用途 | 状态 |
|---|---|---|---|
| `nexus-maven-publish` | 用户名密码 | 发布 Maven Release、Snapshot 制品 | 待写入 Jenkins |
| `nexus-docker-push` | 用户名密码 | 向 Nexus `docker-hosted` 推送镜像 | 待写入 Jenkins |
| `k3s-kubeconfig` | Secret file | 部署 `dev`、`test` Namespace | RBAC 已应用并完成权限校验，待生成受保护配置文件后写入 Jenkins |
| `gitee-webhook-token` | Secret text | 校验 Gitee Webhook | 待确认外部 Token |

`Jenkinsfile.example` 与 `settings.xml.example` 是模板，真实地址、镜像仓库路径和凭据 ID 必须在安装后按实际集群信息替换并验证。

### 4.1 构建 Agent 与镜像仓库前提

`Jenkinsfile.example` 已改为真实本机测试地址，并要求使用标签为 `devops-build` 的专用 Agent。该 Agent 必须具备 JDK 25、Maven 3.9、Docker CLI、Helm、kubectl，以及受控的 Docker 构建能力；控制器不得执行构建，也不得挂载 Docker Socket。

本机 Nexus Docker Hosted 当前只提供 HTTP `host.docker.internal:18082`。Docker Desktop 仅将回环地址列为不安全镜像仓库，尚未信任该主机名。应优先为 Nexus 配置 TLS；若仅限本机测试而决定使用 HTTP，需要管理员确认后再调整 Docker Desktop 的不安全镜像仓库设置并重新验证推送。该安全设置本轮未修改。

## 5. 实际部署顺序

1. 创建 Jenkins Compose 配置与受保护环境变量文件。
2. 完成首次管理员初始化并安装建议插件。
3. 已安装并复核 Pipeline、Git、Credentials Binding、Docker Pipeline、Kubernetes CLI、Maven Integration 等最小插件。
4. 建立上述凭据，不使用 Nexus `admin` 账号。
5. 应用 `infra/k3s/manifests/jenkins-deployer-rbac.yaml`，以 `kubectl auth can-i` 复核 Jenkins 仅能操作 `dev`、`test` 内的部署资源；流水线设置 `HELM_DRIVER=configmap`，不授予读取 Kubernetes Secret 的权限。
6. 将 `settings.xml.example` 替换为指向本机 Nexus `maven-public`、`maven-releases`、`maven-snapshots` 的受控配置。
7. 配置标签为 `devops-build` 的专用构建 Agent；控制器不执行构建，也不挂载 Docker Socket。
8. 创建测试流水线，顺序验证：检出、Maven 测试、制品发布、镜像推送、Helm 校验、K3s 部署与健康检查。
9. 设定保留 30 次构建记录；首次构建失败后验证不会发布 Maven 制品或 Docker 镜像。

## 6. 验收记录模板

| 验收项 | 结果 | 证据 |
|---|---|---|
| Jenkins 容器与登录页可用 | 通过 | `devops-jenkins` 与 `GET /login` HTTP `200` |
| Jenkins 主配置持久化 | 通过 | 重启后登录入口恢复，`devops-jenkins-home` 仍挂载 |
| 首次管理员与建议插件 | 通过（用户人工验收） | 用户提供的 Jenkins 控制台截图，质量等级 B |
| 流水线最小插件 | 通过 | Pipeline、Git、Credentials Binding、Docker Pipeline、Kubernetes CLI、Maven Integration 均已安装，重启后控制器可用 |
| 流水线模板真实地址与权限边界 | 通过静态复核 | 控制器不构建；仅 `dev`、`test`；`HELM_DRIVER=configmap` |
| Maven 测试失败会停止 | 待 Job 创建 | 构建日志 |
| Release / Snapshot 制品发布 | 待凭据创建 | Nexus 仓库与校验和 |
| Docker 镜像推送 | 待凭据创建 | `docker-hosted` 仓库 |
| K3s 部署与健康检查 | 待凭据创建 | Jenkins 构建记录、Kubernetes 状态 |
| 失败后回滚 | 待 Helm Release 创建 | Helm 历史与回滚记录 |

### 术语注解

**Jenkins**¹：用于自动执行构建、测试、制品发布和部署流程的持续集成工具。  
**Credentials**²：Jenkins 受保护保存的用户名密码、令牌、证书或配置文件。  
**Webhook**³：代码平台在提交事件发生时主动通知 Jenkins 的回调机制。  
**Helm**⁴：以可版本化配置安装、升级和回滚 Kubernetes 应用的工具。
