# V2026073007 M5 站内通知迁移说明

## 1. 变更范围

迁移文件：`backend-next/src/main/resources/db/migration/V2026073007__m5_notification.sql`

采用 expand-only 策略新增：

- `notification_projection_checkpoint`：保存 outbox 事件的投影游标、重试次数、下次重试时间、失败码和投影结果；
- `notification_message`：保存接收人本人范围的站内消息、安全摘要、站内投递事实、外部通道真实状态、已读时间和乐观锁版本；
- 权限 `notification:view:self`，授予 OWNER、MANAGER、FRONT_DESK、BEAUTICIAN、FINANCE、MEMBER。

不修改 M0–M5-05 的业务表、状态机和既有数据。

## 2. 索引与约束

- `uk_notification_projection_event (tenant_id, event_id)`：同一事件最多成功建立一个投影检查点；
- `idx_notification_projection_retry (status, next_retry_at, id)`：支持按稳定顺序扫描待处理和失败投影；
- `uk_notification_event_recipient_channel (tenant_id, event_id, recipient_account_id, channel)`：防止事件重放时向同一接收人重复生成消息；
- `idx_notification_recipient_status (tenant_id, recipient_account_id, status, created_at, id)`：支撑本人消息、未读筛选和水位线批量已读；
- `idx_notification_shop_event (tenant_id, shop_id, event_type, created_at)`：支撑门店事件追踪和故障排查；
- 外键连接 tenant、shop、account 和 outbox_event；
- CHECK 约束限制投影状态、消息分类、站内通道、站内投递状态、外部通道状态、已读状态以及 `status/read_at` 一致性。

## 3. 模块与事务边界

- 通知模块只消费 outbox_event，不直接访问售后、审批、提成、结算模块的私有表；
- 业务接收人由各模块提供的 `NotificationEventDescriptorResolver` 解析，通知投影服务只处理稳定描述；
- 投影检查点和消息在同一数据库事务中写入，唯一键承担最终幂等保护；
- 失败投影保留检查点和稳定错误码，后台任务按 `next_retry_at` 重试，不伪造已投影；
- 已读接口按 `recipient_account_id` 强制 SELF；单条已读使用版本校验，批量已读使用请求开始时的时间/ID水位线，避免吞掉并发到达的新消息；
- 当前只实现站内通道。外部短信、邮件或企业微信未配置时，`external_status` 必须为 `UNAVAILABLE` 或 `NOT_REQUESTED`，不能记录为已送达。

## 4. 兼容策略

- 新表为空时不回填或猜测历史通知；历史业务事实仍通过原业务模块查询；
- `/api/v1`、`/api/v2` 和 M5-05 以前的 `/api/v3` 契约不变；
- 所有角色只新增本人通知查看权限，不扩大其业务对象读写范围；
- 通知仅保存安全摘要，不复制手机号、证件、健康信息、支付凭据或完整售后正文。

## 5. 回滚说明

- 首选应用回退：关闭通知投影任务和 `/api/v3/notifications` 路由，新表保留只读；
- 已读状态、投影检查点和站内消息属于审计/交付事实，不执行物理删除；
- 如需重新投影，修复解析器后将失败检查点按受控脚本重置为 `PENDING`，依靠唯一键防重；
- 不修改 Flyway 历史，不删除 outbox 业务事件，不把外部通道状态人工改成成功；
- 只有确认没有任何 M5-06 应用实例访问新表后，才可在独立维护窗口执行反向 DDL；生产环境默认不建议物理回滚。

## 6. 验证结果

- MySQL 8 全新实例成功执行 23 个迁移，最新版本 `2026073007`；
- 新增表 2 张、权限 1 项、关键唯一/检查约束 4 项；
- 投影成功 21 个事件、失败 0、重复消息 0；
- SELF 范围、单条乐观锁已读、批量水位线、投影重放幂等和外部通道真实状态均通过；
- 运行结束后隔离数据库、临时后端和临时测试会话均已清理。
