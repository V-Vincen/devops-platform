---
name: springboot-review
description: Review Spring Boot applications for dependency injection, web APIs, validation, transactions, configuration, caching, messaging, resilience, observability, lifecycle, and version compatibility. Use for Spring Boot behavior and configuration; route Java language, database, and security details to specialized skills.
---

# Spring Boot Review

审查 Spring Boot 应用的启动、请求处理、业务编排、配置和运行生命周期，重点验证框架行为是否与业务意图一致。

## 审查范围

### 1. Bean、依赖注入与生命周期

- Bean 作用域、初始化顺序、条件装配、循环依赖和代理行为是否符合预期。
- 构造器注入、接口边界和配置绑定是否清晰；避免通过字段注入隐藏必需依赖。
- `@Async`、`@Scheduled`、事务代理和其他基于代理的注解是否在有效代理边界内使用。
- 启动失败、关闭流程、线程池和连接池释放是否可观察且可恢复。

### 2. Web API 与参数校验

- HTTP 方法、状态码、请求/响应 DTO、序列化、分页、排序和错误码是否符合接口契约。
- 必填、长度、格式、范围、枚举和跨字段校验是否完整；校验失败不能进入业务或持久化层。
- 全局异常处理是否统一，不能向客户端暴露堆栈、SQL、类名和内部路径。
- 重复提交、幂等、超时、客户端断开和大请求体是否有明确处理。
- API 变更是否保持兼容，是否同步检查前端、调用方、文档和测试。

### 3. 事务与业务一致性

- 事务边界是否覆盖完整业务操作；跨服务、异步任务和事件发布不能假设本地事务自动覆盖。
- 事务注解是否因自调用、私有方法、异常类型或代理失效而没有生效。
- 回滚条件、只读事务、传播级别、隔离级别和长事务风险是否有实际依据。
- 写入成功、缓存更新、消息发送和外部接口调用失败时，数据状态是否可恢复。

### 4. 配置、环境与依赖

- 配置前缀、默认值、类型转换、环境覆盖和缺失配置启动行为是否明确。
- 不同环境的数据库、消息、外部服务和日志配置是否隔离；禁止在代码和配置库中提交密钥。
- Profile、条件配置和配置优先级是否可能导致生产环境加载错误实现。
- Spring Boot、Starter 和第三方依赖是否存在版本冲突、废弃特性或不兼容升级。

### 5. 缓存、消息与外部依赖

- 缓存 key、过期、失效、穿透、击穿、雪崩和脏数据处理是否符合业务一致性要求。
- 消息生产和消费是否处理重复、乱序、失败、重试、死信、幂等和消费进度。
- HTTP/RPC 客户端是否配置连接超时、读取超时、连接池、重试上限和熔断/降级策略。
- 重试是否只用于可安全重试的错误，避免放大流量或造成重复写入。

### 6. 可观测性与运行安全

- 健康检查、就绪检查、指标、请求标识、关键业务日志和审计信息是否足够定位问题。
- 日志、Actuator、错误页和调试配置是否在生产环境暴露内部信息。
- 优雅停机是否等待请求、消息和任务完成，并释放线程池、连接池和文件资源。

## 边界

- Java 语法、集合、并发和 JVM 细节读取 `java-review`。
- SQL、表结构、事务锁、迁移和数据库引擎细节读取 `database-review`；MyBatis、JPA/Hibernate、JDBC 和数据访问事务边界读取 `data-access-review`。
- 认证授权、注入、Token、CORS 和敏感信息读取 `security-review`。
- 需要验收时读取 `test-generation`，覆盖正常、异常、边界、重复调用和依赖失败。

## 清单联动

清单路径解析：CHECKLIST_ROOT 先解析为当前项目/.codex/checklists/；不存在时再解析为 ~/.codex/checklists/fullstack-quality-kit/。本节中的 code-review/、business-test/、db-change/ 和 external-api-doc/ 路径均相对于 CHECKLIST_ROOT；CHECKLIST_ROOT 仅是逻辑标识，不要求设置环境变量。

- 写入 Spring Boot 代码、配置或测试代码时，读取 `CHECKLIST_ROOT/CODE_REVIEW_CHECKLIST.md`、`code-review/code-quality-baseline.md`、`code-review/springboot-quality.md`；测试代码另读 `code-review/test-code-quality.md`。
- 接口、事务、缓存、消息或状态行为变化时，读取 `CHECKLIST_ROOT/BUSINESS_TEST_CHECKLIST.md`，再按范围读取 `business-test/api-behavior.md`、`business-test/state-transition.md`、`business-test/concurrency-idempotency.md` 或 `business-test/regression-suite.md`。
- 涉及数据库写入或结构变更时，读取 `CHECKLIST_ROOT/DB_CHANGE_CHECKLIST.md` 及相关 `db-change/` 子清单。
- 用户明确要求整理或生成外部接口文档时，读取 `CHECKLIST_ROOT/EXTERNAL_API_DOC_CHECKLIST.md`、`external-api-doc/api-discovery.md`、`external-api-doc/api-contract.md`、`external-api-doc/api-error-retry.md` 和 `external-api-doc/api-version-compatibility.md`。

## 输出要求

必须指出配置或注解实际作用的条件，并在可能时通过启动、接口、日志或测试验证。对仅凭框架习惯推测的问题标记为待确认，不把推荐配置当成必然缺陷。
