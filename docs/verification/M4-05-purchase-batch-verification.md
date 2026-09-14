# M4-05 采购与批次库存验收记录

验收日期：2026-07-30  
阶段：M4-05 采购、审批、收货、批次库存与管理端  
状态：通过

## 1. 需求覆盖

| 要求 | 实现与证据 |
|---|---|
| 采购幂等 | 持久化幂等键与请求摘要；同键同载荷重放，同键异载荷 409 |
| 职责分离 | 创建人自审返回 403；审批使用独立 `purchase:approve` |
| 版本控制 | 提交、审批和收货均校验采购单版本 |
| 部分收货 | 2 件部分到货后状态为 `PARTIALLY_RECEIVED` |
| 全部收货 | 剩余 3 件到货后状态为 `RECEIVED` |
| 禁止超收 | 尝试超收返回 409，采购数量和库存余额不变 |
| 同事务入账 | 收货、批次、批次流水、库存流水、余额、审计和 outbox 同事务 |
| 故障回滚 | 批次流水插入故障时，收货单、采购数量和库存余额全部回滚 |
| FEFO | 2027-06-01 批次稳定排在 2027-12-01 前，无效期批次最后 |
| 日期契约 | 采购列表、详情和批次日期均返回 `YYYY-MM-DD`，不发生时区减一天 |
| 模块边界 | purchase 模块通过库存应用服务入账，不直接写库存私有表 |
| 管理端 | 新增采购单、审批、收货、批次筛选和权限入口，白色主题 |

## 2. 测试

关键规则测试：

- `PurchaseOrderStatusPolicyTest`
- `PurchaseReceiptPolicyTest`
- `StockBatchFefoPolicyTest`
- `PurchaseModuleBoundaryContractTest`
- `M4PurchaseBatchMigrationContractTest`

全量后端测试：

```powershell
Set-Location E:\face\backend-next
mvn -q test
```

结果：退出码 0，无失败或错误。

管理端生产构建：

```powershell
Set-Location E:\face\admin-next
npm run build
```

结果：`vue-tsc -b && vite build` 退出码 0。

## 3. 隔离 MySQL 8 与浏览器验收

最终命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File E:\face\scripts\verify-m1-v3-security.ps1 `
  -DatabasePort 3357 -BackendPort 8217 -VerifyM4Purchase
```

关键结果：

```text
M4_PURCHASE_ORDERS=2
M4_PURCHASE_RECEIPTS=2
M4_PURCHASE_BATCHES=2
M4_PURCHASE_BATCH_MOVEMENTS=2
M4_PURCHASE_INVENTORY_MOVEMENTS=2
M4_PURCHASE_CREATE_IDEMPOTENCY=PASS
M4_PURCHASE_SEPARATION_OF_DUTIES=PASS
M4_PURCHASE_PARTIAL_AND_FINAL_RECEIPT=PASS
M4_PURCHASE_OVER_RECEIPT_ROLLBACK=PASS
M4_PURCHASE_FEFO_BATCH_ORDER=PASS
M4_PURCHASE_FAILURE_INJECTION_ROLLBACK=PASS
M4_PURCHASE_UI=PASS
M4_PURCHASE_TABLES=6
M4_PURCHASE_PERMISSIONS=4
M4_OPENING_BATCH_PARITY=0/0
M4_OPENING_BATCH_ORPHANS=0
M4_OPENING_BATCH_DUPLICATES=0
LATEST=2026073001;SUCCESS=1
M1_V3_SECURITY=PASS
```

浏览器结果：

- 采购单列表、详情、历史收货和职责分离说明可见。
- 批次库存展示 2 个采购批次，并显示商品、地点、效期、数量、成本和来源。
- 白色主题、导航入口、权限路由和筛选控件正常。
- 浏览器错误、网络失败和页面错误提示均为 0。

截图：

- `E:\FACE\.artifacts\预览\M4-管理端-采购单闭环.png`
- `E:\FACE\.artifacts\预览\M4-管理端-批次库存.png`

## 4. 数据、日志和兼容性自检

- 验收数据为合成供应商、商品、采购单、收货单和审批账号。
- 日志、审计、outbox 和截图未输出密码、原始令牌、手机号、证件、健康信息或支付凭据。
- 迁移为 expand-only；旧应用可忽略新增表。
- 期初批次不修改 `stock_balance`，不会把历史库存重复入账。
- API、数据库、架构、验收记录和 `PRODUCT.md` 已同步。

## 5. 阶段结论

M4-05 门禁通过，可以进入 M4-06 支付适配器与对账。M4-05 不代表 M4 全阶段完成；
生产支付通道、回调验签、对账差异处理和 M4 总验收仍属于后续阶段。
