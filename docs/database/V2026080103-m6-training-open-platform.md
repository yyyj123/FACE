# V2026080103 M6 培训与开放平台迁移说明

## 变更

迁移 `V2026080103__m6_training_open_platform.sql` 新增：

- `training_course`：租户/门店内课程编码与修订号唯一，发布版本不可覆盖。
- `training_record`：员工、课程、状态、分数、证书和乐观锁当前态。
- `training_record_history`：只追加的培训动作与幂等事实。
- `integration_client`：绑定门店的客户端、作用域、摘要密钥、限流和版本。
- `integration_client_event`：创建、轮换、撤销事件。
- `integration_request_log`：Nonce、防重放、状态码和调用审计。

新增 7 项权限：`training:view`、`training:manage`、`training:verify`、`training:self`、`integration:view`、`integration:manage`、`integration:rotate`。管理权限授予店主、店长和区域经理，技师仅授予本人培训权限。

## 索引与约束

- 6 个业务唯一键覆盖课程版本、员工课程记录、培训历史幂等键、客户端编码、客户端事件幂等键、客户端 Nonce。
- 状态、分数、验证者/验证时间、证书和有效期由 CHECK 约束保持一致。
- 集成客户端只允许 `catalog:read`；密钥摘要固定 64 位十六进制；限流范围 1–60 次/分钟。
- 外键按租户/门店或实体关系限制；历史与调用日志不提供级联物理删除。

## 兼容与回滚

迁移只新增对象，不改变历史表字段及接口。发布前回滚可按依赖逆序删除 `integration_request_log`、`integration_client_event`、`integration_client`、`training_record_history`、`training_record`、`training_course`，再删除 7 项权限；正式环境一旦产生业务数据，不允许直接 DOWN，必须先导出日志/历史、停用客户端和课程，并由变更审批执行补偿迁移。

验收在全新 MySQL 8 隔离实例上从基线迁移至 `2026080103`，验证结果：6 张表、6 个唯一键、7 项权限、Flyway `SUCCESS=1`。
