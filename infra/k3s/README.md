# 第四阶段：K3s 测试集群操作说明

> 文档版本：S1.1  
> 更新日期：2026-10-05  
> 当前状态：单节点 K3s 测试集群已安装并完成 Nexus 镜像拉取与 HTTP Ingress 验收。

## 1. 本期目标与边界

【本期必须】

- 建设单节点测试集群，创建基础 Namespace、镜像拉取凭据和首个测试部署入口。
- K3s 从 Nexus `docker-group` 拉取业务镜像。
- 建立最小健康检查、资源限制和回滚操作。

【本期不做】

- 高可用控制平面、多节点工作节点、生产流量和公网域名。
- Nacos、日志平台、监控告警和灰度发布。

## 2. 本机实施方式

原生 K3s Server 运行在 Linux。本机是 macOS，因此本期本地测试建议使用基于 Docker Desktop 的 `k3d` 创建 K3s 集群；这仍运行 K3s，但不等同于在测试 Linux 服务器直接安装 K3s。

| 用途 | 本机建议端口 | 集群端口 | 当前状态 |
|---|---:|---:|---|
| K3s API | `16443` | `6443` | 已运行并核验 |
| Ingress HTTP | `18088` | `80` | 已运行并核验 |
| Ingress HTTPS | `18443` | `443` | 已映射，尚未配置域名和证书 |

实际安装已下载 k3d、Helm 与 K3s 运行镜像，创建 Docker 容器、Kubernetes 资源和 Namespace 级镜像凭据。端口最终以 [端口总表](../../docs/PORT_INVENTORY.md) 为准。

## 3. 安装前检查

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

## 4. 实际部署记录

1. 已安装 `k3d 5.9.0`、`helm 4.3.0`，复用现有 `kubectl 1.36.1`。
2. 已按 [k3d-devops-test.yaml](k3d-devops-test.yaml) 创建 `devops-test` 集群；控制平面为 `K3s v1.35.5+k3s1`，节点状态为 `Ready`。
3. 已创建 `platform-infra`、`devops-platform`、`dev`、`test`、`staging` Namespace。
4. 已在 `dev`、`test` 创建 `nexus-docker-group` 类型的 `imagePullSecret`，账号只使用最小权限的 `k3s-puller`，不使用 Nexus 管理员。
5. 已完成 [nexus-pull-check-job.yaml](manifests/nexus-pull-check-job.yaml)：Job 从 Nexus `docker-group` 拉取 `hello-world` 并成功结束。
6. 已完成 [nexus-ingress-check.yaml](manifests/nexus-ingress-check.yaml)：Nginx Deployment 就绪，Traefik 经 `127.0.0.1:18088` 的 HTTP 路由严格验证通过。
7. 已执行 `helm lint infra/helm/devops-platform`，通过且仅提示 Chart 图标为建议项；项目 Helm Chart 尚未部署，因为当前没有可部署的平台镜像与集群内 PostgreSQL、Redis Service。
8. 已应用 [jenkins-deployer-rbac.yaml](manifests/jenkins-deployer-rbac.yaml)：Jenkins 仅可在 `dev`、`test` Namespace 管理 Helm 部署所需资源；不授予 Namespace、节点、集群角色或其他集群级资源权限。流水线必须将 `HELM_DRIVER` 设置为 `configmap`，避免 Jenkins 读取 Namespace 内其他 Secret；该清单完成权限校验后，才可创建 `k3s-kubeconfig`。

## 5. 验收记录模板

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

### 术语注解

**K3s**¹：面向资源较小环境的轻量 Kubernetes 发行版。  
**k3d**²：在 Docker 容器中运行 K3s 集群的本地开发工具。  
**Namespace**³：Kubernetes 中隔离资源名称与权限边界的逻辑空间。  
**imagePullSecret**⁴：供 Kubernetes 从私有镜像仓库拉取镜像的受保护凭据。
