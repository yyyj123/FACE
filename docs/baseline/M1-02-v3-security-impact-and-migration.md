# M1-02 V3 安全闭环影响与迁移方案

状态：已实施并通过验收  
基线：商业文档 V1.1（2026-07-26）  
前置阶段：M0 已验收  
任务编号：M1-02

## 1. 输入、输出与验收

输入：

- 《美容PRD_V1.1》
- 《美容数据库V1.1》
- 《美容院店铺管理系统 API接口清单V1.1》
- 《美容院店铺管理系统 分阶段开发实施与验收V1.1》
- `PRODUCT.md`、`DESIGN.md`
- `backend-next` 现有 `/api/v2`、Flyway、权限与健康检查代码

输出：

- `/api/v3` 统一响应、稳定错误码、`request_id` 与时间戳
- 短期访问令牌、可轮换刷新令牌、可撤销会话
- 登录失败数据库限流与锁定
- `/api/v3/auth/login|refresh|logout`
- `/api/v3/me/context`、`/api/v3/shops`、`/api/v3/health/readiness`
- V3 权限范围与数据访问审计
- Flyway Expand 迁移、索引说明、兼容策略、回滚说明

验收：

- V3 契约、会话轮换/撤销、跨范围拒绝和 readiness 测试通过
- `/api/v1`、`/api/v2` 行为和旧 `token` 表不被改变
- 全新 MySQL 8 影子库完成 Flyway migrate/validate
- V3 登录、刷新、旧刷新令牌重放失败、退出后访问失败
- 后端测试/构建与两端前端生产构建通过
- 无真实客户数据、明文访问/刷新令牌、密码或支付凭据进入数据库及日志

## 2. 影响模块

| 模块 | 影响 | 边界 |
|---|---|---|
| security | V3 令牌解析、会话撤销、权限上下文、请求 ID | 只从服务端会话解析 tenant/account/roles |
| v3/auth | 登录、刷新轮换、退出 | 不直接访问其他模块私有表；经仓储和权限服务 |
| v3/platform | 当前上下文、可访问门店、readiness | 复用 `TenantAccessService` 与数据库健康组件 |
| audit | 上下文/授权元数据访问日志 | 仅保存标识和结果，不保存敏感正文 |
| database | 新增 V3 会话表及索引 | Expand-only，不修改旧表数据和历史迁移 |
| api | V3 响应与错误契约 | v1/v2 响应保持原样 |

## 3. 表结构、索引与兼容策略

新增 `auth_session`：

- `session_id`：不可猜测的会话标识。
- `access_token_hash`、`refresh_token_hash`：仅保存 SHA-256 哈希，不保存原始令牌。
- `tenant_id`、`account_id`、`home_shop_id`：服务端身份绑定。
- `access_expires_at`、`refresh_expires_at`、`revoked_at`：过期与撤销。
- `version`：刷新令牌轮换使用乐观并发控制。
- `last_seen_at`、创建/更新时间：会话审计。

索引：

- 访问令牌哈希唯一索引：鉴权等值查询。
- 刷新令牌哈希唯一索引：刷新等值查询与重放拒绝。
- `account_id, revoked_at, refresh_expires_at`：账号会话管理。
- `tenant_id, created_at`：租户审计与清理。

兼容：

- 旧 `/api/v2` 继续使用原 `token` 表；不改列、不回填、不旋转旧 token。
- V3 只读取 `auth_session`，V2 只读取 `token`，避免一张表同时承担两种生命周期。
- `REGIONAL_MANAGER` 已存在于 `V2026072401`，本任务只做回归验证，不重复插入角色。
- `failed_login_count` 与 `locked_until` 已存在于 `account`，直接使用数据库原生原子更新，不新增重复限流表。

## 4. 状态、权限与接口契约

- 会话状态由 `revoked_at`、访问过期时间、刷新过期时间共同决定。
- 刷新成功必须在同一事务内替换访问/刷新令牌哈希并增加 `version`。
- 旧刷新令牌在成功轮换后立即失效；并发刷新最多一个成功。
- 退出只撤销当前会话，不删除历史记录。
- 登录连续失败达到阈值后设置 `locked_until`；成功登录清零失败计数。
- `tenant_id`、账号、角色、区域和门店范围只由服务端解析。
- `X-Shop-Id` 只能作为候选上下文，使用前仍需校验授权范围。
- V3 失败响应使用 HTTP 类别 + 稳定业务码；不返回堆栈和内部 SQL。

## 5. 测试先行

实现前先添加并运行失败测试：

- 响应必须包含稳定 `code/message/data/request_id/timestamp`。
- 请求 ID 只接受安全格式；缺失或非法时生成新 ID。
- 原始令牌与数据库哈希不同，哈希稳定。
- V3 访问令牌仅能解析未撤销且未过期的会话。
- 刷新令牌轮换后旧令牌重放失败；并发刷新最多一个成功。
- 退出后当前访问令牌失效。
- 登录失败计数和临时锁定生效。
- 门店/SELF 权限继续由同一有效授权解析。
- readiness 在表、迁移或索引不完整时返回不可就绪。

## 6. 回滚方案

1. 关闭或回退包含 `/api/v3` 路由的应用版本；V1/V2 继续服务。
2. 保留 `auth_session` 和其历史记录，停止新写入，不执行即时 DROP。
3. 若需要物理删除，只能在独立维护窗口、确认无 V3 会话依赖并完成备份后执行单独审批脚本。
4. Flyway 失败时停止发布，不修改 checksum，不手工标记成功；从 M0 备份重建影子库后复验。
5. 登录异常时关闭 V3 登录入口，旧 V2 会话不受影响。

## 7. 开工检查表

1. 当前阶段/任务：M1 / M1-02。
2. 输入、输出和验收：见第 1 节。
3. 模块、表、接口、权限和状态：见第 2–4 节。
4. 历史数据：仅 Expand；不改旧 token 和业务历史数据。
5. 先新增测试：见第 5 节。
6. 验证：Maven、两端构建、Flyway migrate/validate、影子库接口演练、readiness。
7. 文档：同步 API、数据库迁移、运行手册与 M1 验收记录。
