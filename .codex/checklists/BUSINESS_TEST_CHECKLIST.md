# 业务测试清单

适用范围：接口结果、业务规则、状态流转或页面交互发生变化后执行。测试范围覆盖整个受影响功能，不只覆盖代码改动位置。

## 清单路径解析

CHECKLIST_ROOT 先解析为当前项目/.codex/checklists/；不存在时再解析为 ~/.codex/checklists/fullstack-quality-kit/。以下路由均相对于 CHECKLIST_ROOT。

## 领域子清单路由

根据受影响行为继续读取：

- 通用业务测试：`CHECKLIST_ROOT/business-test/business-test-baseline.md`
- 接口行为：`CHECKLIST_ROOT/business-test/api-behavior.md`
- 前端交互：`CHECKLIST_ROOT/business-test/frontend-interaction.md`
- 状态流转：`CHECKLIST_ROOT/business-test/state-transition.md`
- 权限边界：`CHECKLIST_ROOT/business-test/permission-boundary.md`
- 并发与幂等：`CHECKLIST_ROOT/business-test/concurrency-idempotency.md`
- 回归：`CHECKLIST_ROOT/business-test/regression-suite.md`

总清单负责测试范围和结果门禁，领域子清单负责场景覆盖；不得用局部通过代替完整业务验收。

## 基础路径

- [ ] 验证正常路径、失败路径、异常路径、空值、边界值和部分成功场景。
- [ ] 验证状态切换前后数据、页面和接口结果一致。
- [ ] 涉及写操作时，验证重复执行的幂等性，不产生重复记录或错误状态。
- [ ] 每次写操作后核对真实数据库结果，不能只相信页面提示。
- [ ] 同一份底层数据存在多个入口时，交叉验证各入口结果一致。

## 查询和交互

- [ ] 验证搜索、筛选、分页和组合条件。
- [ ] 单独清空筛选条件后，验证结果、可选项和联动条件恢复正确。
- [ ] 验证重置操作，不能只验证选中条件后的正向路径。
- [ ] 需要登录时，先直接访问目标页面，并确认访问域名和登录态来源一致。

## 批量和性能

- [ ] 使用接近真实上限的数据量验证批量选择、提交和渲染。
- [ ] 观察接口耗时、前端计算和页面交互是否出现明显卡顿。
- [ ] 性能优化使用多个不同输入进行测试，不能依据单一样本下结论。
- [ ] 将优化后结果与优化前或生产基线逐条对比，检查数量、顺序和字段完整性。

## 结果

- [ ] 先注明测试范围是本次修改范围还是全项目范围，并记录测试数据范围、操作步骤、预期结果、实际结果和未覆盖项。
- [ ] 每个失败项说明触发场景、证据、用户影响、根因、修改建议、处理状态、验证结果和剩余风险。
- [ ] 发现失败时不得用“页面能打开”代替业务验收；必须核对前端提示、接口返回、数据库状态和日志结果。
