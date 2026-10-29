# 第四阶段：K3s 测试集群操作说明

> 文档版本：S1.3
> 更新日期：2026-10-24
> 当前状态：单节点 K3s 测试集群已安装并完成 Nexus 镜像拉取与 HTTP Ingress 验收。

## 1. 本期目标与边界

【本期必须】

- 建设单节点测试集群，创建基础 Namespace、镜像拉取凭据和首个测试部署入口。
- K3s 从 Nexus `docker-group` 拉取业务镜像。
- 建立最小健康检查、资源限制和回滚操作。

【本期不做】

- 高可用控制平面、多节点工作节点、生产流量和公网域名。
- Nacos、日志平台、监控告警和灰度发布。

## 2. 关联文档

- [实施计划](../../docs/IMPLEMENTATION_PLAN.md)
- [Jenkins 操作说明](../jenkins/README.md)
- [端口总表](../../docs/PORT_INVENTORY.md)
- [项目记忆](../../PROJECT_MEMORY.md)

## 3. 部署顺序

```text
k3d 创建 devops-test 集群
  ↓
创建 Namespace 与 Nexus 镜像拉取凭据
  ↓
验证 Docker Group 镜像拉取
  ↓
验证 HTTP Ingress
  ↓
应用 Jenkins 部署 RBAC
  ↓
后续部署平台 Helm Chart
```

## 4. 本机实施方式

原生 K3s Server 运行在 Linux。本机是 macOS，因此本期本地测试建议使用基于 Docker Desktop 的 `k3d` 创建 K3s 集群；这仍运行 K3s，但不等同于在测试 Linux 服务器直接安装 K3s。

`devops-test` 是本项目单节点测试集群的固定名称。它不是业务服务，而是承载 `dev`、`test` 等 Kubernetes Namespace 的运行底座。选择单节点集群是为了先验证镜像拉取、Helm 部署、Ingress 与权限边界；本期不以它承担生产高可用或多节点容错。

实施时，先由 k3d 创建 `devops-test`，再创建 Namespace、Nexus 镜像拉取凭据和 Jenkins 部署权限，最后才部署平台或业务工作负载。这个顺序保证工作负载启动前已有网络入口、镜像来源和最小权限身份。

| 用途 | 本机建议端口 | 集群端口 | 当前状态 |
|---|---:|---:|---|
| K3s API | `16443` | `6443` | 已运行并核验 |
| Ingress HTTP | `18088` | `80` | 已运行并核验 |
| Ingress HTTPS | `18443` | `443` | 已映射，尚未配置域名和证书 |

实际安装已下载 k3d、Helm 与 K3s 运行镜像，创建 Docker 容器、Kubernetes 资源和 Namespace 级镜像凭据。端口最终以 [端口总表](../../docs/PORT_INVENTORY.md) 为准。

## 5. 安装前检查

```bash
docker info
command -v k3d
command -v kubectl
docker ps --format 'table {{.Names}}\t{{.Ports}}'
lsof -nP -iTCP:16443 -sTCP:LISTEN
lsof -nP -iTCP:18088 -sTCP:LISTEN
lsof -nP -iTCP:18443 -sTCP:LISTEN
```

安装前必须确认 Docker Desktop 运行正常，并预留足够 CPU、内存与磁盘。Nexus、PostgreSQL、Redis 继续作为 Docker Desktop 中的既有基础设施运行，不迁移、不删除。

## 6. 配置说明

### 6.1 工具安装与版本核验

2026-10-21 在本机检查到以下安装来源。部署时的原始安装命令没有留存，下面的安装命令是供新环境参考的操作，不作为当时执行过程的记录。

