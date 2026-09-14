# M6-02 品牌经营分析验证记录

验证日期：2026-07-28  
当前阶段：M6 商用运营  
任务编号：M6-02

## 1. 输入、输出和验收标准

输入：

- 四份 V1.1 商用升级基线
- M6-01 经营分析接口、快照字段和权限模型
- `PRODUCT.md`、`DESIGN.md` 与现有白色主题组件规范

输出：

- `/api/v3/analytics/sales/overview` 兼容增加 `brandComposition`
- 管理端经营分析增加“品牌分析”视图
- 品牌金额、订单、客户、数量及店内销售构成
- 数据质量与非外部市场份额说明

验收标准：

- 只统计有效订单中的零售商品。
- 品牌来自订单行成交维度快照，空品牌独立列示。
- 订单退款不分摊到品牌。
- 沿用 `analytics:view`、租户和门店范围。
- 技师访问仍返回 403。
- 后端测试、隔离 MySQL/API、管理端和顾客端生产构建通过。

## 2. 影响与兼容

| 项目 | 处理 |
|---|---|
| 状态机 | 不修改 |
| 数据库 | 复用 `brand_name_snapshot`，无新迁移 |
| API | 现有 overview 响应增加字段，向后兼容 |
| 权限 | 沿用 `analytics:view`，不开放导出 |
| 历史数据 | 保留 `CURRENT_MASTER_BACKFILL` / `MISSING` 质量标识 |
| 模块边界 | 分析控制器通过交易查询端口调用交易模块 |

M6 完整范围依赖 M5。本任务不实现套餐、项目结余、利润、提成、营销、培训或开放平台，
也不把这些能力标记为完成。

## 3. 测试驱动记录

先修改 `TransactionAnalyticsQueryServiceTest`，要求响应包含
`brandComposition` 且指标版本为 `M6-02-v1`。首次执行失败，实际版本仍为
`M6-01-v1`；随后才实现品牌聚合、空范围响应和新口径字段。

## 4. 验证结果

### 后端

```text
mvn.cmd clean verify
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 隔离 MySQL 8 与真实 API

验证使用全新临时数据目录 `data11` 和纯虚构商品/订单，未访问现有开发数据库。

```text
LATEST=2026072801;SUCCESS=1
TABLES=7/7
INDEX=8
ANALYTICS_COLUMNS=4/4
ANALYTICS_INDEXES=2/2
ANALYTICS_PERMISSIONS=2/2
READINESS={"status":"UP"}
ANALYTICS_OWNER_API=PASS
BRAND_ANALYTICS_API=PASS
ANALYTICS_BEAUTICIAN_403=PASS
TEMP_MIGRATION_VERIFICATION=PASS
```

品牌验证覆盖：

- `TestBrandA` 的退款前订单行成交额为 288.00。
- `itemType=SERVICE` 时品牌构成为空。
- 店主拥有查看权限，技师请求返回 403。

### 前端

```text
admin-next: npm.cmd run build -> PASS
front-next: npm.cmd run build -> PASS
```

管理端构建日志仍包含既有的 `sass_binary_site` npm 配置警告和第三方
`@vueuse/core` PURE 注释警告，不影响本次构建结果。

## 5. 自检

| 检查项 | 结果 |
|---|---|
| 需求覆盖 | 品牌构成、排名、金额、订单、客户和数量已覆盖 |
| 权限 | 沿用 `analytics:view`；技师 403 已验证 |
| 错误与空状态 | 服务筛选不伪造品牌；无品牌数据展示明确空状态 |
| 日志与敏感数据 | 验证数据为虚构数据；响应不包含会员手机号、健康信息或支付凭据 |
| 数据库 | 无新表结构，完整 Flyway 链仍通过 |
| 文档 | API 口径、设计主题和本验证记录已同步 |
| 未完成边界 | 套餐、项目结余、利润、提成、营销、培训和开放平台未标记完成 |
| 提交 | `E:\face` 不是 Git 仓库，未擅自初始化，无法执行小步提交 |
