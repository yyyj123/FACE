# V2026080101 M6 经营报表快照迁移说明

## 1. 变更范围

迁移文件：`backend-next/src/main/resources/db/migration/V2026080101__m6_report_snapshot.sql`

采用 expand-only 策略新增 `report_snapshot`，保存经营分析概览的不可变 CSV 快照、授权门店范围、筛选条件、指标版本、请求哈希、内容哈希、大小、行数和保留期限。

不修改 `sales_order`、`sales_order_item`、退款、会员、套餐、库存、支付或提成表，不回填历史报表，不改变任何既有状态机。

## 2. 索引与约束

- `uk_report_snapshot_idempotency (tenant_id, requested_by_account_id, idempotency_key)`：同一账号的同一幂等键最多生成一份快照；
- `idx_report_snapshot_requester (tenant_id, requested_by_account_id, created_at, id)`：支撑本人报表倒序分页；
- `idx_report_snapshot_expiry (status, expires_at, id)`：支撑过期扫描和后续受控归档；
- 外键连接 `tenant` 和创建账号 `account`；
- CHECK 约束限制报表类型、CSV 格式、`READY/EXPIRED` 状态、品项类型、64 位十六进制请求/内容哈希；
- `ck_report_snapshot_ready_content` 强制内容非空、字节数等于 BLOB 实际长度、行数大于零且过期时间晚于就绪时间。

数据库唯一键是并发下的最终幂等保护。应用层同时比较稳定 `request_hash`：同键同载荷返回原快照，同键异载荷返回 HTTP 409。

## 3. 模块与事务边界

- 分析模块通过 `TransactionAnalyticsQueryPort` 请求交易模块生成经营事实，不直接访问订单私有表；
- 生成 CSV、插入快照和写入 `ANALYTICS_REPORT_CREATED` 审计处于同一事务；任何一步失败都会回滚，不保留伪造的 `READY`；
- 下载前重新校验当前 `analytics:export` 门店范围，并写入 `ANALYTICS_REPORT_DOWNLOADED` 审计；
- 列表、详情和下载 SQL 都强制 `tenant_id + requested_by_account_id`，范围外对象返回不存在，避免枚举；
- CSV 固化 `M6-04-v1` 指标版本，退款保持 `ORDER_LEVEL_ONLY`，不伪造品项退款、利润或外部市场份额；
- 文本单元格以 `= + - @` 开头时加前导单引号，避免 WPS/Excel 公式注入。

## 4. 兼容策略

- `/api/v1`、`/api/v2` 和已有经营分析查询接口不变；
- 复用已存在的 `analytics:export`，不扩大 MANAGER、FRONT_DESK 或 BEAUTICIAN 的默认权限；
- 普通 JSON 响应只返回元数据，不返回 BLOB 内容；
- 过期状态由查询时的 `expires_at` 派生，当前版本不批量改写历史快照；
- CSV 不包含会员姓名、手机号、证件、健康信息、支付凭据或密码字段。

## 5. 回滚说明

- 首选应用回退：下线 `/api/v3/analytics/reports` 路由和管理端报表页签，新表保留只读；
- 不删除已生成快照和审计记录，不修改 Flyway 历史；
- 若必须物理回滚，只能在确认没有应用实例访问新表、完成合规留存评估和独立备份后，于维护窗口执行单独反向 DDL；
- 回滚不会触碰订单、退款、库存、套餐、支付或提成数据。

## 6. 验证结果

- MySQL 8 全新隔离实例成功执行 24 个迁移，最新版本 `2026080101`；
- 新表 1 张、关键索引 3 个、关键 CHECK 约束 3 个；
- 两账号各生成一份快照，重复键记录 0；
- 同键重放、异参冲突、本人隔离、下载 SHA-256、UTF-8 BOM、敏感数据守卫、创建/下载审计均通过；
- 隔离数据库、临时后端和临时测试会话在验收后已清理。
