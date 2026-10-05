---
name: security-review
description: Review full-stack applications for authentication, authorization, tenant isolation, injection, XSS, CSRF, SSRF, unsafe deserialization, file upload, secrets, tokens, dependencies, logging, CORS, and abuse controls. Use for security review or when a change handles untrusted input, identity, permissions, or sensitive data.
---

# Security Review

以攻击面、信任边界、敏感数据流和实际授权逻辑为依据审查安全性。区分确定性漏洞、可利用风险和需要环境确认的安全建议；不要只根据函数名称或框架存在就断言存在漏洞。

## 审查范围

### 1. 身份认证与会话

- 登录、退出、密码重置、多因素认证、Token 刷新、Cookie 属性和会话失效是否正确。
- Token、Cookie、LocalStorage、日志、错误响应和埋点是否泄露敏感信息。
- 认证失败、暴力尝试、账户枚举、验证码和频率限制是否满足风险要求。
- 过期 Token、重复使用 Token、跨站请求和设备/会话管理是否有明确策略。

### 2. 授权与数据隔离

- 每个读取、修改、删除和导出入口是否校验用户、组织、租户、角色和资源归属。
- 不能只依赖前端隐藏按钮或 URL 不可见；后端必须执行最终授权。
- 批量接口、导入导出、下载、异步任务和消息消费是否复用相同授权边界。
- 管理员、普通用户、跨租户、资源创建者和已离职用户的行为是否分别验证。

### 3. 输入与执行安全

- SQL/MyBatis 参数、命令、模板、表达式、脚本、文件路径和 URL 是否经过约束和安全编码。
- `#{}` 与 `${}`、字符串拼接、动态排序字段和白名单缺失是否可能造成 SQL 注入。
- HTML、富文本、Markdown、URL 和第三方内容是否可能造成 XSS 或开放重定向。
- 服务端请求 URL 是否受控，避免 SSRF、内网探测和云元数据访问。
- 反序列化、文件上传、压缩包、图片解析和 XML 处理是否限制类型、大小、路径和资源消耗。

### 4. Web 与接口安全

- CSRF、CORS、SameSite、Origin/Referer、缓存和错误响应是否符合部署方式。
- JSON、文件、批量和分页接口是否限制大小、数量、深度、频率和超时。
- 错误页、调试端点、Actuator、Swagger、Source Map 和健康检查是否暴露不必要信息。
- 重复提交、重放、批量滥用、资源耗尽和业务流程绕过是否有控制。

### 5. 密钥、日志与依赖

- 代码、配置、构建产物和提交历史中是否存在明文密码、密钥、Token 或证书。
- 日志、异常、审计和监控是否脱敏，是否保留必要的主体、资源、结果和时间信息。
- 依赖版本、镜像、构建插件和第三方脚本是否存在已知漏洞或不受控来源。
- 生产配置、权限、网络访问、备份和密钥轮换是否符合部署环境要求。

## 验证要求

- 给出攻击前置条件、入口、输入、授权判断、数据影响和复现步骤。
- 对无法访问的部署配置、网络策略、密钥系统和依赖扫描结果标记为待确认。
- 安全问题必须提供最小修复方向和回归测试，包括允许路径、拒绝路径、跨用户/租户和异常路径。
- 不输出真实密钥、Token、个人信息或可直接用于攻击的敏感数据。

## 边界

- Java 实现细节读取 `java-review`。
- Spring Security、过滤器、配置、事务和接口实现读取 `springboot-review`。
- 数据库 SQL、租户字段、数据隔离和结构约束读取 `database-review`；Mapper、参数绑定、数据访问条件和 ORM 风险读取 `data-access-review`。
- 前端 XSS、Token 存储、CORS 和页面交互读取 `frontend-review`。
- 读取 `test-generation` 生成安全回归用例和权限矩阵。

## 清单联动

清单路径解析：CHECKLIST_ROOT 先解析为当前项目/.codex/checklists/；不存在时再解析为 ~/.codex/checklists/fullstack-quality-kit/。本节中的 code-review/、business-test/、db-change/ 和 external-api-doc/ 路径均相对于 CHECKLIST_ROOT；CHECKLIST_ROOT 仅是逻辑标识，不要求设置环境变量。

- 写入认证、授权、输入处理、安全配置或测试代码时，读取 `CHECKLIST_ROOT/CODE_REVIEW_CHECKLIST.md`、`code-review/code-quality-baseline.md` 和 `code-review/security-code-quality.md`；测试代码另读 `code-review/test-code-quality.md`。
- 认证、授权、越权、敏感数据或接口行为变化时，读取 `CHECKLIST_ROOT/BUSINESS_TEST_CHECKLIST.md`，并读取 `business-test/permission-boundary.md`、`business-test/api-behavior.md`、`business-test/concurrency-idempotency.md` 或 `business-test/regression-suite.md`。
- 安全问题涉及数据库写入、租户字段、审计数据或结构变更时，读取 `CHECKLIST_ROOT/DB_CHANGE_CHECKLIST.md` 及相关 `db-change/` 子清单。
- 用户明确要求整理或生成外部接口文档时，读取 `CHECKLIST_ROOT/EXTERNAL_API_DOC_CHECKLIST.md`、`external-api-doc/api-security.md` 和其他实际相关子清单；安全审查本身不等于接口文档整理。
