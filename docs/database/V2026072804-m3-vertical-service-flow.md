# V2026072804 M3 数据库变更说明

## 变更性质

迁移文件：

`backend-next/src/main/resources/db/migration/V2026072804__m3_vertical_service_flow.sql`

本迁移为 expand-only 前向兼容迁移。它不删除、不重命名旧表、旧列或历史数据，V1/V2/M2 代码可忽略新增表和可空列。

## 新表

### `customer_confirmation`

保存顾客对已完成护理结果的独立确认事实：

- 状态仅允许 `PENDING`、`CONFIRMED`、`REJECTED`。
- 拒绝必须填写原因，非拒绝状态不得保存拒绝原因。
- 同一租户、服务记录和确认类型只能有一条记录。
- 顾客操作使用 `version`、`idempotency_key` 与 `request_hash` 防止并发覆盖和同键异载荷。
- 外键连接租户、门店、预约、服务记录、会员和操作账号。

### `service_record_correction`

保存已完成护理事实的追加式更正：

- 原始 `service_record` 不更新。
- 保存 `base_version`、更正原因、JSON 更正字段、幂等键、请求哈希与操作账号。
- 同一租户内幂等键唯一。
- 更正原因由数据库 `CHECK` 保证非空。

## 扩展字段

- `sales_order.create_idempotency_key`
- `sales_order.create_request_hash`
- `service_record.completion_request_hash`
- `payment_transaction.request_hash`

字段均为可空，以保证历史记录与旧应用兼容；新领域服务写入时强制使用。

## 索引与约束

- `uk_customer_confirmation_record_type`：阻止同一护理结果重复生成确认。
- `uk_customer_confirmation_idempotency`：顾客确认幂等。
- `idx_customer_confirmation_member_status`：顾客端按本人和状态查询。
- `idx_customer_confirmation_shop_time`：管理端按门店和时间追踪。
- `uk_service_correction_idempotency`：更正命令幂等。
- `idx_service_correction_record_time`：按护理记录顺序读取更正历史。
- `idx_service_correction_shop_time`：门店审计查询。
- `uk_sales_order_create_idempotency`：订单创建幂等。
- 所有新事实表使用 InnoDB 外键、状态 `CHECK` 和 `utf8mb4`。

## 权限数据

新增：

- `service_record:correct`，风险级别 `CRITICAL`，授予 `OWNER`、`MANAGER`。
- `customer_confirmation:view`，风险级别 `SENSITIVE`，授予 `OWNER`、`MANAGER`、`MEMBER`。

插入采用不存在检查，不重复写入 `role_permission`。

## 发布兼容策略

1. 备份数据库并校验备份 SHA-256。
2. 在影子 MySQL 8 执行全部 Flyway 迁移和 M3 契约测试。
3. 部署可识别新表/字段的后端；V1/V2 路由保持。
4. 先开启管理端查询，再开启技师执行和顾客确认流量。
5. 观察 409、幂等冲突、乐观锁冲突、库存不足、事务回滚和 outbox 积压。

## 回滚说明

本迁移不提供自动 `DROP` 回滚。出现异常时：

1. 停止 M3 新写入入口。
2. 回退应用到上一个兼容版本。
3. 保留新表、新列、确认、更正和幂等记录，避免丢失审计事实。
4. 只有在完整备份、依赖扫描、维护窗口和单独审批后，才允许另建收缩迁移。
5. 禁止手工修改 `flyway_schema_history`，禁止直接删除顾客确认或更正历史。

影子库已验证最新版本 `2026072804`、成功标记为 1，并完成故障注入整体回滚。
