---
name: java-review
description: Review Java backend code for Alibaba Java Coding Guidelines, correctness, maintainability, concurrency, JVM/resource usage, performance, exceptions, logging, dependencies, and release quality. Use for Java source and Java-specific design; route Spring Boot, database, and security concerns to their specialized skills.
---

# Java Review

审查 Java 后端代码的正确性、可维护性和运行质量。以项目实际采用的 Alibaba Java Coding Guidelines 版本、构建规则和团队约定为准；规则版本未知时使用通用原则，并把版本差异标记为待确认。

## 审查范围

### 1. Alibaba Java 开发规范与代码正确性

- 命名、可见性、常量、类职责、方法长度、重复代码和复杂度是否符合项目规范。
- `null`、空集合、默认值、边界值、类型转换、溢出、精度和时区处理是否正确。
- `equals`/`hashCode`、可变对象作为 Map key、比较器、集合修改和迭代行为是否安全。
- 是否存在未使用参数、死代码、隐式副作用、魔法值、错误的布尔判断和不完整的分支。
- 不为了满足格式偏好提出没有行为或维护依据的意见。

### 2. 面向对象与架构边界

- 类和方法是否承担单一职责；领域逻辑是否被控制器、工具类或持久化代码吞并。
- 依赖方向是否清晰，是否存在循环依赖、跨层调用、全局状态和不必要的静态工具耦合。
- 接口、抽象类和继承是否真的表达稳定契约；避免为了扩展性提前增加抽象层。
- 公共方法、DTO、枚举和异常变更是否考虑调用方兼容性。

### 3. 集合、并发与异步

- 集合容量、查找复杂度、嵌套循环、大对象复制和批量处理是否可能造成性能问题。
- 共享可变状态、竞态条件、可见性、锁粒度、锁顺序、死锁和线程安全集合是否正确。
- 线程池是否有明确的核心线程、最大线程、队列、拒绝策略、命名、关闭和监控配置；禁止无界创建线程。
- 异步任务的异常、超时、取消、重试、上下文传递和结果顺序是否有处理。
- `parallelStream`、全局线程池和阻塞调用是否可能耗尽工作线程。

### 4. JVM、资源与性能

- 文件、流、Socket、HTTP 响应体和其他资源是否在成功与异常路径都能释放。
- 是否存在连接、线程、类加载、缓存或监听器泄漏；资源上限和生命周期是否明确。
- 大文本、大集合、序列化、反射、正则、装箱拆箱和频繁对象创建是否在实际热点路径产生风险。
- 只在有运行数据、基准或明确复杂度证据时报告性能结论；不要凭代码外观断言一定慢。
- OOM、频繁垃圾回收、线程堆积等 JVM 问题应给出需要收集的日志、堆转储或线程转储证据。

### 5. 异常、日志与可观测性

- 异常是否按可恢复性分类，是否保留原始异常链；禁止空 catch、静默失败和用通用异常掩盖根因。
- 对外错误信息不能暴露堆栈、SQL、密钥和内部路径；日志不能打印密码、Token、身份证号等敏感信息。
- 日志级别、上下文、请求标识和业务关键字段是否足以定位问题；避免循环内重复打印大对象。
- 重试、降级和兜底是否记录真实失败原因，并避免把失败伪装成成功。

### 6. 依赖、构建与发布

- 检查依赖冲突、废弃 API、版本兼容、重复依赖、许可证和已知漏洞；以项目锁定版本和构建结果为证据。
- 配置、密钥、环境变量和资源文件是否被错误打包或硬编码。
- 发布、回滚、优雅停机和向后兼容是否满足当前接口和数据迁移要求。

## 边界

- Spring Bean、Web 接口、事务、缓存、消息和配置问题读取 `springboot-review`。
- SQL、表结构、索引、事务锁、迁移和数据库引擎问题读取 `database-review`；MyBatis、JPA/Hibernate、JDBC 和数据访问实现问题读取 `data-access-review`。
- 认证授权、注入、Token 和敏感数据问题读取 `security-review`。
- 需要测试时读取 `test-generation`，并为正常、异常、边界、并发和回归路径生成用例。

## 清单联动

清单路径解析：CHECKLIST_ROOT 先解析为当前项目/.codex/checklists/；不存在时再解析为 ~/.codex/checklists/fullstack-quality-kit/。本节中的 code-review/、business-test/、db-change/ 和 external-api-doc/ 路径均相对于 CHECKLIST_ROOT；CHECKLIST_ROOT 仅是逻辑标识，不要求设置环境变量。

- 写入 Java 代码、配置或测试代码时，读取 `CHECKLIST_ROOT/CODE_REVIEW_CHECKLIST.md`、`code-review/code-quality-baseline.md`、`code-review/java-quality.md`；测试代码另读 `code-review/test-code-quality.md`。
- Java 改动影响接口、状态或业务结果时，读取 `CHECKLIST_ROOT/BUSINESS_TEST_CHECKLIST.md`，再按范围读取 `business-test/api-behavior.md`、`business-test/state-transition.md` 或 `business-test/regression-suite.md`。
- Java 改动涉及数据库写入或结构变更时，读取 `CHECKLIST_ROOT/DB_CHANGE_CHECKLIST.md`，再读取相关 `db-change/` 子清单。
- 普通 Java 接口分析不触发外部接口文档清单；只有用户明确要求整理或生成外部接口文档时才读取对应总清单及 `external-api-doc/` 子清单。

## 输出要求

每个问题必须给出文件和行号或类/方法、触发条件、实际影响、修复方向和验证方式。将规范问题、确定性缺陷、风险推测和待确认事项分开，不把“建议重构”当作确定性 Bug。
