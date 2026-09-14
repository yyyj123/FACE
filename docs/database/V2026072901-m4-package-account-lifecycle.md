# V2026072901 M4 套餐与会员账户数据库变更说明

## 变更范围

迁移文件：
`backend-next/src/main/resources/db/migration/V2026072901__m4_package_account_lifecycle.sql`

本迁移是 expand-only 前向兼容迁移，不删除、不重命名旧表、旧列或历史数据。
V1/V2 代码可忽略新增表和可空列，M4 新写入统一通过领域应用服务完成。

## 新增表

- `package_product`：套餐产品主数据、售价、有效期、状态和乐观版本。
- `package_product_item`：套餐包含的护理项目及可核销次数。
- `package_instance`：由已支付订单发放给会员的套餐实例、有效期、总余额、
  剩余余额、状态和版本。
- `package_instance_item`：套餐实例按护理项目保存原始次数和剩余次数。
- `package_ledger`：发放、核销、冲正、冻结与解冻的不可变套餐流水。

## 扩展旧表

- `sales_order_item.package_product_id`：允许订单明细引用套餐产品。
- `sales_order_item.item_type` 增加 `PACKAGE`，保留原有 `SERVICE`、`PRODUCT`。
- `member_account_ledger.request_hash`：保存关键写请求摘要，用于同键异载荷检测。
- `member_account_ledger.reversal_of_ledger_id`：冲正流水指向原流水；原流水不修改。

新增列保持可空以兼容历史数据；M4 新写入路径强制提供请求摘要和业务关联。

## 约束与索引

- 套餐产品编号、实例编号、发放幂等键均按租户唯一。
- `uk_package_instance_order_product` 防止同一已支付订单的同一套餐重复发放。
- `uk_package_ledger_idempotency`、`uk_package_ledger_business` 防止重复核销。
- `uk_package_ledger_reversal` 防止同一套餐流水重复冲正。
- `uk_member_ledger_reversal` 防止同一账户流水重复冲正。
- 会员/状态、门店/状态、实例/时间、服务记录、账户流水均建立查询索引。
- 数量、余额、日期和状态使用数据库 `CHECK`；关联租户、门店、会员、订单、
  项目、服务记录和操作人使用 InnoDB 外键。

## 权限数据

新增六项权限：

- `package:view`
- `package:manage`
- `package:writeoff`
- `package:reverse`
- `account:view`
- `account:manage`

角色授权采用不存在时插入，不覆盖既有授权。技师仅获得套餐查看和核销权限，
会员仅获得本人套餐/账户查看范围；管理、冲正属于高风险权限。

## 发布与兼容策略

1. 先完成数据库备份并校验 SHA-256。
2. 在影子 MySQL 8 运行全部 Flyway 迁移、契约测试和故障注入。
3. 部署可识别新表/列的后端，继续保留 V1/V2 路由。
4. 先开放查询，再按管理端发放、技师核销、会员查询顺序放量。
5. 监控幂等冲突、乐观锁冲突、余额不足、过期套餐、跨门店拒绝和 outbox 积压。

## 回滚说明

本迁移不提供自动 `DROP` 回滚。异常时：

1. 停止套餐发放、核销、冲正和账户人工写入。
2. 回退应用到上一个兼容版本；新增表和可空列继续保留。
3. 不删除套餐或账户流水，不手工改余额，不修改 `flyway_schema_history`。
4. 使用订单、套餐实例、账户流水、审计和 outbox 做只读核对。
5. 只有在完整备份、依赖扫描、维护窗口和独立审批后，才可另建收缩迁移。

影子库已验证最新版本 `2026072901`、成功标记为 1，并验证失败注入下无套餐实例、
流水、余额或 outbox 残留。
