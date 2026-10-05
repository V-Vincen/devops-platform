# DevOps Platform

轻量级自建 DevOps 平台，面向少量开发人员和多套微服务测试环境。

## 当前技术基线

- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia
- 后端：Spring Boot 3.5.x、JDK 25、Maven 3.9.x、MyBatis-Plus、Druid
- 数据：PostgreSQL、Redis
- 微服务基础设施：Nacos
- 制品仓库：Nexus Repository，同时承载 Maven 私服和 Docker 私有镜像仓库
- 交付：Gitee、Jenkins、K3s/Kubernetes、Helm

## 目录说明

```text
devops-platform/
├── backend/                         # Spring Boot 后端
├── frontend/                        # Vue 前端
├── infra/                           # 本地依赖、Jenkins、Kubernetes 和 Helm 模板
├── docs/                            # 设计和开发文档
└── README.md
```

## 本地开发启动

### 1. 准备环境

- JDK 25
- Maven 3.9.x
- Node.js LTS
- Docker Desktop 或 Docker Engine
- PostgreSQL、Redis（可通过 `infra/local` 启动基础依赖），Nacos 按 `infra/k8s/nacos` 部署

### 2. 启动本地基础依赖

```bash
cd infra/local
cp .env.example .env
docker compose up -d postgres redis
```

Nacos 需要先准备对应版本的 PostgreSQL Schema，并在确认镜像版本后启动。详细说明见 `infra/k8s/nacos/README.md`。

### 3. 启动后端

```bash
cd backend
DEVOPS_BOOTSTRAP_ADMIN_PASSWORD='replace-with-local-password' mvn spring-boot:run
```

默认地址：`http://localhost:8080`

首次启动时，如果数据库中不存在管理员用户，后端会使用
`DEVOPS_BOOTSTRAP_ADMIN_USERNAME`（默认 `admin`）和
`DEVOPS_BOOTSTRAP_ADMIN_PASSWORD` 创建管理员。密码只在启动参数中读取并以 BCrypt 摘要保存，后续启动不重复覆盖。
如需同时初始化开发人员账号，可以额外提供
`DEVOPS_BOOTSTRAP_DEVELOPER_USERNAME` 和 `DEVOPS_BOOTSTRAP_DEVELOPER_PASSWORD`。

健康检查：`http://localhost:8080/actuator/health`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:5173`

## 当前已实现

- 后端 Spring Boot 基础工程
- PostgreSQL + Flyway 初始化脚本
- MyBatis-Plus 项目查询和创建接口
- Redis、Nacos 配置入口
- Vue 前端工作台和项目列表页
- 微服务管理和项目、环境、微服务关联
- 基础登录、管理员和开发人员角色令牌认证
- 本地 PostgreSQL/Redis Compose 模板
- Helm 和 Jenkins 配置模板

## 当前未实现

- Jenkins API 实际触发
- Nexus API 实际管理
- Kubernetes 实际部署和回滚
- Nacos 配置发布页面
- Redis 分布式锁和缓存业务

这些功能按 `docs/PROJECT_DESIGN.md` 的实施阶段继续开发。
