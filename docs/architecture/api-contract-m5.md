# M5 API 契约补充

> M5-06 已实现并验收站内通知和管理端、技师端、会员端业务界面；本契约所列 M5 路由均已有真实后端实现，不存在伪成功占位。

> 状态：M5-05 已实现规则、流水、结算、售后、审批和经审批提成调整；通知与三端界面按 M5-06 实现。四份商业 V1.2 文档定义了 M5 范围但未列出完整接口；
> 本文先固定 `/api/v3` 契约，M5-07 验收后同步到下一版商业文档。

## 1. 通用约定

- 基础路径：`/api/v3`
- 鉴权：`Authorization: Bearer <access_token>`
- 门店上下文：请求体/查询参数 `shop_id` 与 `X-Shop-Id` 一致，服务端再次校验。
- 金额：十进制字符串，禁止 JSON 浮点。
- 时间：ISO-8601，带明确时区。
- 关键写：`Idempotency-Key` 必填并保存稳定请求摘要。
- 并发写：携带 `version`；冲突返回 HTTP 409。
- 响应：继续使用 `code`、`message`、`data`、`request_id`、`timestamp`。
- 同键同载荷返回原结果；同键异载荷返回 `IDEMPOTENCY_CONFLICT`。
- 日志和 outbox 不含手机号、健康信息、证件、支付凭据或完整售后正文。

## 2. 提成规则

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/commission/rules` | `commission:rule:view` | 查询规则及版本 |
| GET | `/commission/rules/{ruleId}` | `commission:rule:view` | 查询规则版本详情 |
| POST | `/commission/rules` | `commission:rule:manage` | 创建规则草稿 |
| PATCH | `/commission/rules/{ruleId}` | `commission:rule:manage` | 按版本修改草稿 |
| POST | `/commission/rules/{ruleId}/publish` | `commission:rule:manage` | 发布不可变版本 |
| POST | `/commission/rules/{ruleId}/retire` | `commission:rule:manage` | 退役规则版本 |
| POST | `/commission/simulations` | `commission:rule:view` | 按历史快照模拟，不入账 |
| GET | `/commission/source-snapshots/{snapshotId}` | `commission:entry:view` 或技师 SELF | 查询不可变来源快照 |

规则发布必须拒绝同范围、同优先级和生效区间重叠。已发布规则不能 PATCH。

## 3. 提成流水

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| GET | `/commission/entries` | `commission:entry:view` 或技师 SELF | 查询提成流水 |
| GET | `/commission/entries/{entryId}` | 同上 | 查询规则和来源快照 |
| POST | `/commission/entries/{entryId}/adjustments` | `commission:entry:adjust` | M5-05 经审批追加调整 |
| POST | `/commission/entries/{entryId}/freeze` | `commission:freeze` | 冻结待结算 |
| POST | `/commission/entries/{entryId}/unfreeze` | `commission:freeze` | 经授权解冻 |
| 内部 | 无公开路由 | 提成投影应用服务 | 退款成功事件追加冲正 |

冲正不修改原提成。退款联动使用
`refund_id + original_commission_entry_id` 唯一键。
契约标识 `/commission/entries/{entryId}/reversals` 仅代表内部冲正端口，
当前不注册公开 HTTP 路由，退款只能由受控事件投影触发。

## 4. 结算

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/commission/settlements` | `commission:settlement:view` | 查询结算批次 |
| POST | `/commission/settlements` | `commission:settlement:manage` | 创建周期批次 |
| GET | `/commission/settlements/{batchId}` | `commission:settlement:view` | 批次和不可变明细 |
| POST | `/commission/settlements/{batchId}/calculate` | `commission:settlement:manage` | 计算草稿明细 |
| POST | `/commission/settlements/{batchId}/confirm` | `commission:settlement:approve` | 确认批次 |
| POST | `/commission/settlements/{batchId}/mark-paid` | `commission:settlement:approve` | 标记支付事实 |
| POST | `/commission/settlements/{batchId}/close` | `commission:settlement:approve` | 关闭批次 |
| POST | `/commission/settlements/{batchId}/void` | `commission:settlement:approve` | 支付前整批作废并保留明细历史 |
| GET | `/technician/commission-summary` | 技师 SELF | 本人提成与结算摘要 |

