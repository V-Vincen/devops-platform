---
name: fullstack-review
description: Review full-stack applications and generate standard test cases by routing Java, Spring Boot, database engines, data-access implementations, frontend, security, and testing concerns. Use when the user asks for a full-stack code review, quality audit, or test-case generation; do not use for unrelated formatting-only work.
---

# Fullstack Review

对全栈应用进行有证据的代码审查，并在需求、接口或页面行为涉及测试时生成常规测试用例。先识别项目技术栈和受影响范围，再按需读取子 skill；不要默认加载所有领域的规则。

## 路由规则

根据实际文件、入口、依赖和用户请求选择子 skill：

- Java 代码、领域逻辑、并发、JVM、异常、资源管理：读取 `backend/java-review/SKILL.md`。
- Spring Boot 配置、Web 接口、事务、缓存、消息、生命周期：读取 `backend/springboot-review/SKILL.md`。
- 数据库表结构、SQL、索引、事务、锁、迁移、回滚，以及 MySQL、PostgreSQL、Oracle、SQL Server、SQLite 等数据库引擎：读取 `backend/database-review/SKILL.md`。
- MyBatis、JPA/Hibernate、JDBC、Mapper、Repository、ORM 查询和数据访问实现：读取 `backend/data-access-review/SKILL.md`。
- HTML、CSS、JavaScript/TypeScript、React/Vue、页面交互、构建和浏览器兼容：读取 `frontend-review/SKILL.md`。
- 认证授权、输入处理、注入、Token、敏感信息、依赖和接口安全：读取 `security-review/SKILL.md`。
- 用户要求测试、验收、回归或测试代码：读取 `test-generation/SKILL.md`。

一次任务可以组合多个子 skill。例如登录功能通常同时读取 `frontend-review`、`springboot-review`、`security-review` 和 `test-generation`。

## 清单联动

清单路径解析：CHECKLIST_ROOT 先解析为当前项目/.codex/checklists/；不存在时再解析为 ~/.codex/checklists/fullstack-quality-kit/。本节中的 code-review/、business-test/、db-change/ 和 external-api-doc/ 路径均相对于 CHECKLIST_ROOT；CHECKLIST_ROOT 仅是逻辑标识，不要求设置环境变量。

始终遵循当前 `AGENTS.md` 的授权、范围、备份、验证和交付规则。根据实际变更触发总清单，再读取总清单列出的领域子清单：

- 代码、配置或脚本写入：读取 `CHECKLIST_ROOT/CODE_REVIEW_CHECKLIST.md`，并按技术范围读取 `code-review/` 下的子清单。
- 业务行为、接口结果或页面交互变化：读取 `CHECKLIST_ROOT/BUSINESS_TEST_CHECKLIST.md`，并按测试范围读取 `business-test/` 下的子清单。
- 数据库写入或结构变更：读取 `CHECKLIST_ROOT/DB_CHANGE_CHECKLIST.md`，并按变更类型读取 `db-change/` 下的子清单。
- 用户明确要求扫描、整理或生成外部接口文档：读取 `CHECKLIST_ROOT/EXTERNAL_API_DOC_CHECKLIST.md`，并按整理阶段读取 `external-api-doc/` 下的子清单。

子 skill 只负责领域分析、问题识别、修改建议和测试生成，不得跳过用户确认、总清单或领域子清单。交付时报告使用的子 skill、总清单、领域子清单、验证结果和未覆盖项。

### 子 skill 与领域子清单映射

| 子 skill | 主要领域子清单 |
|---|---|
| `java-review` | `code-review/java-quality.md` |
| `springboot-review` | `code-review/springboot-quality.md`、`business-test/api-behavior.md` |
| `database-review` | `code-review/database-quality.md`、`db-change/schema-definition.md`、`db-change/sql-index-performance.md` |
| `data-access-review` | `code-review/data-access-quality.md`、`code-review/security-code-quality.md`、`business-test/regression-suite.md` |
| `frontend-review` | `code-review/frontend-quality.md`、`business-test/frontend-interaction.md` |
| `test-generation` | `business-test/business-test-baseline.md`、`business-test/api-behavior.md`、`business-test/regression-suite.md` |
| `security-review` | `code-review/security-code-quality.md`、`business-test/permission-boundary.md`、`external-api-doc/api-security.md` |

