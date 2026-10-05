# 轻量级自建 DevOps 平台项目设计文档

## 1. 项目定位

本项目面向少量开发人员的内部测试场景，目标是用较少的基础设施完成多套微服务项目的构建、镜像管理、配置管理、测试环境部署、日志查看和版本回滚。

本期不追求生产级高可用，不建设多集群、灰度发布和完整可观测性平台。

## 2. 已确认方案

| 分类 | 方案 |
|---|---|
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia、ECharts |
| 后端 | Spring Boot 3.5.x、JDK 25、Maven 3.9.x |
| ORM | MyBatis-Plus、MyBatis |
| 数据库 | PostgreSQL、Flyway |
| 连接池 | Druid |
| 配置中心 | Nacos |
| 服务发现 | Nacos |
| 缓存和共享能力 | Redis |
| 制品仓库 | Nexus Repository |
| 持续集成 | Jenkins |
| 代码仓库 | Gitee |
| 容器编排 | K3s/Kubernetes、Helm |
| 认证授权 | Spring Security 或 Sa-Token，第一期先保留扩展点 |

Nacos 用于业务微服务的注册发现和配置管理；Kubernetes 负责 Pod 调度、Service 网络和健康检查。Redis 作为平台基础设施提供给后续微服务使用，但不是 DevOps 平台 MVP 的强制启动依赖。

## 3. 总体架构

```text
Gitee
  │ Webhook
  ▼
Jenkins
  ├── Maven 构建
  ├── 单元测试
  ├── Docker 镜像构建
  ├── 推送 Nexus
  └── Helm 部署 K3s
          │
          ▼
      K3s 集群
      ├── DevOps 平台
      ├── Nacos
      ├── Redis
      └── 多套测试微服务

Nexus Repository
  ├── maven-releases
  ├── maven-snapshots
  ├── maven-public
  ├── docker-hosted
  └── docker-group

PostgreSQL
  ├── devops_platform
  ├── nexus
  └── nacos
```

## 4. 前期基础设施准备

### 4.1 资源规划

推荐使用两类节点：

```text
DevOps/K3s 节点：8 vCPU / 16 GB RAM
Nexus 节点：2~4 vCPU / 8 GB RAM / 160 GB SSD
```

Nacos 和 Redis 运行在 K3s 的 `platform-infra` Namespace 中。若必须单机运行，建议至少 12 vCPU、24 GB RAM、300 GB SSD；8 vCPU、16 GB RAM 仅适合低并发验证。

### 4.2 PostgreSQL

建立独立数据库和账号：

```text
devops_platform / devops_user
nexus           / nexus_user
nacos           / nacos_user
```

不同系统不能共用业务 Schema。所有结构变更通过 Flyway 或官方 Schema 管理。

### 4.3 Nexus Repository

Maven 仓库：

```text
maven-releases
maven-snapshots
maven-central
maven-public
```

Docker 仓库：

```text
docker-hosted
docker-proxy
docker-group
```

Jenkins 推送到 `docker-hosted`，K3s 从 `docker-group` 拉取。所有访问使用 HTTPS，并为 Jenkins 和 K3s 分配不同的最小权限账号。

### 4.4 Nacos

测试环境采用单实例、外部 PostgreSQL、持久化卷。生产级部署再扩展为集群。

建议隔离：

```text
Namespace：dev、test
Group：项目编码
DataId：服务配置文件名
```

Nacos 应仅在内网访问。Nacos 2/3 版本涉及 HTTP/API 和 gRPC 端口，实际部署时必须按锁定版本放行 8848、9848、9849 以及对应控制台端口。

### 4.5 Redis

测试环境采用单实例和 PVC，不启用 Sentinel 或 Redis Cluster。Redis 主要用于：

- 业务微服务缓存
- Session
- 分布式锁
- 幂等控制
- 临时任务状态

Redis 不作为核心业务数据的唯一存储。Key 使用项目和环境前缀，例如：

