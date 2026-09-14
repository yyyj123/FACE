# V2026072603 商用升级基础迁移说明

## 影响范围

- 新增 Flyway `beforeMigrate__legacy_member_compatibility.sql` 回调：仅在缺少旧表时创建空的 `chezhu`、`fuwuyuyue`、`peijianxinxi`、`weixiujilu`、`peijianchuku` 兼容表，使全新安装能够执行历史回填迁移；已有旧库为 no-op，历史迁移 checksum 不变。
- 新增 `data_access_log`：记录敏感数据查看、导出、下载和打印的主体、资源标识、结果与 request_id；不保存手机号、证件、健康信息、支付凭据或业务正文。
- 新增 `outbox_event`：为跨模块领域事件提供事务发件箱基础。当前迁移只提供受控存储，不代表事件发布器已经完成。
- 新增 `idx_account_shop_role_permission_lookup`：支撑“权限点与门店范围来自同一有效授权”的高频查询。
- 不修改、不重命名、不删除现有表或列；不回填伪造业务数据。

## 索引说明

| 索引 | 用途 |
|---|---|
| `idx_data_access_account_time` | 按租户、账号和时间审计敏感访问 |
| `idx_data_access_resource_time` | 按资源回放访问历史 |
| `idx_data_access_request` | 用 request_id 关联 API 日志 |
| `uk_outbox_event_id` | 防止同一领域事件重复入箱 |
| `idx_outbox_dispatch` | 发布器按状态和可用时间领取事件 |
| `idx_outbox_aggregate` | 按聚合回放事件 |
| `idx_account_shop_role_permission_lookup` | 权限校验按账号、租户、状态、角色和有效期检索 |

## 兼容策略

1. 迁移为纯 Expand，现有 `/api/v1`、`/api/v2` 和旧应用不会访问新增表。
2. 兼容回调只负责补齐历史迁移的输入表，不写入伪造会员、预约、库存或服务数据；全新安装时五张表保持为空。
3. 已存在任一完整遗留表时，`CREATE TABLE IF NOT EXISTS` 不改变其定义和数据。
4. 新代码在迁移成功前不得开启访问审计或 outbox 新写入开关。
5. `outbox_event.payload` 只能保存消费者必需的最小业务字段；禁止保存客户敏感正文和支付凭据。
6. Flyway checksum 不得手工修改；发现漂移时停止发布，并在影子库重新验证。

## 发布验证

```sql
SELECT VERSION(), DATABASE(), @@session.time_zone;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'data_access_log',
    'outbox_event',
    'chezhu',
    'fuwuyuyue',
    'peijianxinxi',
    'weixiujilu',
    'peijianchuku'
  );

SELECT index_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'account_shop_role'
  AND index_name = 'idx_account_shop_role_permission_lookup';

SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;
```

通过条件：两个新增基础表、五张迁移兼容表和权限索引存在；最新 Flyway 记录 `success = 1`；数据库、时区和目标 schema 正确。

## 回滚说明

- 首选回滚：关闭访问审计/outbox 新写入，回退应用版本；新增表与索引保留，不影响旧版本。
- 禁止在业务运行期间直接 DROP 新表或兼容表。若必须物理撤销，先确认表为空或数据已归档，并在独立维护窗口执行。
- 兼容表若承载了旧系统历史数据，只允许备份后保留，不允许作为本次升级回滚的一部分删除。
- 因本迁移无历史数据回填和破坏性变更，不需要数据反向转换。