| 工具 | 作用 | 本机安装来源和当前版本 |
|---|---|---|
| `k3d` | 在 Docker 容器中创建和管理 K3s 集群 | Homebrew；`/opt/homebrew/bin/k3d`；`5.9.0` |
| `helm` | 根据模板和参数生成、安装和管理 Kubernetes 应用 | Homebrew；`/opt/homebrew/bin/helm`；`4.3.1`，部署记录中的 `4.3.0` 为当时版本 |
| `kubectl` | 向 Kubernetes 集群发送查询和管理命令 | Docker Desktop 自带；`/usr/local/bin/kubectl` 指向 Docker.app 内的程序；`1.36.1` |

新 Mac 已安装并启动 Docker Desktop、且已有 Homebrew 时，可执行以下命令安装 `k3d` 和 `helm`。Homebrew 默认安装执行时可用的版本，不保证与上表版本相同。

```bash
brew install k3d helm
```

`kubectl` 在本机复用 Docker Desktop 提供的程序，未单独通过 Homebrew 安装。新环境若找不到它，需要先确认 Docker Desktop 的安装和命令行工具配置；不要把安装 Kubernetes 客户端误认为已经创建了集群。

安装或更新后，用以下命令检查工具位置和版本；`kubectl version --client` 只检查客户端，不要求集群运行：

```bash
command -v k3d && k3d version
command -v helm && helm version --short
command -v kubectl && kubectl version --client
```

### 6.2 K3s 配置文件用途

这些文件是配置说明，打开文件不会执行操作。创建或应用时应先确认目标集群和 Namespace。

| 文件 | 具体用途 |
|---|---|
| [k3d-devops-test.yaml](k3d-devops-test.yaml) | 创建本机 `devops-test` 集群：定义一个服务节点、API 和 HTTP/HTTPS 端口映射，以及 Nexus 镜像仓库地址。它由 `k3d` 读取，不是 Kubernetes 工作负载清单。 |
| [nexus-pull-check-job.yaml](manifests/nexus-pull-check-job.yaml) | 在 `dev` 中创建一次性 Job，使用 `nexus-docker-group` 凭据经 Nexus 拉取并运行 `hello-world`，验证镜像拉取链路；任务结束后配置为 24 小时自动清理。 |
| [nexus-ingress-check.yaml](manifests/nexus-ingress-check.yaml) | 在 `dev` 中创建测试用 Nginx Deployment、Service 和 Ingress，验证从 Nexus 拉取镜像，以及通过 Traefik 将 `k3s.devops.local` 的 HTTP 请求转发到 Nginx。 |
| [jenkins-deployer-rbac.yaml](manifests/jenkins-deployer-rbac.yaml) | 创建 Jenkins 部署身份，并分别在 `dev`、`test` 中绑定角色，允许管理指定工作负载和服务；不直接授予读取 Secret 或管理集群级资源的 API 权限。能修改工作负载仍可能间接接触其可挂载的数据，需保护 Jenkins 凭据和流水线。 |

### 6.3 Helm 目录文件用途

[Helm Chart 目录](../helm/devops-platform) 用于描述平台后端的部署模板；它与上面的测试 Job 和 Nginx 验证配置用途不同。Helm 将模板与参数组合后生成 Kubernetes 资源，目录内文件本身不会自动部署应用。

| 文件 | 具体用途 |
|---|---|
| [Chart.yaml](../helm/devops-platform/Chart.yaml) | 描述 Chart 的名称、类型、版本和应用版本，供 Helm 识别这个应用包。 |
| [values.yaml](../helm/devops-platform/values.yaml) | 提供默认参数，包括副本数、镜像地址、拉取凭据名称、服务端口、数据库和 Redis 地址以及 Secret 名称；其中的镜像地址是示例，部署前需按实际环境配置。 |
| [templates/deployment.yaml](../helm/devops-platform/templates/deployment.yaml) | 根据参数生成后端 Deployment，设置容器镜像、环境变量、从 Secret 读取的密码以及存活和就绪检查。引用 Secret 不会自动创建 Secret。 |
| [templates/service.yaml](../helm/devops-platform/templates/service.yaml) | 生成后端 Service，将访问请求转发到匹配的后端容器；默认 `ClusterIP` 供集群内部访问，不会自行创建外部访问入口。 |
| [templates/_helpers.tpl](../helm/devops-platform/templates/_helpers.tpl) | 定义模板复用的名称生成规则，供其他模板调用；它不是独立部署的 Kubernetes 资源。 |