```text
devops:*
project-a:dev:*
project-a:test:*
```

平台第一期可以不使用 Redis；后续启用缓存或分布式锁时，通过 `devops:*` 前缀和独立 ACL 用户隔离。

### 4.6 K3s、Jenkins 和 Gitee

K3s 提供测试集群，Jenkins 负责构建和部署，Gitee 通过 Webhook 触发流水线。每个微服务仓库根目录放置 `Jenkinsfile`，由代码仓库管理流水线版本。

## 5. 项目开发实施

### 5.1 后端模块

```text
devops-common       # 统一响应、异常、分页和工具
devops-system       # 用户、角色和权限
devops-project      # 项目管理
devops-environment  # 环境和 Namespace 管理
devops-service      # 微服务管理
devops-pipeline     # Jenkins 流水线
devops-registry     # Nexus 制品和镜像
devops-deployment   # Helm/Kubernetes 部署
devops-nacos        # Nacos 配置和服务状态
devops-redis        # Redis 可选缓存、锁和幂等能力
devops-audit        # 操作审计
```

第一期采用模块化单体，不拆分多个平台微服务。

### 5.2 前端页面

```text
登录页
工作台
项目管理
服务管理
环境管理
流水线和构建日志
部署记录和回滚
镜像管理
Nacos 配置管理
Nacos/Redis 基础设施状态
用户、角色和权限
审计日志
```

### 5.3 核心数据表

```text
sys_user
sys_role
sys_permission
project
environment
service
jenkins_job
pipeline_run
registry_repository
image_artifact
deployment
deployment_revision
nacos_namespace_binding
nacos_config_release
audit_log
```

部署版本保存镜像 Digest，不只保存 `latest`，保证可以准确回滚。

### 5.4 标准流水线

```text
Checkout
  ↓
Maven 编译和测试
  ↓
发布 Maven 制品
  ↓
构建 Docker 镜像
  ↓
推送 Nexus
  ↓
Helm 校验
  ↓
部署 K3s
  ↓
检查 Nacos 注册
  ↓
检查 Redis 连接
  ↓
健康检查
  ↓
回写平台状态
```

### 5.5 配置管理

敏感配置使用 Kubernetes Secret；普通配置可以由 Nacos 管理。配置发布必须保存版本、发布人、发布时间和回滚版本。

平台后端的数据库地址、Redis 地址和 Nacos 地址通过环境变量提供，避免平台启动时依赖 Nacos 获取自身连接地址。

## 6. 本期范围

### 必须完成

- 基础工程和本地启动方式
- PostgreSQL、Nexus、Nacos、Redis 环境模板
- 用户和项目基础管理
- 服务和环境管理
- Jenkins 构建触发
- 镜像推送和 K3s 部署
- Nacos 配置绑定
- 部署状态、日志和回滚
- 基础审计

### 暂不完成

- 多集群高可用
- Nacos 集群
- Redis Sentinel/Cluster
- Harbor
- 灰度发布
- Prometheus、Grafana、SkyWalking
- 完整生产级认证中心

## 7. 验收标准

1. Maven 能从 Nexus 下载依赖并发布内部包。
2. Jenkins 能从 Gitee 自动触发构建。
3. 镜像能推送到 Nexus，并被 K3s 拉取。
4. 微服务能注册到 Nacos 并加载配置。
5. 微服务能连接 Redis。
6. 平台能创建项目、服务和环境。
7. 平台能查看构建和部署状态。
8. 平台能查看 Pod 日志和部署事件。
9. 平台能按镜像 Digest 回滚。
10. 配置变更和部署操作可审计。

## 8. 后续迭代

后续根据实际并发和维护成本增加：

- Nacos 集群
- Redis 高可用
- 镜像安全扫描
- Prometheus/Grafana
- 灰度发布
- 多集群管理
- 生产环境权限和审批流
