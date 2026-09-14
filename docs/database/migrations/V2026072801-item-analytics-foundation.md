# V2026072801 品项经营分析基础迁移说明

当前阶段：M6 经营分析  
任务编号：M6-01  
对应迁移：`V2026072801__item_analytics_foundation.sql`

## 1. 影响范围

本迁移只扩展交易订单行的分析快照和经营分析权限，不修改预约、套餐、库存、支付、退款、提成状态机。

- 表：`sales_order_item`
- 权限：`analytics:view`、`analytics:export`
- 角色：店主、店长、区域经理
- 接口：`/api/v3/analytics/sales/*`
- 历史数据：使用当前项目、商品、品类主数据进行可识别的兼容回填，不伪造成交时快照

## 2. 新增字段

| 字段 | 说明 | 兼容策略 |
|---|---|---|
| `category_id_snapshot` | 成交时品类 ID 快照 | 可空，旧代码不受影响 |
| `category_name_snapshot` | 成交时品类名称快照 | 可空，避免主数据改名影响新订单历史口径 |
| `brand_name_snapshot` | 成交时品牌快照 | 可空，仅商品订单行使用 |
| `dimension_snapshot_quality` | 快照质量 | `TRANSACTION_TIME`、`CURRENT_MASTER_BACKFILL` 或 `MISSING` |

新版本创建订单行时写入 `TRANSACTION_TIME`。历史订单行只允许标记为
`CURRENT_MASTER_BACKFILL` 或 `MISSING`，前端必须展示该差异。

## 3. 索引说明

| 索引 | 用途 |
|---|---|
| `idx_order_item_category_analytics` | 按租户、品类、订单汇总经营结构 |
| `idx_order_item_brand_analytics` | 按租户、品牌、订单汇总经营结构 |

索引不改变唯一性，也不替代订单主表的租户、门店、业务日期过滤。

## 4. 权限说明

- `analytics:view`：敏感级，只读查看；默认授予店主、店长、区域经理。
- `analytics:export`：关键级，预留给受审计导出；默认只授予店主、区域经理。
- 当前 M6-01 未开放导出接口，不能仅凭菜单可见性绕过后端权限。
- 所有查询同时校验租户和授权门店范围。

## 5. 兼容与发布策略

1. 迁移采用 Expand 策略，只增加可兼容字段、约束、索引、权限和授权。
2. 旧版应用不读取新字段，可与新结构短期并行。
3. 新版应用在迁移完成前不得开启经营分析菜单。
4. 历史回填不改订单金额、退款金额、状态或主数据。
5. 品项金额只表示退款前订单行成交额；订单退款未做虚假行级分摊。
6. 发布前必须在隔离 MySQL 8 实例从基线执行全部 Flyway 迁移。

## 6. 验证 SQL

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version = '2026072801';

SELECT column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'sales_order_item'
  AND column_name IN (
    'category_id_snapshot',
    'category_name_snapshot',
    'brand_name_snapshot',
    'dimension_snapshot_quality'
  );

SELECT DISTINCT index_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'sales_order_item'
  AND index_name IN (
    'idx_order_item_category_analytics',
    'idx_order_item_brand_analytics'
  );

SELECT code, risk_level
FROM permission
WHERE code IN ('analytics:view', 'analytics:export');
```

## 7. 回滚说明

优先采用应用回滚：

1. 隐藏经营分析入口并回退应用版本。
2. 保留新增字段、索引和权限，旧版应用不会使用它们。
3. 禁止在营业期间直接删除快照字段或索引。
4. 若必须物理撤销，先完成全库备份并确认没有新版本订单行依赖快照，再在维护窗口用独立的向前迁移执行。

本迁移不删除或覆盖历史订单，因此无需反向转换金额和状态数据。