当前 `values.yaml` 中的镜像拉取凭据默认名为 `nexus-pull`，与上面的测试凭据 `nexus-docker-group` 不同；部署前须按目标 Namespace 中实际存在的凭据调整。数据库、Redis、Nacos 地址与所引用的 Secret 也需要事先准备并核对。以上为文件用途说明，不代表平台 Chart 已经部署或依赖已经就绪。

## 7. 验收记录模板

| 验收项 | 结果 | 证据 |
|---|---|---|
| 集群节点 Ready | 通过 | `kubectl get nodes` 显示单节点 `Ready` |
| Namespace 已创建 | 通过 | 五个规划 Namespace 均为 `Active` |
| Nexus 镜像拉取凭据可用 | 通过 | `dev`、`test` 中均为 `kubernetes.io/dockerconfigjson` |
| 测试镜像可拉取并运行 | 通过 | `nexus-pull-check` Job 为 `Complete` |
| HTTP Ingress 可用 | 通过 | 带 Host 请求 `127.0.0.1:18088` 返回 Nginx 页面 |
| Jenkins 部署权限边界 | 通过 | 可读取、修改 `dev`、`test` Deployment；不能列 Namespace、删除 Node 或读取 Secret |
| 平台健康检查通过 | 待后续镜像和基础 Service | 当前 Helm Chart 未部署 |
| 回滚命令已验证 | 待后续平台部署 | 当前无平台 Helm Release 可回滚 |

## 8. 当前实施记录

1. 已安装 `k3d 5.9.0`、`helm 4.3.0`，复用现有 `kubectl 1.36.1`。
2. 已按 [k3d-devops-test.yaml](k3d-devops-test.yaml) 创建 `devops-test` 集群；控制平面为 `K3s v1.35.5+k3s1`，节点状态为 `Ready`。
3. 已创建 `platform-infra`、`devops-platform`、`dev`、`test`、`staging` Namespace。
4. 已在 `dev`、`test` 创建 `nexus-docker-group` 类型的 `imagePullSecret`，账号只使用最小权限的 `k3s-puller`，不使用 Nexus 管理员。
5. 已完成 [nexus-pull-check-job.yaml](manifests/nexus-pull-check-job.yaml)：Job 从 Nexus `docker-group` 拉取 `hello-world` 并成功结束。
6. 已完成 [nexus-ingress-check.yaml](manifests/nexus-ingress-check.yaml)：Nginx Deployment 就绪，Traefik 经 `127.0.0.1:18088` 的 HTTP 路由严格验证通过。
7. 已执行 `helm lint infra/helm/devops-platform`，通过且仅提示 Chart 图标为建议项；项目 Helm Chart 尚未部署，因为当前没有可部署的平台镜像与集群内 PostgreSQL、Redis Service。
8. 已应用 [jenkins-deployer-rbac.yaml](manifests/jenkins-deployer-rbac.yaml)：Jenkins 仅可在 `dev`、`test` Namespace 管理 Helm 部署所需资源；不授予 Namespace、节点、集群角色或其他集群级资源权限。流水线必须将 `HELM_DRIVER` 设置为 `configmap`，避免 Jenkins 读取 Namespace 内其他 Secret；该清单完成权限校验后，才可创建 `k3s-kubeconfig`。

### 术语注解

**K3s**¹：面向资源较小环境的轻量 Kubernetes 发行版。  
**k3d**²：在 Docker 容器中运行 K3s 集群的本地开发工具。  
**Namespace**³：Kubernetes 中隔离资源名称与权限边界的逻辑空间。  
**imagePullSecret**⁴：供 Kubernetes 从私有镜像仓库拉取镜像的受保护凭据。
