# V2026073001 M4 采购与批次库存数据库变更说明

## 变更范围

迁移文件：
`backend-next/src/main/resources/db/migration/V2026073001__m4_purchase_batch_inventory.sql`

本迁移是 expand-only 前向兼容变更，不删除或改名历史表、列和流水。新增采购与批次表，
并把迁移时已有的正库存余额生成期初批次。期初批次只复制当前余额，不再次更新
`stock_balance`，因此不会重复增加账面库存。

## 新增表

| 表 | 归属模块 | 用途 |
|---|---|---|
| `purchase_order` | purchase | 采购单、状态、版本、创建幂等与审批责任人 |
| `purchase_order_item` | purchase | 商品快照、采购数量、累计收货和成本 |
| `purchase_receipt` | purchase | 每次收货、收货幂等与责任人 |
| `purchase_receipt_item` | purchase | 收货明细与库存批次、库存流水的关联 |
| `stock_batch` | inventory | 批次余额、效期、成本和来源 |
| `stock_batch_movement` | inventory | 不可变批次流水 |

## 约束与索引

- 采购单号、收货单号、内部批次号均按租户唯一。
- 采购创建和收货幂等键均按租户唯一。
- `ck_purchase_order_status` 限制六个采购状态。
- `ck_purchase_item_quantity` 防止负数和超收。
- `ck_stock_batch_quantities` 防止负余额、超预留和在手量超过收货量。
- `ck_stock_batch_dates`、`ck_purchase_receipt_item_dates` 防止失效日早于生产日。
- `idx_purchase_order_shop_status` 支持门店、状态和时间列表查询。
- `idx_stock_batch_fefo` 支持地点、商品、状态和效期排序。
- 批次流水业务键唯一，防止同一业务重复入账。
- 外键保留采购、商品、地点、批次、库存流水和责任人的引用完整性。

## 权限数据

新增四项权限：

- `purchase:view`
- `purchase:manage`
- `purchase:approve`
- `purchase:receive`

默认授权遵循最小权限：OWNER/MANAGER 可审批；WAREHOUSE 可维护和收货但不能审批；
FINANCE 只有查看权限。申请人与审批人分离仍由应用层强制校验，不能仅依赖角色配置。

## 期初批次兼容策略

- 只迁移 `stock_balance.quantity_on_hand > 0` 的记录。
- `source_type=MIGRATION_OPENING`，`source_id=stock_balance.id`。
- 内部批次号为 `OPEN-{tenant}-{location}-{product}`。
- 期初批次和期初批次流水一一对应；`NOT EXISTS` 与唯一键共同防止重复。
- 没有效期和成本的历史余额保留为空，不伪造生产日期、失效日期或采购成本。

## 发布与回滚

发布顺序：

1. 备份数据库并校验备份摘要。
2. 在影子 MySQL 8 从空库运行全部 Flyway 迁移。
3. 校验六张表、四项权限、期初批次/流水数量一致、无孤儿和无重复。
4. 部署能识别新表和新状态的后端。
5. 验证采购创建、职责分离、部分/全部收货、超收回滚和故障注入回滚。
6. 最后开放管理端“采购与批次”入口。

本迁移不提供自动 `DROP` 回滚。异常时应关闭采购写入口，回退到忽略新增表的兼容应用，
保留所有采购、收货和批次事实。只有完成全量备份、依赖扫描、维护窗口和独立审批后，
才能另建收缩迁移；禁止删除已入账批次或手工改写库存余额。