## 工作方式

1. 先检查项目说明、构建文件、依赖、目录结构、入口和已有测试，确认实际技术栈；不要根据目录名称臆测实现。
2. 明确本次审查范围、未覆盖范围和验收目标。只审查用户授权的代码和配置，不擅自修改业务实现。
3. 读取与变更相关的子 skill，结合跨层调用链检查输入、处理、持久化、响应和页面展示是否一致。
4. 以代码、配置、日志、测试结果或接口数据为证据。无法验证的内容标记为“待确认”，不直接称为缺陷。
5. 先报告阻断性和高风险问题，再报告一般问题和可选优化；同一根因造成的多个表现合并说明。
6. 完成后复核受影响的入口、接口、页面、数据访问和测试范围，记录未验证项目。

## 统一审查结果

每个问题使用以下格式：

~~~text
[级别] 问题标题
位置：文件:行号、类/方法、组件或接口
触发场景：什么输入、操作、状态或环境会触发问题
证据：导致问题的具体代码、配置、数据或执行结果
根因：导致问题发生的直接原因或机制
影响：可能造成的业务、数据、性能、稳定性或安全后果
建议：最小可行修复方向；必要时说明兼容性和回滚注意事项
处理状态：已修复、待确认、未处理或仅供参考
验证：修复后应执行的检查、测试或复现步骤
未覆盖项和已知风险：本次未验证内容及其剩余风险
~~~

级别定义：

- `Blocker`：阻止构建、启动、发布，或造成明显数据破坏、严重安全风险。
- `High`：核心流程错误、越权、数据不一致、资源耗尽或高概率生产故障。
- `Medium`：特定输入或场景下失败，或存在可观的维护、性能和兼容性风险。
- `Low`：局部质量问题、可读性问题或低风险优化。
- `Info`：建议、观察项或需要业务确认的事项，不作为缺陷统计。

不要把个人编码偏好直接定性为问题；格式建议只有在项目规范、构建检查、可维护性或实际行为受到影响时才报告。

## 常规测试用例要求

只要用户要求测试、验收、回归，或改动影响接口结果、业务规则、状态流转和页面交互，默认同时提供常规测试用例。每条用例包含：

~~~text
用例编号
功能模块
测试类型
优先级
前置条件
测试数据
操作步骤
预期结果
清理与后置条件
~~~

至少考虑以下场景：

- 正常流程、最小值、最大值、空值和边界值。
- 非法输入、缺少参数、重复提交、重复消费和外部依赖失败。
- 未登录、无权限、跨用户或跨租户访问。
- 分页、筛选、排序、批量数据和空列表。
- 状态切换前后数据、接口响应和页面展示的一致性。
- 并发、超时、重试、幂等、回滚和部分失败。
- 浏览器兼容、响应式布局、键盘操作和错误页面。
- 回归场景：受影响功能的相邻入口和历史缺陷。

测试代码只有在用户要求或项目已有测试体系适合补充时生成。优先复用项目现有框架和测试工具，不为了生成示例而新增依赖；测试必须可重复、可隔离，并验证业务结果而不只验证方法被调用。

## 交付前检查

- 说明实际审查的文件、模块、入口和子 skill。
- 说明已验证的构建、静态检查、单元测试、接口测试或页面测试结果。
- 区分已修复、待确认、未覆盖和仅供参考的项目。
- 对数据库写入、外部接口、权限和页面交互说明关键边界与已知限制。
- 不把“页面能打开”“测试启动成功”当作业务验收完成。
