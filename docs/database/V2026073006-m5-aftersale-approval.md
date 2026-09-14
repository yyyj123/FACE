# V2026073006 M5 售后、审批与提成调整迁移说明

## 1. 变更范围

迁移文件：`backend-next/src/main/resources/db/migration/V2026073006__m5_aftersale_approval.sql`

采用 Expand-only 策略新增：

- `after_sale_case`：售后工单、业务引用、安全摘要、状态和版本。
- `after_sale_case_log`：所有创建、状态变化、重开和退款关联的追加式日志。
- `approval_instance`：业务对象、审批类型、申请人、决定和有效状态。
- `approval_step`：候选权限、不可变决定、决定人和幂等键。
- `commission_adjustment_request`：提成调整金额、原因、审批引用和应用结果。
- `commission_entry.approval_instance_id`：调整流水的审批事实引用。

新增六个权限：

- `commission:entry:adjust`
- `aftersale:view`
- `aftersale:create`
- `aftersale:manage`
- `approval:view`
- `approval:decide`

## 2. 约束与不可变量

- 工单号和创建幂等键在租户内唯一；工单日志幂等键唯一。
- `approval_instance.active_business_key` 为生成列，同一业务/审批类型最多一个有效待审批实例。
- 审批实例内步骤序号唯一，决定幂等键唯一；终态实例 `active_flag=0`。
- 申请人与决定人由应用服务强制分离，候选权限由服务端再次校验。
- `commission_adjustment_request.approval_instance_id` 唯一，同一审批最多应用一次。
- 提成类型扩展为 `ADJUSTMENT`；调整金额可正可负但不能为零，必须同时引用原始提成和已通过审批，不允许退款引用。
- 原提成金额、规则版本、来源快照和历史结算不更新。

## 3. 模块与事务边界

- 售后模块不写退款、支付、订单、套餐、账户或库存私有表。
- 售后退款调用既有 `RefundApplicationService.request`，真实状态从退款模块读取；售后关闭不代表退款成功。
- 审批通过后通过提成调整应用服务追加 `ADJUSTMENT` 流水；审批、调整申请、流水、历史、审计和 outbox 位于同一事务。
- 审批拒绝或取消只更新调整申请状态，不创建提成流水。
- outbox 仅包含工单、审批、提成流水等业务标识，不包含健康、支付或完整售后正文。

## 4. 兼容与回滚

- 不回填、不猜测历史售后或审批；原退款和采购审批不强迁。
- `/api/v1`、`/api/v2` 及原退款状态机保持不变。
- 回滚先关闭 M5-05 写路由并回退应用，五张新表和调整流水保留只读。
- 已决定审批、售后日志和提成调整不得物理删除；业务纠错追加新实例、新日志或反向调整。
- 不通过修改 Flyway 历史、覆盖决定或修改原提成完成回滚。

## 5. 验证结果

- MySQL 8 全新实例成功执行 22 个迁移，最新版本 `2026073006`。
- 五张新增表、六项权限、六个关键唯一/检查约束存在。
- 会员 SELF、售后状态机、关闭终态、退款应用服务边界、审批职责分离、决定重放幂等和调整流水唯一应用均通过。

