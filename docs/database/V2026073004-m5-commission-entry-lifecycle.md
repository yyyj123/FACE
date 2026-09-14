# V2026073004 M5 提成流水生命周期迁移说明

## 1. 变更范围

迁移文件：`backend-next/src/main/resources/db/migration/V2026073004__m5_commission_entry_lifecycle.sql`

本迁移采用 Expand 策略，只新增提成流水、状态历史、事件投影检查点和冻结权限，不修改订单、支付、退款、服务记录、套餐、库存或既有财务流水。

### 新增表

- `commission_entry`：保存不可变入账/冲正金额、规则版本、来源快照、员工、原流水、退款引用、状态和乐观锁版本。
- `commission_entry_history`：追加记录入账、冲正、冻结和解冻事实，不覆盖历史。
- `commission_event_projection`：保存 outbox 消费检查点、处理状态、重试次数、结果码和安全错误码。

### 新增权限

- `commission:freeze`
- 授予 `OWNER`、`MANAGER`、`FINANCE`。
- `BEAUTICIAN` 不获得冻结权限，只可在 `commission:entry:view` 下按 SELF 范围查看本人流水和来源快照。

## 2. 索引与约束

- `uk_commission_entry_source_key(tenant_id, source_entry_key)`：同一来源/规则/员工入账以及同一退款冲正唯一。
- `uk_commission_entry_history_idempotency(tenant_id, idempotency_key)`：状态历史关键写幂等。
- `uk_commission_projection_outbox(tenant_id, outbox_event_id)`：同一 outbox 事件只保留一个投影检查点。
- `ck_commission_entry_direction`：入账金额为正、冲正金额为负。
- `ck_commission_entry_link`：冲正必须同时引用原流水与成功退款，普通入账不得伪造引用。
- 按门店、员工、状态、发生时间建立流水查询索引；按状态、下次重试时间建立投影扫描索引。
- 外键不使用级联删除，避免历史提成、退款和快照证据被物理移除。

## 3. 事件与事务边界

- 服务、支付、退款模块继续只写各自私有表并发布 `ServiceRecordCompleted`、`PaymentSucceeded`、`RefundCompleted`。
- 提成模块通过 outbox 投影应用服务消费事件，不允许其他模块直接写提成私有表。
- 支付和服务完成无论先后，最终都进入同一受控入账函数；业务唯一键保证只入账一次。
- 部分退款按“退款金额 / 原实付金额”生成负向冲正，金额使用两位十进制确定性舍入；最终退款使用剩余可冲金额消除尾差。
- 原提成金额永不更新；历史复算只比较规则版本、来源快照和结果，不覆盖原流水。

## 4. 历史数据兼容

- 迁移不猜测、不回填迁移前历史提成。
- 对迁移时已存在的相关 outbox 事件建立 `MIGRATION_BASELINE_SKIPPED` 检查点，防止部署后把旧支付、旧服务或旧退款误当成新业务重复计提。
- 迁移后新事件正常投影；不存在已发布匹配规则时记录 `NO_RULE`，不伪造入账。
- `/api/v1`、`/api/v2` 和既有表结构保持不变；M5 新能力使用 `/api/v3/commission/*`。

## 5. 回滚说明

1. 先停止提成投影调度和 M5-03 写接口，应用回滚到 M5-02。
2. 三张新表保留只读，不直接 DROP；已生成入账或冲正时禁止删除原流水。
3. 业务纠错必须追加反向流水，不能通过 UPDATE 金额或删除历史完成。
4. 若必须物理清理，先导出三张新表、outbox、审计日志和 Flyway 历史，并确认不存在 M5-04 结算引用后，由 DBA 在独立维护窗口执行。
5. 不修改 `flyway_schema_history` 来伪造回滚。

## 6. 验证结果

- MySQL 8 全新实例从基线成功执行 20 个迁移，最新版本 `2026073004`。
- 三张新表、一个新增权限和五个关键唯一/检查约束均存在。
- 全链路产生提成 `29.80`，对 `67.00` 部分退款产生 `-7.45` 冲正。
- 同一退款事件重放未重复冲正；冻结/解冻、版本、历史、审计和 SELF 权限均通过。

