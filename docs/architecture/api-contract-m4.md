# M4 API 契约补充

> 状态：受控补充。商业 API V1.1 只覆盖 M1-M3；本文依据 PRD V1.1、数据库
> V1.1、分阶段实施 V1.1 和既有 V3 通用契约补齐 M4，后续必须同步到商业 API
> 文档的新版本。

## 1. 通用约定

- 基础路径：`/api/v3`
- 鉴权：`Authorization: Bearer <access_token>`
- 门店上下文：`X-Shop-Id`；服务端必须再次校验租户、门店、角色和 SELF 范围
- 金额：十进制字符串，例如 `"128.00"`；禁止 JSON 浮点金额
- 时间：ISO-8601，带时区
- 关键写：`Idempotency-Key` 必填，服务端保存稳定请求摘要
- 并发写：请求体携带 `version` 或 `If-Match`
- 同键同载荷：返回原结果；同键异载荷：HTTP 409 `IDEMPOTENCY_CONFLICT`
- 版本冲突：HTTP 409 `VERSION_CONFLICT`
- 非法状态：HTTP 422 `STATE_TRANSITION_DENIED`
- 余额不足：HTTP 409 `INSUFFICIENT_BALANCE`
- 套餐余额不足：HTTP 409 `INSUFFICIENT_PACKAGE_BALANCE`
- 库存不足：HTTP 409 `INSUFFICIENT_STOCK`
- 通道暂不可用：HTTP 503 `DEPENDENCY_UNAVAILABLE`
- 响应继续使用 `code`、`message`、`data`、`request_id`、`timestamp`

关键写请求摘要至少包含规范化路径、租户、门店、业务对象、金额/数量、版本和业务
字段；不得包含支付密钥或原始敏感凭据。

## 2. 套餐产品与会员套餐

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| GET | `/package-products` | `package:view` | 查询套餐产品及当前版本 |
| POST | `/package-products` | `package:manage` | 创建草稿套餐 |
| PATCH | `/package-products/{id}` | `package:manage` | 按版本修改草稿/上下架 |
| GET | `/members/{memberId}/packages` | `package:view` 或会员 SELF | 查询会员套餐余额和状态 |
| POST | `/members/{memberId}/packages` | `package:manage` | 由已支付订单发放套餐实例 |
| GET | `/package-instances/{id}/ledger` | `package:view` 或会员 SELF | 查询不可变套餐流水 |
| POST | `/package-instances/{id}/write-offs` | `package:writeoff` | 关联服务记录核销 |
| POST | `/package-ledger/{ledgerId}/reversals` | `package:reverse` | 对原核销追加冲正流水 |
| POST | `/package-instances/{id}/freeze` | `package:manage` | 冻结套餐实例 |
| POST | `/package-instances/{id}/unfreeze` | `package:manage` | 解冻套餐实例 |

核销请求示例：

```json
{
  "shop_id": 1,
  "version": 3,
  "service_record_id": 2001,
  "package_product_item_id": 18,
  "quantity": "1",
  "remark": "本次护理核销"
}
```

核销必须锁定实例余额，写不可变流水，关联服务记录并更新余额快照。任何一步失败
均整体回滚。冲正只新增反向流水，不改原流水。

## 3. 会员账户

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| GET | `/members/{memberId}/accounts` | `account:view` 或会员 SELF | 查询余额、赠送金、积分账户 |
| GET | `/member-accounts/{id}/ledger` | `account:view` 或会员 SELF | 查询不可变账户流水 |
| POST | `/member-accounts/{id}/credits` | `account:manage` | 充值、赠送或经批准的调整入账 |
| POST | `/member-accounts/{id}/debits` | `account:manage` | 经批准的消费/调整扣减 |
| POST | `/member-account-ledger/{ledgerId}/reversals` | `account:manage` | 追加原流水的反向流水 |
| POST | `/member-accounts/{id}/freeze` | `account:manage` | 冻结账户 |
| POST | `/member-accounts/{id}/unfreeze` | `account:manage` | 解冻账户 |

通用账户写请求必须包含 `version`、`entry_type`、`amount`、`reference_type`、
`reference_id` 和原因。普通接口不得直接提交 `balance_after`。

## 4. 支付与退款

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| POST | `/orders/{id}/payments` | `order:manage` | 创建支付；外部通道返回当前通道状态 |
| GET | `/payments/{id}` | `order:view` | 查询支付与可退金额 |
| POST | `/payments/{id}/refunds` | `refund:request` | 创建退款申请 |
| POST | `/refunds/{id}/decision` | `refund:approve` | 审批或拒绝退款 |
| POST | `/refunds/{id}/execute` | `refund:execute` | 执行已审批退款；外部通道未配置时返回 503，状态保持 `APPROVED` |
| GET | `/refunds/{id}` | `order:view` | 查询单笔退款状态 |
| GET | `/members/{memberId}/refunds` | `order:view` 或会员 SELF | 查询会员近期退款进度 |

退款申请示例：

```json
{
  "shop_id": 1,
  "order_id": 1001,
  "amount": "128.00",
  "reason_code": "SERVICE_ADJUSTMENT",
  "reason": "经门店审核同意退款"
}
```

退款成功必须：

1. 保留原支付交易和原支付金额。
2. 写独立退款交易。
3. 通过账户/套餐/积分应用服务追加冲正流水。
4. 更新支付与订单的可复算汇总字段。
5. 写审计和 `RefundCompleted` outbox 事件。

