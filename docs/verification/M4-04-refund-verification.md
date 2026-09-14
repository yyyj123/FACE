# M4-04 退款生命周期验收记录

验收日期：2026-07-30  
阶段：M4-04 退款申请、审批、执行、会员查询与账务冲正  
状态：通过

## 1. 需求覆盖

| 要求 | 实现与证据 |
|---|---|
| 申请幂等 | 独立申请幂等键与 SHA-256 摘要；同键异载荷 409 |
| 审批分离 | 申请人自审返回 403；审批只进入 `APPROVED` |
| 显式执行 | `refund:execute`、独立执行幂等键、责任人和状态 |
| 余额退款 | 追加 `ORDER_REFUND` 账户流水，余额恢复至支付前快照 |
| 套餐退款 | 未使用套餐全额退款追加反向流水并取消实例 |
| 外部通道 | 未配置生产适配器时返回 503，状态保持 `APPROVED` |
| 会员查询 | 会员只能查询本人退款；跨会员查询隐藏式 404 |
| 三端权限 | 管理端操作、会员端只读进度、技师端无资金数据 |
| 审计与事件 | 成功退款写审计和 `RefundCompleted` outbox |

## 2. 测试先行与缺陷修复

- `M4RefundMigrationContractTest`
- `M4RefundLifecycleContractTest`
- `RefundExecutionPolicyTest`
- `ResourceBookingConcurrencyContractTest`

整链验收曾捕获 M2 资源并发“双成功”。根因是 MySQL `REPEATABLE READ` 下普通
`COUNT(*)` 使用事务早期快照；第二个请求取得资源行锁后仍看不到刚提交的占用。测试先
失败，随后把占用校验改为 `SELECT id ... FOR UPDATE` 当前读。复验只生成 1 条预约和
1 条资源占用，另一个请求返回 409。该修复不改表结构或接口。

## 3. 隔离 MySQL 8 与业务验收

命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File E:\face\scripts\verify-m1-v3-security.ps1 `
  -DatabasePort 3344 -BackendPort 8204 -VerifyM4Refund
```

关键结果：

```text
M2_RESERVED_ROWS=1
M2_APPOINTMENT_ROWS=1
M2_MASTER_DATA_AND_RESOURCE_CONCURRENCY=PASS
M4_REFUND_STATUS=SUCCESS
M4_REFUND_REQUEST_HASH=64
M4_REFUND_EXECUTION_HASH=64
M4_REFUND_PACKAGE_STATUS=CANCELLED
M4_REFUND_PACKAGE_REVERSALS=1
M4_EXTERNAL_REFUND_STATUS=APPROVED
M4_BALANCE_REFUND_STATUS=SUCCESS
M4_BALANCE_REFUND_LEDGER=1
M4_REFUND_COLUMNS=9
M4_REFUND_PERMISSIONS=2
M4_REFUND_PROCESSING_CONSTRAINT=1
M4_REFUND_REQUEST_IDEMPOTENCY=PASS
M4_REFUND_SEPARATION_OF_DUTIES=PASS
M4_REFUND_PACKAGE_REVERSAL=PASS
M4_BALANCE_REFUND_RESTORED=PASS
M4_MEMBER_REFUND_SCOPE=PASS
M4_EXTERNAL_REFUND_NOT_FAKED=PASS
M4_DATABASE_CONTRACT=PASS
LATEST=2026072902;SUCCESS=1
M1_V3_SECURITY=PASS
```

## 4. 全量测试与生产构建

```text
backend-next: Tests run: 93, Failures: 0, Errors: 0, Skipped: 0
front-next: vue-tsc -b && vite build — PASS
admin-next: vue-tsc -b && vite build — PASS
```

## 5. 三端浏览器与视觉验收

最终命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File E:\face\scripts\verify-m1-v3-security.ps1 `
  -DatabasePort 3347 -BackendPort 8207 -VerifyM4Refund -VerifyM4Ui
```

结果：

```text
technician.lifecycleGuard=true
memberRefund.rows=3
memberRefund.threeSteps=true
memberRefund.noManagementAction=true
adminRefund.rows=5
adminRefund.completed=true
adminRefund.externalGuard=true
adminRefund.separationCopy=true
browserErrors=[]
M4_THREE_CLIENT_UI=PASS
LATEST=2026072902;SUCCESS=1
M1_V3_SECURITY=PASS
```

- 技师端在套餐退款取消后不再提供该套餐核销选项，也不显示退款资金数据。
- 会员端 390px 显示申请、审核、实际退款三阶段，无管理操作和横向溢出。
- 管理端区分已退款、退款待执行和外部通道待配置，不把未接通通道标成成功。
- 三端均为白色主题，无错误提示或浏览器控制台错误。

截图：

- `E:\FACE\.artifacts\预览\M4-技师端-套餐核销.png`
- `E:\FACE\.artifacts\预览\M4-会员端-退款进度.png`
- `E:\FACE\.artifacts\预览\M4-管理端-退款闭环.png`

## 6. 安全、兼容与文档自检

- 验收数据均为合成会员、订单、支付、退款和套餐数据。
- 日志与验收摘要未输出密码、原始令牌、支付凭据、证件或健康信息。
- V2 退款路由保留并委托同一应用服务；审批与执行已拆分。
- 数据库迁移说明、M4 API 契约和退款生命周期说明已同步。
- 迁移为 expand-only，无自动破坏性回滚。

## 7. 阶段结论

M4-04 门禁全部通过，可以进入 M4-05 采购与批次库存。M4-04 不冒充 M4 全阶段
完成；采购收货、生产支付通道、对账差异处理和 M4 总验收仍属于后续阶段。
