# M6-05 经营报表快照与受审计导出：影响与迁移方案

## 1. 当前阶段与任务

- 当前阶段：M6 商用运营
- 已完成前置：M6-01 品项分析、M6-02 品牌分析、M6-03 门店对比、M6-04 同期对比；M5 全阶段已验收
- 当前任务：M6-05 经营报表快照与受审计导出
- 产品结果：店主或区域负责人能够把当前授权范围与统计口径固化成不可变 CSV 报表，并在本人报表历史中审计下载

## 2. 输入、输出与验收标准

输入：

- 四份 V1.2 商业基线及 M5-07 验收记录
- M6-01 至 M6-04 的 `/api/v3/analytics/sales/overview`、`analytics:view`、`analytics:export` 和 `M6-04-v1` 指标口径
- 当前 `backend-next`、`admin-next`、MySQL 8 Flyway 迁移与白色主题设计规范

输出：

- `report_snapshot` 不可变报表快照表
- 报表创建、本人历史、详情和下载接口
- 管理端经营分析中的“报表快照”任务视图
- 数据库、权限、幂等、CSV 安全、审计、构建和实机验收证据

验收标准：

- 只有具备 `analytics:export` 的账号可创建和下载报表；浏览权限不能替代导出权限
- 报表范围必须是当前账号拥有导出权限的门店；跨租户、跨账号、跨门店访问返回 403/404
- `Idempotency-Key` 与请求摘要共同保证同请求只生成一个快照；同键不同请求返回 409
- 报表内容由已验收经营分析口径生成，固化 `metricVersion`、范围、时间、品项类型、生成时刻和 SHA-256
- 快照创建后不可覆盖；下载只追加审计，不修改内容、摘要或查询范围
- CSV 不包含手机号、证件、健康信息、支付凭据或完整售后正文，并防止表格公式注入
- 旧 `/api/v1`、`/api/v2` 与现有 M6-01 至 M6-04 响应保持兼容

## 3. 影响模块、表、接口、权限与状态

| 类型 | 影响 |
| --- | --- |
| 模块 | analytics 新增报表应用服务；通过 `TransactionAnalyticsQueryPort` 获取交易模块授权聚合结果，不直接访问交易私有表 |
| 表 | 新增 `report_snapshot`；复用 `audit_log`，不修改历史业务表 |
| 接口 | `POST /api/v3/analytics/reports`、`GET /api/v3/analytics/reports`、`GET /api/v3/analytics/reports/{reportId}`、`GET /api/v3/analytics/reports/{reportId}/download` |
| 权限 | 复用已存在的 `analytics:export`；不新增默认技师、会员或前台授权 |
| 状态 | `READY`、`EXPIRED`；M6-05 同步生成，失败事务整体回滚，不制造伪 `READY` |
| 幂等 | 表内原生唯一约束：租户、申请账号、幂等键；请求摘要冲突返回 409 |
| 版本 | 快照 `version=0`，内容与查询参数创建后不可修改；指标口径沿用 `M6-04-v1` |
| 审计 | 创建和下载写入 `audit_log`；只记录报表 ID、范围摘要、内容摘要与字节数，不记录报表正文 |

## 4. 数据库迁移、索引与约束

迁移：`V2026080101__m6_report_snapshot.sql`

新增结构：

- 主键、租户、申请账号、报告类型、格式、状态和版本
- 门店范围 JSON 快照、日期范围、品项类型、指标版本、生成时间与过期时间
- 幂等键、请求摘要、内容 SHA-256、内容字节数、行数和 CSV 内容
- 外键连接租户与申请账号；不连接交易模块私有表

索引与约束：

- 唯一键 `uk_report_snapshot_idempotency(tenant_id, requested_by_account_id, idempotency_key)`
- 本人历史索引 `idx_report_snapshot_requester(tenant_id, requested_by_account_id, created_at, id)`
- 过期维护索引 `idx_report_snapshot_expiry(status, expires_at, id)`
- 状态、格式、品项类型、摘要长度、内容字节数与就绪字段一致性使用 CHECK 约束

## 5. 兼容、回滚与历史数据

- Expand-only：只新增表，不改名、不删列、不回填历史业务数据
- 旧应用忽略新表；现有实时经营分析接口和页面默认视图不变
- 应用回滚时关闭报表路由和页面入口，保留 `report_snapshot` 与审计记录只读
- 默认生产策略不执行物理反向迁移；确需 DROP 前必须确认无报表数据、完成独立备份和审批
- 快照到期只改变可下载性；M6-05 不提供删除历史快照接口

## 6. 测试先行清单

先新增失败测试，再实现：

1. 迁移必须包含表、唯一键、本人索引、状态约束，且不得包含 DROP/DELETE
2. 报表策略拒绝无效日期、超过 93 天、非法品项类型和非法格式
3. CSV 对以 `= + - @` 开头的快照文本进行公式注入防护
4. 创建报表必须校验 `analytics:export` 门店范围，不能复用 `analytics:view` 代替
5. 同幂等键同摘要返回同一快照；同键不同摘要返回 409
6. 详情、列表和下载只返回本人且处于当前租户的快照
7. 下载前重新验证报表门店范围仍在当前导出权限内，并追加审计
8. 响应与日志不得包含受保护的客户或支付敏感信息

## 7. 实现后验证命令

- `mvn.cmd -q test`
- `mvn.cmd -q -DskipTests package`
- `npm.cmd run build`（admin-next）
- `npm.cmd run build`（front-next）
- 全新 MySQL 8 实例执行完整 Flyway 迁移并校验 `LATEST=2026080101;SUCCESS=1`
- 真实 API 验证：授权创建、幂等重放、冲突、本人列表、下载、跨账号/技师 403
- 管理端桌面与窄屏浏览器验收：加载、空态、成功、错误、下载、无横向溢出和无控制台异常

## 8. 文档同步

- 新增 M6-05 数据库迁移说明与验证记录
- 更新 M6 经营分析接口契约和 `PRODUCT.md` 阶段状态
- M6 总阶段完成后再使用 WPS 同步四份商业文档；本小阶段不提前把营销、培训、开放平台、性能或灾备标记为完成
