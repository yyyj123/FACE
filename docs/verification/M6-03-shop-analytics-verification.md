# M6-03 门店经营对比验证记录

验证日期：2026-07-28  
当前阶段：M6 商用运营  
任务编号：M6-03

## 1. 输入、输出和验收标准

输入：

- 四份 V1.1 商用升级基线
- M6-01/M6-02 经营分析接口、权限和数据质量口径
- 当前授权门店上下文与白色主题管理端

输出：

- `/api/v3/analytics/sales/overview` 兼容增加 `shopBreakdown`
- 管理端增加“门店对比”视图
- 门店订单、消费客户、实收、退款、净收款和单均净收

验收标准：

- 只汇总当前租户和账号授权门店内的有效订单。
- 指定门店时不返回其他门店。
- 退款保持订单级冲正，不按品项或门店外推。
- 后端权限仍是最终授权边界，技师访问返回 403。
- 后端测试、完整 Flyway、隔离 API 和双前端构建全部通过。

## 2. 影响与兼容

| 项目 | 处理 |
|---|---|
| 状态机 | 不修改 |
| 数据库 | 无新增字段、表或索引 |
| API | overview 响应增加 `shopBreakdown`，向后兼容 |
| 权限 | 沿用 `analytics:view` 和门店范围 |
| 模块边界 | 交易模块只按订单 `shop_id` 分组；前端映射授权门店名称 |
| 历史数据 | 复用订单事实，不执行回填 |

M6 完整范围依赖 M5。本任务不实现利润、套餐结余、提成、营销、培训、开放平台或外部市场指标。

## 3. 测试驱动记录

先修改 `TransactionAnalyticsQueryServiceTest`，要求安全空结果包含 `shopBreakdown`
且指标版本为 `M6-03-v1`。首次执行失败，实际仍为 `M6-02-v1`；随后才实现按授权范围的门店汇总。

## 4. 验证结果

### 后端

```text
mvn.cmd clean verify
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 隔离 MySQL 8 与真实 API

使用全新临时数据目录 `data12`，从基线执行完整 Flyway，并写入一条纯虚构零售订单。
未访问现有开发数据库。

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
SHOP_ANALYTICS_API=PASS
ANALYTICS_SHOP_SCOPE_403=PASS
ANALYTICS_BEAUTICIAN_403=PASS
TEMP_MIGRATION_VERIFICATION=PASS
```

门店验证覆盖：

- 门店 1 实收和净收款均为 288.00。
- 不在授权范围内的门店 999 返回 403。
- 技师访问经营分析返回 403。
- M6-02 品牌聚合回归仍通过。

### 前端

```text
admin-next: npm.cmd run build -> PASS
front-next: npm.cmd run build -> PASS
```

管理端构建日志仍包含既有 `sass_binary_site` npm 配置警告和第三方
`@vueuse/core` PURE 注释警告，不影响本次构建。

## 5. 自检

| 检查项 | 结果 |
|---|---|
| 需求覆盖 | 授权门店的订单、客户、实收、退款、净收款和单均净收已覆盖 |
| 权限 | 全部查询使用 `analytics:view`；越权门店和技师 403 已验证 |
| 错误与空状态 | 单店筛选提示如何恢复多店对比；无订单时显示明确空状态 |
| 日志与敏感数据 | 测试数据完全虚构；响应不包含手机号、健康信息或支付凭据 |
| 数据库 | 无新结构；完整 Flyway 链仍通过 |
| 模块边界 | 交易查询不读取门店私有表；名称由授权上下文映射 |
| 文档 | API 口径和验证记录已同步 |
| 未完成边界 | 利润、套餐、提成、营销、培训和开放平台未标记完成 |
| 提交 | `E:\face` 不是 Git 仓库，未擅自初始化 |
