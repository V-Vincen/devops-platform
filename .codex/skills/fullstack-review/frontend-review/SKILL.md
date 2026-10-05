---
name: frontend-review
description: Review frontend applications built with HTML, CSS, JavaScript/TypeScript, React, Vue, or similar frameworks for correctness, interaction states, accessibility, compatibility, performance, API integration, build quality, and frontend security.
---

# Frontend Review

审查前端页面、组件、状态、接口交互、构建和浏览器运行行为。先确认实际框架、版本、路由、状态管理、测试工具和构建命令，不把框架习惯当成项目事实。

## 审查范围

### 1. 页面结构与可访问性

- HTML 语义、标题层级、表单标签、按钮语义、焦点顺序和键盘操作是否完整。
- 图片、图标、颜色、错误提示和动态内容是否有可理解的文本或辅助信息。
- 弹窗、下拉框、表格、分页和状态切换是否能被键盘和辅助技术正确使用。
- 不以颜色作为唯一状态表达；文本和交互反馈应区分成功、失败、警告和禁用。

### 2. CSS、响应式与兼容

- 布局在目标屏幕尺寸、缩放、长文本、空数据和极端内容下是否稳定。
- 样式作用域、层叠、z-index、滚动容器、固定定位和弹性布局是否产生溢出或遮挡。
- 断点、触摸区域、移动端输入和浏览器兼容是否符合项目支持范围。
- 设计 token、主题、暗色模式和组件变体是否保持一致；避免局部硬编码破坏全局样式。

### 3. JavaScript / TypeScript 与组件

- 类型定义、可空值、异步异常、竞态、取消请求和组件卸载后的状态更新是否安全。
- 组件职责、属性、事件、生命周期、依赖数组和响应式数据是否正确。
- React/Vue 的状态更新、派生状态、列表 key、缓存和副作用是否可能导致旧数据或重复请求。
- 避免将业务规则、接口调用和大型状态逻辑全部堆在页面组件中。
- 事件监听、定时器、订阅、Observer 和 Blob/URL 等资源是否在销毁时释放。

### 4. API、表单与交互状态

- 请求参数、响应类型、错误码、分页、排序、权限和接口版本是否与后端契约一致。
- 页面是否覆盖加载中、成功、空数据、校验失败、权限不足、超时、网络失败和重试状态。
- 表单是否处理必填、格式、长度、范围、重复提交、草稿、重置和离开页面提示。
- 写操作完成后是否正确刷新、失效或更新本地缓存；不能只依赖成功 Toast。
- 快速切换筛选、路由和搜索条件时，旧请求结果不能覆盖新请求结果。

### 5. 性能与构建

- 首屏资源、代码分割、懒加载、图片尺寸、重复渲染、大列表和缓存策略是否合理。
- 构建产物、环境变量、Source Map、依赖版本和开发配置是否会泄露敏感信息或影响发布。
- 只有在有构建分析、浏览器指标或实际复现时报告性能问题；说明测试设备、数据量和基线。
- 关注内存泄漏、事件监听累积、未取消请求和页面切换后的后台任务。

### 6. 前端安全

- 用户输入、富文本、URL、HTML 模板和第三方内容是否存在 XSS 风险。
- Token、Cookie、LocalStorage、错误日志和埋点是否泄露敏感信息。
- CORS、CSRF、开放重定向、文件上传、下载文件名和第三方脚本来源是否受控。
- 前端权限控制只能改善体验；关键授权必须由后端再次校验。

## 边界

- 后端接口实现、事务、配置和消息读取 `springboot-review`。
- 数据库、SQL、表结构和索引读取 `database-review`；MyBatis、JPA/Hibernate、JDBC 和 Mapper 读取 `data-access-review`。
- 通用注入、认证授权、敏感数据和依赖安全读取 `security-review`。
- 页面交互、浏览器兼容和接口联调场景读取 `test-generation`。

## 清单联动

清单路径解析：CHECKLIST_ROOT 先解析为当前项目/.codex/checklists/；不存在时再解析为 ~/.codex/checklists/fullstack-quality-kit/。本节中的 code-review/、business-test/、db-change/ 和 external-api-doc/ 路径均相对于 CHECKLIST_ROOT；CHECKLIST_ROOT 仅是逻辑标识，不要求设置环境变量。

- 写入前端代码、配置或测试代码时，读取 `CHECKLIST_ROOT/CODE_REVIEW_CHECKLIST.md`、`code-review/code-quality-baseline.md`、`code-review/frontend-quality.md`；涉及安全代码时另读 `code-review/security-code-quality.md`，测试代码另读 `code-review/test-code-quality.md`。
- 页面交互、接口结果、表单、路由或状态变化时，读取 `CHECKLIST_ROOT/BUSINESS_TEST_CHECKLIST.md`，再读取 `business-test/frontend-interaction.md`、`business-test/api-behavior.md`、`business-test/permission-boundary.md` 或 `business-test/regression-suite.md`。
- 用户明确要求整理或生成外部接口文档时，读取 `CHECKLIST_ROOT/EXTERNAL_API_DOC_CHECKLIST.md` 及相关外部接口文档子清单；普通前端接口联调不触发文档清单。

## 输出要求

问题必须关联具体组件、页面、路由、事件、请求或构建配置，说明触发步骤和用户可见影响。页面代码审查不得只报告格式偏好；优先报告状态遗漏、数据错误、兼容性、可访问性、安全和性能证据。
