# M6-06 营销治理与合规触达影响及迁移方案

日期：2026-08-01  
当前阶段：M6 商用运营  
任务编号：M6-06

## 1. 开工检查表

1. 当前任务：M6-06 营销治理与合规触达。
2. 输入：四份 V1.1 商业基线、M5 站内通知、M6-05 完整验收和当前代码规范。
3. 输出：活动状态机、会员本人营销同意/撤回、不可变受众快照、投递尝试账本、站内触达和管理端/会员端入口。
4. 验收：无同意不入选；撤回后不新增触达；申请与审批职责分离；执行幂等；跨租户/门店/账号隔离；外部通道不伪造；测试、构建、全新 MySQL 迁移和隔离 API 通过。
5. 历史数据：所有既有会员默认没有营销同意，不回填为已同意。
6. 先补测试：状态转换、职责分离、同意版本、撤回水位、受众冻结、重复执行、投递唯一键、外部通道真实性和权限范围。
7. 文档：同步迁移、M6 API、模块边界和验收记录；M6 总阶段完成后再用 WPS 同步四份商业文档。

## 2. 影响模块

| 模块 | 影响 |
|---|---|
| marketing | 新增活动应用服务、策略、状态历史和受众/投递账本 |
| member | 通过公开查询端口提供活跃会员最小标识；提供会员本人同意/撤回应用服务，不暴露私有表 |
| notification | 只接收合规站内消息草稿或领域事件；不得直接查询营销私有表 |
| security | 新增查看、管理、审批、执行权限；会员同意仅 SELF |
| admin-next | 新增营销治理页，展示真实状态、同意口径、可用通道和执行结果 |
| front-next | 新增会员本人营销偏好设置，默认关闭并明确撤回影响 |

不修改订单、退款、支付、套餐、库存、提成、护理记录或 M6-05 报表状态。

## 3. 状态与职责分离

活动状态：

```text
DRAFT -> PENDING_APPROVAL -> APPROVED -> RUNNING -> COMPLETED
   |             |              |
   +-------------+--------------+-> CANCELLED
```

- 草稿可按 `version` 修改；提交后不可直接改内容，需取消并创建新版本；
- 创建人/提交人不能审批本人活动；
- 只有 `APPROVED` 可执行，执行前再次计算当前同意并冻结受众；
- `RUNNING/COMPLETED/CANCELLED` 不允许回到草稿；
- 取消和失败都保留状态历史，不物理删除；
- 当前仅 `IN_APP` 可真实执行。`SMS/EMAIL/WECHAT` 记录为 `UNAVAILABLE`，不能进入已投递状态。

## 4. 数据库迁移

计划迁移：`V2026080102__m6_marketing_governance.sql`。

计划新增：

- `marketing_campaign`：活动版本、门店范围、标题、安全摘要、通道、状态、创建/提交/审批/执行账号、幂等键和乐观锁；
- `marketing_campaign_status_history`：不可变状态变化、原因、账号、请求键和请求哈希；
- `member_marketing_consent`：会员本人按通道的 `GRANTED/REVOKED`、版本、来源、同意文本版本和时间；
- `member_marketing_consent_history`：每次同意或撤回的不可变事实、文本版本/哈希、幂等键和请求哈希；
- `marketing_campaign_audience`：执行时冻结的会员/账号最小标识、同意版本与入选时间；
- `marketing_delivery_attempt`：每个活动受众和通道的真实投递状态、稳定错误码、尝试次数和时间。

关键唯一键：

- 活动创建幂等：`tenant_id + created_by + create_idempotency_key`；
- 同意：`tenant_id + member_id + channel`；
- 受众：`tenant_id + campaign_id + member_id`；
- 投递：`tenant_id + campaign_id + member_id + channel`；
- 状态历史幂等：`tenant_id + campaign_id + idempotency_key`。

索引覆盖租户/门店/状态、审批队列、活动受众、会员同意和投递重试。CHECK 约束固定状态、通道、同意状态、版本和时间一致性。

## 5. 兼容与回滚

- expand-only，不重命名或删除既有列；
- 历史会员没有同意记录等同于未同意，不自动补 `GRANTED`；
- `/api/v1`、`/api/v2` 和 M6-05 以前接口不变；
- 应用回退时下线营销路由/任务，新表保留只读；
- 不删除同意、撤回、活动、受众、投递或审计事实；
- 物理反向 DDL 仅允许在独立备份、合规留存确认和无应用访问的维护窗口执行。

## 6. API 和权限计划

管理端：

- `GET/POST /api/v3/marketing/campaigns`
- `GET/PATCH /api/v3/marketing/campaigns/{id}`
- `POST /api/v3/marketing/campaigns/{id}/submit`
- `POST /api/v3/marketing/campaigns/{id}/decisions`
- `POST /api/v3/marketing/campaigns/{id}/execute`
- `POST /api/v3/marketing/campaigns/{id}/cancel`
- `GET /api/v3/marketing/campaigns/{id}/audience`
- `GET /api/v3/marketing/campaigns/{id}/deliveries`

会员端：

- `GET /api/v3/me/marketing-consents`
- `PUT /api/v3/me/marketing-consents/{channel}`

权限：`marketing:view`、`marketing:manage`、`marketing:approve`、`marketing:execute`。所有写接口必须携带幂等键；修改和决定携带 `version`。会员偏好只允许本人，管理端不得代替会员勾选同意。

## 7. 敏感数据与投递真实性

- 活动受众和投递账本不复制手机号、证件、健康资料、护理详情或支付凭据；
- 本阶段不根据健康/护理记录做营销画像；
- 同意文本使用版本号和哈希固化，不在日志输出完整联系方式；
- 站内触达只记录安全标题、摘要和动作路径；
- 外部供应商未配置时响应和数据库均保持 `UNAVAILABLE`，禁止写 `SENT/DELIVERED`；
- 任何活动执行前再次检查活动状态、当前权限和同意，随后冻结受众，避免撤回后仍生成新投递。

## 8. 分阶段实现顺序

1. 策略和迁移契约红灯测试；
2. 活动/同意/受众/投递表及索引约束；
3. 会员本人同意与撤回服务；
4. 活动草稿、提交、审批、取消状态机；
5. 受众冻结与幂等站内投递；
6. 管理端和会员端白色主题界面；
7. 全量测试、构建、全新 MySQL 迁移、权限/职责分离/幂等/撤回/外部通道真实性验收；
8. 文档同步和 M6-06 源码快照。