退款状态按 `PENDING → APPROVED → PROCESSING → SUCCESS/FAILED` 推进；审批拒绝进入
`REJECTED`。申请人不得审批自己的退款。申请、审批和执行是三个独立动作，分别记录
`created_by`、`approved_by`、`executed_by`；申请与执行分别使用独立幂等键和请求摘要。

现金与会员余额当前使用 `LOCAL_LEDGER` 执行。会员余额退款通过会员账户应用服务追加
`ORDER_REFUND` 流水；完全未使用的套餐订单全额退款通过套餐应用服务追加反向流水并
取消实例。银行卡、微信和支付宝标记为 `EXTERNAL_ADAPTER`；生产适配器未配置时执行
接口返回 HTTP 503 `DEPENDENCY_UNAVAILABLE`，退款仍保持 `APPROVED`，不得伪造成功、
外部退款号或账务冲正。

V2 兼容路由保留原地址，但退款申请与审批统一委托 M4 应用服务；审批只改变审批状态，
不会隐式执行退款。V2 新增 `/api/v2/transactions/refunds/{id}/execute` 作为显式执行
入口，避免旧客户端把“审批通过”误认为“资金已退”。

## 5. 采购与库存批次

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| GET | `/purchase-orders` | `purchase:view` | 查询采购单 |
| GET | `/purchase-orders/{id}` | `purchase:view` | 查询采购单、明细和历史收货 |
| POST | `/purchase-orders` | `purchase:manage` | 创建采购草稿 |
| POST | `/purchase-orders/{id}/submit` | `purchase:manage` | 提交采购单 |
| POST | `/purchase-orders/{id}/decision` | `purchase:approve` | 批准或关闭申请；申请人不得审批本人申请 |
| POST | `/purchase-orders/{id}/receipts` | `purchase:receive` | 部分/全部收货并创建库存批次 |
| GET | `/inventory/batches` | `inventory:view` | 查询批次余额和效期 |

采购单和收货均要求 `Idempotency-Key`；提交、审批和收货均携带采购单 `version`。
收货请求按明细携带采购明细 ID、地点、数量、供应商批号、生产/到期日期和单位成本。
内部批次号由服务端生成。收货应用服务调用库存模块入账，采购模块不能直接写库存私有表。
采购单、明细收货数量、收货单、库存余额、库存批次、批次不可变流水、库存流水、审计和
outbox 必须在同一事务内提交或回滚。

## 6. 对账

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| GET | `/reconciliation-batches` | `reconciliation:view` | 查询对账批次 |
| POST | `/reconciliation-batches` | `reconciliation:manage` | 按通道和账务日期创建/运行对账 |
| GET | `/reconciliation-batches/{id}` | `reconciliation:view` | 查询系统汇总、通道汇总和差异 |
| POST | `/reconciliation-batches/{id}/resolve` | `reconciliation:manage` | 追加差异处理记录 |
| POST | `/reconciliation-batches/{id}/close` | `reconciliation:manage` | 关闭已匹配或已处理批次 |

对账差异处理不能直接修改订单、支付、退款、账户、套餐或库存流水；需要修复业务账时
必须调用对应的退款、冲正或库存调整应用服务。

## 7. SELF 与敏感数据

- 会员只能查询本人套餐、账户和退款。
- 技师只能对分配给本人的服务记录执行套餐核销，不得查看会员资金余额。
- 前台可申请退款但不能审批自己的退款申请。
- 仓管可收货但不能查看支付凭据；财务可对账但不能直接改库存。
- API、日志、审计和 outbox 不返回或记录手机号正文、健康信息、证件、卡号、
  支付密钥、回调验签密钥或原始通道凭据。

## 8. 支付通道回调

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/payment-channels/{channelCode}/callbacks` | 通道签名 | 接收通道事件；仅此入口不要求用户 Bearer 令牌 |

请求头：

- `X-Payment-Timestamp`：Unix 秒时间戳。
- `X-Payment-Nonce`：通道事件随机串。
- `X-Payment-Signature`：对“时间戳 + nonce + 原始请求体”计算的 HMAC-SHA256。

请求体至少包含 `event_id`、`event_type`、`merchant_order_no`、`external_transaction_no`、
`amount`、`channel_status`。服务端必须先验签，再使用 `(channel_code, event_id)` 防重放。
重复的有效事件返回首次处理结果，不重复更新支付、订单或账务。

当前仅提供 `SANDBOX` 隔离适配器。`WECHAT`、`ALIPAY`、`CARD` 未配置生产适配器时，
支付创建和退款执行必须返回 HTTP 503 `DEPENDENCY_UNAVAILABLE`。

## 9. 对账请求与状态

创建对账批次：

```json
{
  "shop_id": 1,
  "channel_code": "SANDBOX",
  "accounting_date": "2026-07-30",
  "channel_payment_count": 1,
  "channel_payment_amount": "268.00",
  "channel_refund_count": 0,
  "channel_refund_amount": "0.00"
}
```

处理差异和关闭批次必须携带 `shop_id` 与当前 `version`。处理差异另需
`resolution_note`，只追加 `reconciliation_resolution`，不修改任何资金流水。

状态机为：

`PENDING → RUNNING → MATCHED | DIFFERENT → RESOLVED → CLOSED`

`MATCHED` 可直接关闭；`DIFFERENT` 必须先处理再关闭；`CLOSED` 不可重新打开。
