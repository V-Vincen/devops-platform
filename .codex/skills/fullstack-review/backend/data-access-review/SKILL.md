---
name: data-access-review
description: Review data-access implementations such as MyBatis, JPA/Hibernate, JDBC, query builders, mappers, repositories, and ORM code for parameter binding, mapping, query loading, transactions, batching, caching, data scope, security, consistency, and performance.
---

# Data Access Review

审查应用代码与数据库之间的数据访问实现。重点检查 MyBatis、JPA/Hibernate、JDBC、查询构造器、Mapper 和 Repository 的参数绑定、结果映射、查询加载、事务边界、批量语义、缓存、数据权限和异常处理。

## 审查流程

1. 找到数据访问入口、Mapper 或 Repository、SQL 或查询表达式、实体映射、事务入口和调用方。
2. 确认实际使用的访问框架、版本、数据库方言、分页方式、缓存策略和测试工具。
3. 沿着输入、查询、映射、写入、事务提交、异常转换和接口响应追踪数据。
4. 区分数据访问实现问题和数据库引擎问题，分别路由到 data-access-review 和 database-review。
5. 输出具体入口、参数来源、影响数据范围、修复方向和回归验证。

## 审查范围

### 1. 参数绑定和注入风险

- 用户输入、筛选条件、排序字段、表名和列名是否经过参数绑定或严格白名单校验。
- MyBatis 优先使用井号参数绑定；美元花括号替换语法只允许用于经过白名单校验的标识符。
- JPA/JPQL、原生 SQL、JDBC 和查询构造器是否使用参数绑定，避免字符串拼接。
- 空值、空集合、缺少条件和动态条件组合是否可能扩大查询、更新或删除范围。
- 参数类型、精度、时区、枚举和数组映射是否与数据库字段一致。

### 2. 结果映射和数据语义

- Mapper 返回类型、字段别名、resultMap、类型处理器、枚举、JSON、时间和精度映射是否准确。
- JPA/Hibernate 实体状态、脏检查、级联、孤儿删除、列默认值和实体关系是否符合业务规则。
- JDBC ResultSet、资源关闭、空结果、重复结果和影响行数是否得到正确处理。
- 更新 0 行、更新异常多行、唯一约束冲突和乐观锁失败是否有明确结果语义。
- 读写模型、软删除、状态字段、租户字段和审计字段是否在所有入口保持一致。

### 3. 查询加载和性能

- 嵌套查询、延迟加载、关联集合和循环调用是否造成 N+1 查询。
- 是否只查询需要的字段，避免大对象、无界列表和隐式加载。
- 分页查询的主查询、总数查询、排序稳定性、空页面和深分页是否正确。
- JPA/Hibernate 的 fetch、join fetch、EntityGraph、批量大小和集合映射是否造成重复行或内存增长。
- MyBatis 的嵌套查询、分页插件、一级或二级缓存和批量执行是否与事务边界一致。
- 查询超时、取消、限流、批量大小和远程依赖失败是否有可恢复路径。

### 4. 事务和会话边界

- 事务是否覆盖必须原子完成的数据访问，不把关键写入拆到独立事务。
- Spring 事务代理、传播行为、只读标记、异常回滚和跨线程执行是否真实生效。
- JPA flush、Session 生命周期、MyBatis SqlSession、连接释放和缓存刷新是否可控。
- 查询后再写入是否存在竞态；乐观锁、条件更新、唯一约束或幂等键是否真正生效。
- 重试是否可能重复写入、重复消息或覆盖更新，失败后是否可以回滚或补偿。

### 5. 批量和写入语义

- 批量插入、更新和删除是否控制批次，避免单次 SQL、参数或事务过大。
- 批量部分失败时是否能定位失败项，是否支持重试、回滚和幂等。
- 写入前后的状态、缓存、消息、审计和接口结果是否一致。
- 影响行数、版本号、唯一约束和业务状态是否被校验，而不是只判断方法调用成功。
- 数据访问异常、死锁、超时、连接失败和约束冲突是否被转换为可识别结果。

### 6. 数据范围和安全边界

- 读取、修改、删除、导出和下载入口是否检查用户、组织、租户和资源归属。
- 租户条件、逻辑删除、数据权限和审计条件是否覆盖所有 Mapper 或 Repository 入口。
- 动态排序、筛选、表名、列名、原生 SQL、文件路径和 URL 参数是否有注入风险。
- 错误信息、日志、SQL 参数和调试输出是否泄露密码、Token、个人信息或内部结构。
- 批量接口是否防止越权扩大数据范围、重复提交和资源耗尽。

## 框架专项

### MyBatis

- 检查 XML 或注解 SQL 的动态条件、where 拼接、foreach 空集合、resultMap 和类型处理器。
- 检查美元花括号替换语法的来源和白名单，不能把用户输入直接作为 SQL 片段。
- 检查分页、排序、租户条件、逻辑删除和数据权限是否在所有 Mapper 入口生效。
- 检查缓存、Session 生命周期、批量执行、影响行数和事务提交结果。

### JPA/Hibernate

- 检查实体状态、脏检查、延迟加载、级联、孤儿删除、fetch 策略和集合映射。
- 检查 equals/hashCode、代理对象、实体生命周期和序列化边界。
- 检查 N+1、join fetch 重复行、分页与集合关联、原生 SQL 和锁模式。
- 检查乐观锁、悲观锁、flush 时机、事务传播和异常回滚。

### JDBC 和查询构造器

- 检查 PreparedStatement 参数绑定、try-with-resources、连接释放和超时设置。
- 检查查询构造器是否允许未经校验的字段、排序表达式或 SQL 片段。
- 检查批量参数、空集合、影响行数、结果集关闭和异常转换。

## 边界

- 数据库引擎、DDL、索引、执行计划、锁和迁移读取 database-review。
- Java 集合、线程池和异常实现读取 java-review。
- Spring 配置、事务代理、缓存和消息整合读取 springboot-review。
- 注入、越权、敏感信息和租户隔离读取 security-review。
- 数据访问变化后的接口、页面和回归用例读取 test-generation。

## 清单联动

清单路径解析：CHECKLIST_ROOT 先解析为当前项目/.codex/checklists/；不存在时再解析为 ~/.codex/checklists/fullstack-quality-kit/。本节中的 code-review/、business-test/、db-change/ 和 external-api-doc/ 路径均相对于 CHECKLIST_ROOT；CHECKLIST_ROOT 仅是逻辑标识，不要求设置环境变量。

- 写入 Mapper、Repository、JPA、JDBC、查询构造器或测试代码时，读取 CHECKLIST_ROOT/CODE_REVIEW_CHECKLIST.md、code-review/code-quality-baseline.md 和 code-review/data-access-quality.md；测试代码另读 code-review/test-code-quality.md。
- 涉及输入、权限、租户、敏感数据或原生 SQL 时，读取 code-review/security-code-quality.md，并按业务范围读取 CHECKLIST_ROOT/BUSINESS_TEST_CHECKLIST.md。
- 涉及数据库写入、删除、结构变更或真实数据校验时，读取 CHECKLIST_ROOT/DB_CHANGE_CHECKLIST.md 及相关 db-change/ 子清单。
- 用户明确要求整理或生成外部接口文档时，读取 CHECKLIST_ROOT/EXTERNAL_API_DOC_CHECKLIST.md 及相关 external-api-doc/ 子清单。

## 输出要求

每个数据访问问题必须指出具体 Mapper、Repository、查询、参数来源或事务入口、触发条件、影响范围、修复方向和验证方式。不能只写“存在 N+1”或“增加事务”，必须说明实际查询、边界和验证证据。
