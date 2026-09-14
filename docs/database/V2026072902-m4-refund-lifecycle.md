# V2026072902 M4 退款生命周期数据库变更说明

## 变更范围

迁移文件：
`backend-next/src/main/resources/db/migration/V2026072902__m4_refund_lifecycle.sql`

迁移为 expand-only 前向兼容变更：不删除退款、支付、订单、套餐或账户历史数据，
不重命名旧列。旧代码可忽略新增可空列；M4 新写入统一通过退款应用服务完成。

## 新增列

`refund_transaction` 新增：

- `request_hash`：申请请求 SHA-256 摘要。
- `execution_idempotency_key`、`execution_request_hash`：执行阶段独立幂等信息。
- `execution_mode`：`LOCAL_LEDGER` 或 `EXTERNAL_ADAPTER`。
- `external_refund_no`、`channel_status`：外部通道结果；未执行时保持空值。
- `failure_code`、`failed_at`：执行失败证据。
- `executed_by`：执行责任人，外键指向 `account`。

新增列均允许空值，以兼容历史退款。M4 新申请/执行路径强制写请求摘要。

## 状态、约束与索引

- `ck_refund_status` 扩展为
  `PENDING/APPROVED/PROCESSING/SUCCESS/REJECTED/FAILED`。
- `ck_refund_execution_mode` 限制本地账务或外部适配器两种执行模式。
- `uk_refund_execution_idempotency` 按租户防止重复执行。
- `uk_refund_external_no` 按租户防止外部退款号重复。
- `idx_refund_payment_status` 支持按支付、状态、时间核算可退金额。
- `fk_refund_executor` 保留执行责任人引用完整性。

## 权限数据

新增：

- `refund:request`：OWNER、MANAGER、FRONT_DESK。
- `refund:execute`：OWNER、MANAGER、FINANCE。

既有 `refund:approve` 继续独立授权。授权使用不存在时插入，不覆盖门店已有角色配置；
申请、审批、执行不能因角色重叠而绕过申请人不得自审规则。

## 兼容、发布与回滚

1. 备份数据库并校验摘要。
2. 在影子 MySQL 8 运行 Flyway、全量测试和退款故障路径。
3. 先部署可识别新状态/列的后端，再开放管理端和会员端入口。
4. 监控幂等冲突、版本冲突、503 外部依赖、`APPROVED` 积压和 outbox。

本迁移不提供自动 `DROP` 回滚。异常时停止退款申请/审批/执行，回退到可忽略新增列的
兼容应用版本并保留所有退款记录；不得删除 `PROCESSING/FAILED` 记录、不得手改支付累计
退款或套餐/账户余额。只有在完整备份、依赖扫描、维护窗口和独立审批后，才可另建收缩
迁移。

隔离数据库已验证 Flyway 最新版本 `2026072902`、成功标记为 1；退款新增 9 列、
2 项权限、状态约束和索引均存在。

