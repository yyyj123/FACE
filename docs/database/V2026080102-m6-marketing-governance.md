# V2026080102 M6 营销治理迁移说明

## 1. 变更范围

迁移：`backend-next/src/main/resources/db/migration/V2026080102__m6_marketing_governance.sql`。

expand-only 新增 `marketing_campaign`、`marketing_campaign_status_history`、`member_marketing_consent`、`member_marketing_consent_history`、`marketing_campaign_audience`、`marketing_delivery_attempt` 六张表；扩展 `notification_message.ck_notification_category` 允许 `MARKETING`；新增 `marketing:view/manage/approve/execute` 四项权限并授予 OWNER、MANAGER、REGIONAL_MANAGER。

不修改订单、支付、退款、套餐、库存、提成或护理记录，不回填历史会员为已同意。

## 2. 索引与约束

- 活动：创建幂等唯一键、门店状态索引、审批队列索引、状态/通道/职责分离/时间一致性 CHECK；
- 状态历史：`tenant_id + campaign_id + idempotency_key` 唯一，不提供更新或删除接口；
- 同意当前水位：`tenant_id + member_id + channel` 唯一，按通道/状态/会员查询索引；
- 同意历史：会员/通道/幂等键唯一，按发生时间追溯；
- 受众：活动/会员唯一，只保存最小标识和同意版本；
- 投递：活动/会员/通道唯一，只有 `IN_APP` 且存在事件与送达时间时才允许 `DELIVERED`；
- 外键覆盖租户、门店、活动、会员、账号、同意、受众和 outbox 事件。

数据库唯一键是并发幂等最终保护；应用层同时校验请求哈希、乐观版本和状态机。

## 3. 兼容与迁移策略

- `/api/v1`、`/api/v2` 和 M6-05 以前接口不变；
- 已有会员没有同意记录，查询层展示 `REVOKED/version=0`；
- `notification_message` 只扩展分类枚举，既有记录和唯一键不变；
- 外部通道可作为不可执行草稿记录，但提交/执行返回 409，不写 `SENT/DELIVERED`；
- 应用必须先完成迁移再发布营销路由和菜单；旧应用忽略新表及新增通知分类。

## 4. 回滚说明

- 首选应用回退：下线营销 API、管理端菜单、会员偏好入口和投递任务，新表保留只读；
- 禁止回填、删除或覆盖同意/撤回、状态、受众、投递、outbox 和审计事实；
- 不修改 Flyway 历史；
- 如必须物理反向 DDL，须先完成独立备份、合规留存确认、应用零访问确认并在维护窗口执行；还原通知分类约束前必须确认没有 `MARKETING` 通知，否则会破坏历史数据；
- 回滚不触碰订单、支付、退款、套餐、库存、提成和护理记录。

## 5. 验证结果

- 全新 MySQL 8 隔离实例迁移至 `2026080102`，成功标记为 1；
- 新表 6 张、关键唯一键 6 个、权限 4 项；
- 合成会员默认未同意，明确授权后活动冻结受众 1、投递 1、站内通知 1；
- 重放执行不重复，自批 0，撤回后新活动无受众，短信提交被拒；
- 验收结束后临时数据库、后端、Vite 和无头浏览器均自动清理。
