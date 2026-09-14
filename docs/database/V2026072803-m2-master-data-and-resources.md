# V2026072803 M2 数据库变更说明

## 变更类型

扩展式、前向兼容 Flyway 迁移：
`backend-next/src/main/resources/db/migration/V2026072803__m2_master_data_and_resources.sql`。

## 表与字段

- `service_item`：增加 `version`、`updated_by`。
- `staff_schedule`：增加 `status`、`version`、`created_by`、`updated_by`。
- `staff_skill_version`：技能有效期、版本、当前版本唯一标记和审计人。
- `service_resource`：房间/设备编码、类型、并行容量、状态、版本和审计人。
- `resource_booking`：预约资源占用、时段、释放原因和释放审计。

迁移从 `staff_service` 回填当前技能版本，但不删除或重命名旧表、旧列和旧数据。

## 索引与约束

- `idx_staff_schedule_active_window`：租户、门店、员工、日期、状态和时段。
- 技能当前版本使用生成列 `current_marker` 和唯一索引，数据库保证同一
  技师/项目最多一个当前版本。
- 资源编码在租户/门店内唯一；类型、状态、容量和版本使用 `CHECK`。
- 资源占用建立预约索引和资源时段索引；状态和时段使用 `CHECK`。
- 新表使用 InnoDB 外键连接租户、门店、员工、项目、预约和账号。

## 发布兼容

1. 先备份并校验 SHA-256。
2. 在影子 MySQL 8 执行全部 Flyway 迁移。
3. 部署可识别新表的后端，旧 V1/V2 保持服务。
4. 开启 V3 基础资料流量，再开启预约资源选择。
5. 观察 409、锁等待、死锁、幂等冲突和审计写入。

## 回滚说明

本迁移不提供自动 `DROP` 回滚。出现异常时停止 V3 新写入、回退应用并保留
新表和历史记录。物理删除或字段回退必须在完整备份、依赖扫描、维护窗口和
单独审批后另建迁移，禁止手工修改 `flyway_schema_history`。
