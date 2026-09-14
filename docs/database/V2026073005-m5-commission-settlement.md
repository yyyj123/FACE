# V2026073005 M5 提成结算迁移说明

## 1. 变更范围

迁移文件：`backend-next/src/main/resources/db/migration/V2026073005__m5_commission_settlement.sql`

采用 Expand-only 策略，新增：

- `commission_settlement_batch`：周期、币种、汇总、状态、版本、职责人、支付/关闭/作废事实。
- `commission_settlement_item`：引用提成流水并保存结算时员工、金额和发生时间快照。
- 权限 `commission:settlement:view`、`commission:settlement:manage`、`commission:settlement:approve`。
- 扩充 `commission_entry_history` 合法动作，允许追加 `SETTLED` 与 `SETTLEMENT_VOIDED`。

不修改订单、支付、退款、服务、套餐、库存或原提成金额。

## 2. 索引、约束与不可变量

- `uk_commission_settlement_no`：租户/门店内结算号唯一。
- `uk_commission_settlement_create_key`：创建命令业务幂等。
- `uk_commission_settlement_batch_entry`：同批次不重复引用同一流水。
- `active_entry_id` 为生成列；`uk_commission_settlement_active_entry` 保证一个提成流水最多进入一个有效批次，作废明细仍可保留。
- `ck_commission_settlement_period`：周期开始不晚于结束。
- `ck_commission_settlement_status`：仅允许 `DRAFT/CALCULATED/CONFIRMED/PAID/CLOSED/VOIDED`。
- 明细金额不可为零；批次/明细外键均不级联删除。

## 3. 状态、职责分离与事务

- 正向状态：`DRAFT -> CALCULATED -> CONFIRMED -> PAID -> CLOSED`。
- `DRAFT/CALCULATED/CONFIRMED` 可整批 `VOIDED`；`PAID/CLOSED/VOIDED` 不可重开。
- 只有 `FROZEN` 且未被有效明细占用的提成可计算。
- 创建人或最后计算人不得确认本人批次。
- 确认批次在同一事务中锁定批次、明细和提成，把流水从 `FROZEN` 转为 `SETTLED` 并追加历史。
- 已确认批次在支付前整批作废时，将仍属于该批次的 `SETTLED` 流水恢复为 `FROZEN`，明细只改为无效，不删除。
- 支付只保存安全参考，不存银行卡、账号、凭据或外部支付秘密。

## 4. 兼容与回滚

- 不回填、不猜测历史结算，不把旧提成自动标记为已支付。
- `/api/v1`、`/api/v2` 不变；新能力位于 `/api/v3/commission/settlements`。
- 回滚先关闭结算写路由并回退应用，保留两张表只读。
- 已确认、支付或关闭批次不得物理删除；差额和退款冲正进入后续批次。
- 只有无业务数据的影子环境，在完成备份恢复验证后才可由 DBA 单独审批物理清理。

## 5. 真实数据库验证

- MySQL 8 全新实例成功执行 21 个迁移，最新版本 `2026073005`。
- 两张结算表、三个权限、五个关键约束存在。
- 两条冻结流水进入一个有效批次并结算；职责分离、支付、关闭、终态保护、技师 SELF 摘要和临时授权清理均通过。

