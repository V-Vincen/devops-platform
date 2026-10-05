---
name: test-generation
description: Generate standard test cases and, when requested, test code for full-stack features across backend APIs, databases, frontend interactions, permissions, failures, and regression paths. Use for test planning, acceptance, regression, or test implementation.
---

# Test Generation

先根据需求、接口契约、页面状态、数据结构和已有缺陷识别测试范围，再生成可执行的常规测试用例。测试用例和测试代码是两种交付物：用户只要求测试时默认输出用例；明确要求代码时再实现测试代码。

## 测试分析流程

1. 找到需求入口、页面路由、API、服务方法、Mapper、表结构、状态枚举和已有测试。
2. 画出正常业务流、失败分支、状态变化、权限边界和外部依赖。
3. 根据影响范围选择单元、集成、API、组件、端到端和回归测试，不为每个问题机械地增加所有类型。
4. 用正常、边界、异常、权限、并发/幂等、兼容和回归维度检查覆盖情况。
5. 对写操作确认真实数据、状态、消息和缓存结果，而不只验证页面提示或 Mock 调用。

## 常规测试用例格式

默认使用以下表格字段：

~~~text
用例编号 | 功能模块 | 测试类型 | 优先级 | 前置条件 | 测试数据 | 操作步骤 | 预期结果 | 清理与后置条件
~~~

优先级：

- `P0`：核心主流程、数据安全、登录授权、关键写入，失败会阻止发布。
- `P1`：重要异常、边界、回归和主要兼容场景。
- `P2`：低频组合、辅助交互和可选体验优化。

每个功能至少检查：

- 正常路径和成功响应。
- 必填、空值、最小值、最大值、长度、格式和重复数据。
- 非法输入、超时、依赖失败、数据库失败和部分成功。
- 未登录、无权限、跨用户/租户、过期 Token 和重复提交。
- 分页、排序、筛选、空列表、大批量和结果顺序。
- 状态前后数据、缓存、消息、接口响应和页面展示的一致性。
- 并发更新、重试、幂等、回滚和恢复。
- 兼容旧数据、旧客户端、不同浏览器、移动端和响应式布局。

## 测试类型选择

- 单元测试：验证纯业务规则、边界、异常和状态转换；避免把框架行为全部 Mock 掉。
- 集成测试：验证 Spring Bean、事务、数据库、Mapper、消息或外部客户端之间的真实连接。
- API 测试：验证状态码、响应字段、错误码、权限、幂等和数据副作用。
- 前端组件测试：验证输入、点击、加载、空数据、错误、禁用、键盘和可访问性。
- 端到端测试：只覆盖少量高价值主流程和跨层回归，避免脆弱地依赖实现细节。
- 回归测试：覆盖本次变更入口、共享组件、相邻接口、历史缺陷和受影响数据。

## 测试代码生成

- Java 优先复用项目已有的 JUnit、Mockito、Spring Test 和测试数据工具；不要擅自引入新框架。
- 前端优先复用项目已有的 Vitest、Jest、Testing Library、Playwright 或 Cypress；以项目实际依赖为准。
- 测试名称表达业务行为和预期结果，准备、执行、断言和清理边界清晰。
- 不只断言方法被调用；优先断言返回值、状态、持久化结果、消息、页面和用户可见行为。
- 测试数据隔离，避免依赖执行顺序、系统时间、网络和共享环境；必要时固定时钟和随机数。
- 失败测试必须能定位输入、前置条件和实际结果；避免过度 Mock 导致测试与真实行为脱节。

## 清单联动

清单路径解析：CHECKLIST_ROOT 先解析为当前项目/.codex/checklists/；不存在时再解析为 ~/.codex/checklists/fullstack-quality-kit/。本节中的 code-review/、business-test/、db-change/ 和 external-api-doc/ 路径均相对于 CHECKLIST_ROOT；CHECKLIST_ROOT 仅是逻辑标识，不要求设置环境变量。

- 生成或执行业务测试时，读取 `CHECKLIST_ROOT/BUSINESS_TEST_CHECKLIST.md` 和 `business-test/business-test-baseline.md`，再按范围读取 `api-behavior.md`、`frontend-interaction.md`、`state-transition.md`、`permission-boundary.md`、`concurrency-idempotency.md` 或 `regression-suite.md`。
- 写入测试代码时，读取 `CHECKLIST_ROOT/CODE_REVIEW_CHECKLIST.md`、`code-review/code-quality-baseline.md` 和 `code-review/test-code-quality.md`。
- 测试涉及数据库变更或真实数据写入时，读取 `CHECKLIST_ROOT/DB_CHANGE_CHECKLIST.md` 及相关 `db-change/` 子清单；只读测试数据准备不替代数据验证要求。
- 测试外部接口文档时，只有用户明确要求整理或生成文档才读取 `CHECKLIST_ROOT/EXTERNAL_API_DOC_CHECKLIST.md`；测试本身不等于接口文档整理。

## 输出要求

交付时说明用例覆盖范围、未覆盖项、测试数据假设、执行结果和环境限制。若需求不完整，先列出假设；不要用猜测补齐业务规则并将其当成验收标准。
