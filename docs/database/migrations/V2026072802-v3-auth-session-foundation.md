# V2026072802 V3 会话基础迁移说明

迁移文件：`backend-next/src/main/resources/db/migration/V2026072802__v3_auth_session_foundation.sql`  
类型：Expand-only  
阶段：M1

## 变更

新增 `auth_session`，旧 `token` 表不变。表用于 `/api/v3` 的短期访问令牌、刷新轮换和会话撤销。

关键字段：

- `session_id`：会话 UUID。
- `tenant_id/account_id/home_shop_id`：服务端身份绑定。
- `access_token_hash/refresh_token_hash`：SHA-256 哈希。
- `access_expires_at/refresh_expires_at`：两级过期时间。
- `revoked_at/revoke_reason`：撤销证据。
- `version`：刷新并发乐观条件。
- `last_seen_at/created_at/updated_at`：生命周期审计。

## 索引

| 索引 | 目的 |
|---|---|
| `uk_auth_session_id` | 会话标识唯一 |
| `uk_auth_session_access_hash` | 访问令牌等值鉴权 |
| `uk_auth_session_refresh_hash` | 刷新令牌等值查询与重放拒绝 |
| `idx_auth_session_account_active` | 账号有效会话查询和清理 |
| `idx_auth_session_tenant_created` | 租户审计与生命周期清理 |

## 约束

- 外键绑定 tenant、account、shop。
- 刷新到期必须晚于访问到期。
- 撤销时间和撤销原因必须同时为空或同时存在。
- 数据库不保存原始访问/刷新令牌。

## 兼容

- `/api/v1`、`/api/v2` 继续读写旧 `token`。
- 新代码只在 `/api/v3` 使用 `auth_session`。
- 不回填历史 token，不改变历史迁移 checksum。
- `REGIONAL_MANAGER` 已由 `V2026072401` 创建，本迁移不重复创建。

## 发布与回滚

发布前：

1. 完成 M0 备份和影子库恢复。
2. 在影子 MySQL 8 执行 Flyway migrate/validate。
3. 验证新表、三个唯一索引、两个查询索引和外键。
4. 验证 V3 登录/刷新/退出；V2 登录保持可用。

回滚：

- 先回退应用或关闭 V3 入口。
- 保留新增表和会话历史，不即时 DROP。
- 物理删除必须单独维护窗口、备份、空表/无依赖确认和审批。
