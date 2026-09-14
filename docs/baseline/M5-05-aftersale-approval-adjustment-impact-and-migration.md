# M5-05 售后、审批与提成调整影响及迁移说明

## 1. 当前任务与验收

- 阶段：M5-05。
- 输入：四份 V1.2 商业基线、M5 生命周期/API 契约、M5-02 至 M5-04 已验收能力。
- 输出：售后工单与追加日志、通用审批实例/步骤、经审批的提成调整，以及退款应用服务的受控协作。
- 验收：售后状态机、版本、幂等、租户/门店/会员 SELF；申请人与决定人职责分离；决定不可覆盖；提成调整只能在审批通过后追加流水；售后退款不复制退款状态机；日志/outbox 不包含健康、支付或完整敏感正文。

## 2. 影响模块与边界

- `aftersale` 独占 `after_sale_case`、`after_sale_case_log`，只保存安全摘要和外部业务引用。
- `approval` 独占 `approval_instance`、`approval_step`，只保存业务类型、业务标识、候选权限、决定和安全摘要。
- `commission` 扩展 `ADJUSTMENT` 追加式流水；不修改原提成金额、规则版本或来源快照。
- `transaction` 退款状态机不迁移、不复制；售后仅调用 `RefundApplicationService.request`，后续审批/执行继续使用退款模块原权限、状态、幂等和职责分离。
- `notification` 只接收 outbox 事实，留到 M5-06 实现。

禁止售后/审批模块直接写退款、支付、订单、套餐、账户、库存或提成私有表；审批通过后的业务动作由公开应用服务执行。

## 3. 计划表、索引与权限

新增：

- `after_sale_case`：工单号、会员/订单/服务引用、类别、优先级、安全摘要、状态、版本、责任人。
- `after_sale_case_log`：创建、分诊、处理、等待客户、解决、关闭、拒绝、重开和退款关联的追加记录。
- `approval_instance`：业务类型/标识、审批类型、申请人、状态、版本、原实例引用和有效标志。
- `approval_step`：步骤序号、候选权限、决定人、决定、原因和决定时间。

提成调整使用既有 `commission_entry`，通过受控迁移扩展 `entry_type=ADJUSTMENT` 与方向约束；调整保存原流水引用和审批实例引用，正负金额均允许但不得为零。

新增权限：

- `commission:entry:adjust`
- `aftersale:view`
- `aftersale:create`
- `aftersale:manage`
- `approval:view`
- `approval:decide`

关键唯一键：

- 租户内工单号唯一；工单关键写幂等键唯一。
- 同一业务对象/审批类型最多一个有效 `PENDING` 实例。
- 审批实例内步骤序号唯一，决定幂等键唯一。
- 同一审批实例最多生成一条提成调整流水。

## 4. 状态、迁移与回滚

- 售后：`OPEN -> TRIAGED -> PROCESSING -> WAITING_CUSTOMER -> RESOLVED -> CLOSED`；
  `OPEN/TRIAGED/PROCESSING -> REJECTED`；`RESOLVED -> REOPENED -> PROCESSING`。
- 审批：`PENDING -> APPROVED | REJECTED | CANCELLED | EXPIRED`，终态不可覆盖。
- 迁移采用 Expand-only，不回填历史工单或伪造历史审批。
- 旧退款、采购审批不强迁；新通用审批只服务 M5 新流程和后续渐进接入。
- 回滚先关闭 M5-05 写路由，保留新表和调整流水只读；已决定审批、售后日志和提成调整不得物理删除。

## 5. 测试先行清单

1. 售后合法/非法状态跳转、重开、版本冲突与终态保护。
2. 会员 SELF、门店范围、跨租户/跨门店防枚举。
3. 申请人不能决定本人审批；无候选权限、跨范围决定被拒绝。
4. 审批决定不可覆盖，复议创建关联的新实例。
5. 提成调整仅审批通过后追加且同审批实例幂等；原提成不更新。
6. 售后退款复用退款应用服务，错误时不伪造退款成功。
7. 数据库唯一键、检查约束、事务回滚、审计与 outbox。
8. 新鲜 MySQL 8 全历史链、全量测试、后端打包和双前端构建。