申请创建或计算批次的人不能单独确认自己发起的批次。已关闭批次不可重开。

## 5. 售后工单

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| GET | `/after-sales/cases` | `aftersale:view` 或会员 SELF | 查询售后工单 |
| POST | `/after-sales/cases` | `aftersale:create` 或会员 SELF | 创建工单 |
| GET | `/after-sales/cases/{caseId}` | 同上 | 工单详情和追加记录 |
| POST | `/after-sales/cases/{caseId}/actions` | `aftersale:manage` | 受状态机约束的处理动作 |
| POST | `/after-sales/cases/{caseId}/refunds` | `refund:request` | 经退款应用服务申请退款 |
| POST | `/after-sales/cases/{caseId}/reopen` | `aftersale:manage` 或会员 SELF 受限 | 追加重开 |

售后工单的“已解决/已关闭”不等同于退款成功；响应必须返回关联退款真实状态。

## 6. 审批

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| GET | `/approvals` | `approval:view` | 查询本人待办或授权范围历史 |
| GET | `/approvals/{approvalId}` | `approval:view` | 审批实例和步骤 |
| POST | `/approvals/{approvalId}/decisions` | `approval:decide` | 批准/拒绝 |
| POST | `/approvals/{approvalId}/cancel` | 发起人/管理权限 | 未决定前取消 |

审批决定请求必须携带 `version`、`action` 和必要原因。申请人不能审批本人申请；
已决定实例不可覆盖。

## 7. 通知

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| GET | `/notifications` | `notification:view:self` | 查询本人站内通知 |
| POST | `/notifications/{notificationId}/read` | `notification:view:self` | 幂等标记本人已读 |
| POST | `/notifications/read-all` | `notification:view:self` | 按版本/时间水位批量已读 |

通知接口不返回其他接收人信息；外部渠道未配置时不得返回“已送达”。

## 8. 错误码

| HTTP | code | 场景 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 参数、金额或时间区间错误 |
| 403 | `FORBIDDEN` | 无权限、职责分离失败 |
| 404 | `RESOURCE_NOT_FOUND` | 对象不存在或范围外防枚举 |
| 409 | `VERSION_CONFLICT` | 乐观锁冲突 |
| 409 | `IDEMPOTENCY_CONFLICT` | 同键异载荷 |
| 409 | `RULE_SCOPE_CONFLICT` | 规则范围/生效区间冲突 |
| 409 | `SETTLEMENT_CONFLICT` | 流水已进入有效批次 |
| 422 | `STATE_TRANSITION_DENIED` | 非法状态动作 |
| 503 | `DEPENDENCY_UNAVAILABLE` | 外部通知或依赖未配置 |

## 9. 兼容和事件

- `/api/v1`、`/api/v2` 不改变。
- `ServiceRecordCompleted`、`PaymentSucceeded`、`RefundCompleted` 继续由原模块发布。
- M5 消费者至少一次投递、业务唯一键幂等。
- 提成冲正暂时失败时冻结相关结算并告警，不能覆盖原提成或伪造完成。
- 未实现接口必须返回明确错误或不注册路由，禁止返回伪成功。

## 10. M5-06 通知实现补充

`GET /notifications` 支持 `status=ALL|UNREAD|READ`、`page` 和 `page_size`，仅返回当前 access token 对应账号的消息；响应包含 `records`、`unreadCount`、`page`、`pageSize`。

`POST /notifications/{notificationId}/read` 必须携带 `Idempotency-Key` 和请求体 `version`。非本人消息按范围外资源处理；版本冲突返回 HTTP 409，不允许最后写入覆盖。

`POST /notifications/read-all` 必须携带 `Idempotency-Key`。服务端在请求开始时固定 `created_at + id` 水位线，只更新水位线之前的本人未读消息；并发到达的新消息保持未读。

消息字段只允许安全摘要、业务类型/编号、站内动作路径和真实投递状态。当前 `channel=IN_APP`、`delivery_status=DELIVERED`；未配置外部通道时 `external_status=UNAVAILABLE`，不得返回外部已送达。

通知投影通过 outbox 至少一次消费，由 `(tenant_id,event_id,recipient_account_id,channel)` 唯一键防重；解析失败保留可重试检查点，不直接访问其他模块私有表。
