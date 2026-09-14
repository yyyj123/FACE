# M6-01 经营分析重构验证记录

验证日期：2026-07-28  
当前阶段：M6 经营分析  
任务编号：M6-01

## 1. 输入、输出与验收标准

输入基线：

- 《美容PRD_V1.1》
- 《美容数据库V1.1》
- 《美容院店铺管理系统 API接口清单V1.1》
- 《美容院店铺管理系统 分阶段开发实施与验收V1.1》
- `backend-next`、`admin-next`、`front-next` 现有代码及迁移

输出：

- `/api/v3/analytics/sales/overview`
- `/api/v3/analytics/sales/lines`
- 管理端经营分析总览与销售明细
- 订单行品类、品牌维度快照
- `analytics:view`、`analytics:export` 权限模型
- 数据库迁移、接口口径、兼容与回滚文档

验收标准：

- 后端先有日期规则、权限和查询边界测试，再实现功能。
- 经营分析只能读取当前租户和授权门店。
- 技师角色不能访问经营分析接口。
- 订单级退款不伪造为订单行退款。
- 历史维度回填与成交时快照必须明确区分。
- 隔离 MySQL 8 全量迁移、应用连接、双前端生产构建均通过。

## 2. 影响模块、表、接口、权限与状态

| 类型 | 影响 |
|---|---|
| 模块 | 分析、交易、权限、管理端 |
| 表 | `sales_order_item` 扩展四个可兼容快照字段 |
| 接口 | 新增 `/api/v3/analytics/sales/*`；`/api/v2/context` 兼容增加权限集合 |
| 权限 | 新增 `analytics:view`、`analytics:export` |
| 状态 | 不修改预约、订单、退款、库存、套餐或提成状态机 |
| 历史数据 | 只做带质量标识的主数据兼容回填，不修改金额和状态 |

分析模块通过查询端口调用交易模块应用服务，不直接读取交易模块私有表。

## 3. 测试驱动记录

1. 先新增 `AnalyticsQueryPolicyTest`，首次编译因实现类不存在而失败，再实现日期默认值、倒序校验、93 日上限和品项类型白名单。
2. 先新增 `TransactionAnalyticsQueryServiceTest`，覆盖租户/门店范围和指标口径，再实现交易模块查询服务。
3. 真实 API 验证发现无权限账号因空门店范围返回 200 空结果；先修改测试要求执行权限校验并确认失败，再修正实现，最终无权限账号返回 403。
4. 扩展 `TenantAccessServiceTest`，验证上下文权限集合来自有效角色授权。

## 4. 数据库迁移与应用连接

验证环境：

- 隔离 MySQL 8 临时实例
- 临时端口 `3319`
- 后端临时端口 `8191`
- 管理端临时端口 `8081`
- 未访问或修改现有开发数据库 `127.0.0.1:3308`

结果：

```text
LATEST=2026072801;SUCCESS=1
TABLES=7/7
INDEX=8
ANALYTICS_COLUMNS=4/4
ANALYTICS_INDEXES=2/2
ANALYTICS_PERMISSIONS=2/2
READINESS={"status":"UP"}
ANALYTICS_OWNER_API=PASS
ANALYTICS_BEAUTICIAN_403=PASS
TEMP_MIGRATION_VERIFICATION=PASS
```

说明：

- 最新 Flyway 迁移成功。
- 四个快照字段、两个分析索引和两个权限点均存在。
- 店主账号能够查询 V3 经营分析接口。
- 技师账号访问同一接口返回 403。
- 临时验证服务和数据库已停止，端口 `3319`、`8191`、`8081` 均已释放。

## 5. 全量测试与构建

### 后端

命令：

```powershell
mvn.cmd clean verify
```

结果：

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

已生成：

`backend-next/target/face-chain-platform-0.1.0-SNAPSHOT.jar`

测试日志存在 Mockito 动态加载 Java Agent 的未来兼容警告，以及一个既有测试的未检查操作编译提示；本次测试和打包未失败。

### 管理端

命令：

```powershell
npm.cmd run build
```

结果：TypeScript 校验与 Vite 生产构建通过，生成独立的 `AnalyticsView` 资源。

日志包含旧 npm 配置 `sass_binary_site` 和第三方 `@vueuse/core` PURE 注释位置警告，不影响构建结果，后续应纳入依赖治理。

### 顾客端

命令：

```powershell
npm.cmd run build
```

结果：TypeScript 校验与 Vite 生产构建通过。

### HTTP 路由

```text
ADMIN_HOME_STATUS=200
ADMIN_ANALYTICS_ROUTE_STATUS=200
ANALYTICS_ROUTE_SHELL=True
```

应用内浏览器自动视觉检查需要的运行工具在当前会话未提供，因此没有伪造视觉验收结论。生产构建、真实 HTTP 路由和真实 API 已验证；发布前仍建议由验收人员在目标浏览器检查桌面和窄屏布局。

## 6. 每步自检

| 检查项 | 结果 |
|---|---|
| 需求覆盖 | 完成 M6-01 可靠 P0 经营分析；未冒充完成套餐、提成、利润或外部市占 |
| 权限 | 菜单、路由、接口同时约束；后端为最终授权边界 |
| 错误处理 | 非法日期、范围、品项类型和门店越权均拒绝 |
| 日志与敏感数据 | 接口和测试不返回/输出手机号、证件、健康信息或支付凭据 |
| 测试 | 后端 22 项测试通过，权限缺陷有回归覆盖 |
| 数据库 | Expand 迁移、索引、兼容、质量标识和回滚说明完整 |
| 文档同步 | 已同步 API 口径、迁移说明和本验证记录 |
| 提交 | 当前 `E:\face` 不是 Git 仓库，未擅自初始化，无法执行小步提交 |

## 7. 发布边界

本记录证明代码、隔离迁移、应用连接、接口、权限和构建通过，不等于已经迁移生产数据库。
真实数据库发布仍需备份、批准的维护窗口、数据库凭据和发布后核验；不得复用临时验证数据或在日志中输出真实客户敏感信息。
