# M6 API 契约补充

> 当前实现范围：M6-01 至 M6-06。经营指标版本仍为 `M6-04-v1`；M6-06 新增营销治理与合规站内触达，不改变经营指标定义。

## 1. 通用约定

- 基础路径：`/api/v3`；
- 鉴权：支持当前管理端会话，后端始终从服务端租户上下文解析账号；
- 查询权限：`analytics:view`；报表权限：`analytics:export`；
- 日期范围最多 93 个自然日；
- 报表创建必须携带 `Idempotency-Key`，长度 1–100，只允许 ASCII 字母、数字及 `._:-`；
- 经营分析沿用现有兼容响应封装 `code/msg/data`，下载接口直接返回 CSV 字节；
- 所有门店范围以后端实时权限查询为准，前端菜单隐藏不能替代授权。

## 2. 经营分析查询

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/analytics/sales/overview` | `analytics:view` | 总览、周期、门店、品类、品牌、排行和趋势 |
| GET | `/analytics/sales/lines` | `analytics:view` | 无会员敏感字段的销售明细 |

参数包括 `shopId`、`fromDate`、`toDate`、`itemType=ALL|SERVICE|PRODUCT`。退款按订单级冲正，品项成交额为退款前订单行金额。

## 3. 报表快照

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| POST | `/analytics/reports` | `analytics:export` + 授权门店 | 同步生成不可变 CSV 快照 |
| GET | `/analytics/reports` | `analytics:export` + 本人 | 分页查询本人快照 |
| GET | `/analytics/reports/{reportId}` | `analytics:export` + 本人 | 查询元数据，不返回内容 |
| GET | `/analytics/reports/{reportId}/download` | `analytics:export` + 本人 + 当前门店权限 | 下载未过期 CSV 并审计 |

创建请求：

```json
{
  "shopId": 1,
  "fromDate": "2026-07-01",
  "toDate": "2026-07-28",
  "itemType": "ALL",
  "format": "CSV"
}
```

响应元数据至少包含：`id`、`reportType=SALES_OVERVIEW`、`format=CSV`、`status`、`shopIds`、日期、品项范围、`metricVersion`、`contentSha256`、`contentBytes`、`rowCount`、`readyAt` 和 `expiresAt`。不得包含 `content`。

同一账号使用相同幂等键和相同载荷时返回原 `id`；载荷不同返回 HTTP 409。列表、详情和下载均为创建账号本人范围，其他账号即使拥有导出权限也返回 404。

下载响应：

- `Content-Type: text/csv;charset=UTF-8`；
- `Content-Disposition: attachment`；
- `X-Content-SHA256` 等于响应字节的 SHA-256；
- 文件以 UTF-8 BOM 开头，方便 WPS/Excel 正确识别中文；
- 已过期返回 HTTP 410；权限被撤销返回 HTTP 403。

## 4. 状态、审计与兼容

- 对外状态为 `READY` 或按 `expires_at` 派生的 `EXPIRED`；
- 当前同步生成失败会整笔回滚，不建立 `FAILED` 或伪 `READY`；
- 创建和下载分别写入 `ANALYTICS_REPORT_CREATED`、`ANALYTICS_REPORT_DOWNLOADED`，审计只保存报表 ID、哈希和字节数；
- 不提供更新或删除接口，版本字段保留供未来受控归档；
- `/api/v1`、`/api/v2` 以及 M6-01 至 M6-04 响应字段不删除、不改名。

## 5. 明确不在 M6-05 的内容

- XLSX/PDF 导出；
- 行级退款分摊、利润、成本或外部市场占有率；
- 跨账号共享、邮件/短信发送和自动营销；
- 自动物理删除过期快照。

## 6. 营销活动治理

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET/POST | `/marketing/campaigns` | `marketing:view` / `marketing:manage` | 分页查询或创建门店活动草稿 |
| GET/PATCH | `/marketing/campaigns/{campaignId}` | `marketing:view` / `marketing:manage` | 查询详情或按版本修改草稿 |
| POST | `/marketing/campaigns/{campaignId}/submit` | `marketing:manage` | 提交审批；未配置通道返回 409 |
| POST | `/marketing/campaigns/{campaignId}/decisions` | `marketing:approve` | 独立审批人批准或拒绝 |
| POST | `/marketing/campaigns/{campaignId}/execute` | `marketing:execute` | 冻结明确同意受众并幂等投递站内通知 |
| POST | `/marketing/campaigns/{campaignId}/cancel` | `marketing:manage` | 取消尚未执行的活动并保留历史 |
| GET | `/marketing/campaigns/{campaignId}/audience` | `marketing:view` | 查询不含联系方式的冻结受众标识 |
| GET | `/marketing/campaigns/{campaignId}/deliveries` | `marketing:view` | 查询真实投递账本 |

活动创建请求字段：`shop_id`、`title`、`safe_summary`、`channel`，可选 `action_path`、`scheduled_at`。通道枚举为 `IN_APP|SMS|EMAIL|WECHAT`；当前仅 `IN_APP` 可提交和执行，其他通道明确返回“未配置”，不得写入已发送或已送达。

状态机：`DRAFT -> PENDING_APPROVAL -> APPROVED -> RUNNING -> COMPLETED`；`PENDING_APPROVAL` 可到 `REJECTED`，`DRAFT/PENDING_APPROVAL/APPROVED` 可到 `CANCELLED`。终态不可回退。活动创建人和提交人不得审批本活动，数据库同时约束 `approved_by` 不得等于创建人或提交人。

所有写接口必须携带 ASCII `Idempotency-Key`；修改、提交、决定、执行和取消必须携带当前 `version`。同键异参返回 409；乐观锁冲突返回 409；无明确同意受众时执行返回 409 且不写受众或通知。

## 7. 会员本人营销同意

| 方法 | 路径 | 范围 | 说明 |
|---|---|---|---|
| GET | `/me/marketing-consents` | 会员本人 | 返回四个通道当前水位；无记录按 `REVOKED/version=0` 展示 |
| PUT | `/me/marketing-consents/{channel}` | 会员本人 | 按版本明确同意或撤回，并写不可变历史 |

更新请求为 `status=GRANTED|REVOKED` 与 `version`。同意文本由服务端以 `MARKETING-CONSENT-V1` 和 SHA-256 固化，来源固定为 `MEMBER_SELF`；管理端没有代会员授权接口。撤回只影响之后新执行的活动，不改写已冻结的历史受众和投递事实。

## 8. M6-06 安全、审计与错误行为

- 活动只保存标题、安全摘要和动作路径；禁止在受众、投递、事件或审计中复制手机号、证件、健康/护理详情和支付凭据；
- 会员模块通过 `MarketingAudienceQueryPort` 仅提供有效会员 ID 与收件账号 ID；营销模块不得直接读取会员私有表；
- 通知模块通过 `MarketingDeliveryPort` 写 `MARKETING` 类别站内消息；通知模块不得读取营销私有表；
- 执行事务同时覆盖 `RUNNING`、受众冻结、outbox、通知、投递账本和 `COMPLETED`，任一步失败整体回滚；
- 审计动作包含活动创建、修改、状态变化、送达以及会员同意/撤回，不记录联系方式或完整业务正文；
- 401/403 用于未登录或权限/本人范围失败，404 用于租户/门店范围外对象，400 用于字段和幂等键格式错误，409 用于状态、版本、职责分离、无受众、重复键异参或通道未配置。

## 9. M6-07 员工培训

| 方法 | 路径 | 权限/范围 | 说明 |
|---|---|---|---|
| GET/POST | `/training/courses` | `training:view` / `training:manage` | 查询课程或创建不可变课程版本 |
| POST | `/training/courses/{courseId}/publish` | `training:manage` | 按版本发布课程 |
| POST | `/training/courses/{courseId}/retire` | `training:manage` | 按版本停用课程 |
| GET/POST | `/training/records` | `training:view` / `training:manage` | 查询或分配员工培训 |
| GET | `/training/me` | `training:self` | 查询当前员工本人培训 |
| POST | `/training/me/{recordId}/start` | `training:self` + 本人 | 开始培训 |
| POST | `/training/me/{recordId}/submit` | `training:self` + 本人 | 提交安全证据摘要 |
| POST | `/training/records/{recordId}/verify` | `training:verify` + 非本人 | 独立验证并生成通过/失败结果 |

所有写操作必须带 ASCII `Idempotency-Key`；课程和培训状态动作必须带当前 `version`。课程为 `DRAFT -> ACTIVE -> RETIRED`；培训记录为 `ASSIGNED -> IN_PROGRESS -> SUBMITTED -> PASSED|FAILED`。验证账号与受训员工账号相同时返回 409。

## 10. M6-07 集成客户端管理

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET/POST | `/integrations/clients` | `integration:view` / `integration:manage` | 查询或创建绑定门店的客户端 |
| POST | `/integrations/clients/{clientId}/rotate-secret` | `integration:rotate` | 按版本轮换密钥，旧密钥立即失效 |
| POST | `/integrations/clients/{clientId}/revoke` | `integration:manage` | 按版本撤销客户端 |

创建和轮换成功响应中的 `secret` 只显示一次，幂等重放只返回 `secretShownOnce=false`，不得再次返回完整密钥。当前仅接受 `catalog:read`，限流为 1–60 次/分钟。

## 11. M6-07 开放目录

| 方法 | 路径 | 作用域 | 说明 |
|---|---|---|---|
| GET | `/open/v1/catalog/services` | `catalog:read` | 返回客户端绑定门店的公开服务目录白名单字段 |

开放请求必须携带：

- `Authorization: Bearer <一次性下发的密钥>`
- `X-Integration-Client: <client_code>`
- `X-Integration-Timestamp: <ISO-8601 UTC timestamp>`
- `X-Integration-Nonce: <唯一随机值>`

时间戳允许最多 5 分钟漂移；相同客户端的 Nonce 不得重复。响应不包含会员、护理、健康、支付、库存或员工联系方式。401 表示客户端或密钥无效，403 表示作用域不符，409 表示重放，429 表示超过客户端分钟限流。
