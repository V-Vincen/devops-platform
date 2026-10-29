# Spring Boot 4 升级清单与实施记录

## 1. 关联文档

- [项目实施计划](IMPLEMENTATION_PLAN.md)
- [项目设计](PROJECT_DESIGN.md)
- [K3s 操作说明](../infra/k3s/README.md)
- [Jenkins 操作说明](../infra/jenkins/README.md)

## 2. 目标与范围

本次将平台后端从 Spring Boot 3.5.x 升级到仍处于开源维护期的 Spring Boot 4.0.x，并同步升级与其强绑定的 Spring Cloud、Spring Cloud Alibaba 以及 Spring Boot 专用 Starter。

不包含数据库 Schema 变更、业务接口改造、前端功能变更或运行环境部署。本地运行时依赖的 PostgreSQL、Redis、Nacos、Jenkins、Nexus 与 K3s 不在本次修改范围内。

## 3. 版本矩阵

| 组件 | 升级前 | 升级后 | 选择原因 |
| --- | --- | --- | --- |
| Spring Boot | 3.5.16 | 4.0.9 | 3.5.16 是该系列最后一个开源维护版本；4.0.x 为当前受维护主线。 |
| Spring Cloud | 2025.0.0 | 2025.1.2 | 2025.1.x 与 Spring Boot 4.0.x 对应。 |
| Spring Cloud Alibaba | 2025.0.0.0 | 2025.1.0.0 | 2025.1.x 对应 Spring Boot 4.0.x 与 Spring Cloud 2025.1.x。 |
| MyBatis-Plus | 3.5.17 | 3.5.18 | 该版本提供 Spring Boot 4 Starter。 |
| Druid | 1.2.24 | 1.2.28 | 该版本提供 Spring Boot 4 Starter。 |

## 4. 实施步骤

### 4.1 升级依赖版本

在 `backend/pom.xml` 统一更新 Spring Boot、Spring Cloud、Spring Cloud Alibaba、MyBatis-Plus 与 Druid 的版本。依赖版本由 Maven BOM 统一管理，禁止在业务模块零散覆盖 Spring Framework、Spring Security 或 Spring Data 的版本。

### 4.2 迁移 Spring Boot 专用 Starter

Spring Boot 4 将 Web MVC、测试和数据访问模块拆分得更清晰。平台应使用下列 Starter，避免继续依赖仅为兼容保留的旧模块：

| 原组件 | 新组件 | 用途 |
| --- | --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` | 提供平台 REST API 的 Spring MVC 与嵌入式 Tomcat。 |
| `spring-boot-starter-test` | `spring-boot-starter-webmvc-test` | 提供 MVC 接口测试所需的 JUnit、MockMvc 与 Spring 测试支持。 |
| `mybatis-plus-spring-boot3-starter` | `mybatis-plus-spring-boot4-starter` | 创建 Mapper、分页与 MyBatis-Plus 自动配置。 |
| `druid-spring-boot-3-starter` | `druid-spring-boot-4-starter` | 管理 PostgreSQL 数据源连接池。 |
| `flyway-core` | `spring-boot-starter-flyway` | 让 Flyway 按 Spring Boot 4 自动配置执行数据库迁移。 |

`flyway-database-postgresql` 保留，用于让 Flyway 识别 PostgreSQL 数据库方言。

### 4.3 验证清单

1. 执行 `mvn -B clean verify`，验证编译、单元测试和可执行 JAR 打包。
2. 执行 `mvn dependency:tree`，确认运行时没有 Spring Boot 3.x、Spring Framework 6.x 或 Spring Security 6.x 的混入依赖。
3. 执行前端 `npm run build`，验证后端版本升级未破坏前端类型检查与生产构建。
4. 在具备 PostgreSQL、Redis 与 Nacos 的环境执行启动验证，检查 Flyway 迁移、数据库连接、Redis 健康检查和 Nacos 关闭/启用两种配置。
5. 在 Jenkins 与 K3s 环境可用后，执行一次测试微服务构建和部署，确认升级后的平台记录接口与流水线回调不受影响。

## 5. 本次实施记录

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
| Maven 编译与单元测试 | 通过 | 47 个测试通过，0 失败、0 错误、0 跳过。 |
| Maven 依赖解析 | 通过 | Spring Boot 4.0.9、Spring Cloud 2025.1.2、Spring Cloud Alibaba 2025.1.0.0 均已解析。 |
| 运行时启动验证 | 待执行 | 当前未启动 PostgreSQL、Redis、Nacos；不以单元测试替代实际基础设施连接验证。 |
| Jenkins/K3s 全链路验证 | 待执行 | 依赖 Docker、Jenkins、Nexus 与 K3s 环境启动，以及私有 Git 仓库凭据接入。 |

## 6. 回滚方式

如果出现无法在短时间内修复的兼容性问题，使用 Git 将 `backend/pom.xml` 恢复到本次提交之前的版本，并重新执行 `mvn -B clean test`。本次未执行数据库迁移，因此不存在数据库回滚动作。
