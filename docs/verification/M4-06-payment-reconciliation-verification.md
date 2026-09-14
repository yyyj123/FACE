# M4-06 支付适配与对账验收记录

验收日期：2026-07-30

## 影响范围

- 模块：交易、支付适配、退款、对账、权限、管理端。
- 表：`payment_transaction`、`payment_callback_event`、`reconciliation_batch`、`reconciliation_item`、`reconciliation_resolution`。
- 接口：V3 支付创建、支付回调、退款执行、对账批次查询/运行/处理/关闭。
- 兼容：现金与余额支付保持本地事务；生产外部通道未配置时返回 503，不伪造成功。

## 测试先行

在实现前新增并确认失败：

- `PaymentCallbackVerifierTest`
- `PaymentChannelPolicyTest`
- `ReconciliationLifecyclePolicyTest`
- `M4PaymentReconciliationMigrationContractTest`

实现完成后上述测试及全量后端测试通过。

## 真实链路命令

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File E:\face\scripts\verify-m1-v3-security.ps1 `
  -DatabasePort 3362 `
  -BackendPort 8222 `
  -VerifyM4Payment
```

## 结果

- `M4_PAYMENT_UNCONFIGURED_CHANNEL_GUARD=PASS`
- `M4_PAYMENT_CALLBACK_SIGNATURE_AND_REPLAY=PASS`
- `M4_PAYMENT_CALLBACK_TRANSACTION=PASS`
- `M4_RECONCILIATION_MATCH_AND_DIFFERENCE=PASS`
- `M4_RECONCILIATION_RESOLVE_AND_CLOSE=PASS`
- `M4_RECONCILIATION_UI=PASS`
- `M4_PAYMENT_RECONCILIATION_DATABASE_CONTRACT=PASS`
- `LATEST=2026073002;SUCCESS=1`
- `M1_V3_SECURITY=PASS`

数据库证据：

- 回调事件 1 条，重复回调未重复入账。
- 对账批次 2 条，其中 1 条一致、1 条差异后处理并关闭。
- 差异项 1 条，处理记录 1 条。
- SANDBOX 成功支付 1 条。

浏览器证据：

- 白色主题、导航权限、批次列表和侧栏详情可见。
- 页面无横向视口溢出、无脚本错误、无失败网络请求。
- 截图：`E:\FACE\.artifacts\预览\M4-管理端-支付与对账.png`

## 已知边界

SANDBOX 只用于隔离验收。微信、支付宝、银行卡生产适配器、商户证书、主动查询和通道对账文件尚未配置，因此生产外部支付仍会安全地返回 `DEPENDENCY_UNAVAILABLE`。
