# M6-05 经营报表快照与受审计导出验证记录

验证日期：2026-08-01  
当前阶段：M6 商用运营  
任务编号：M6-05

## 1. 输入、输出和验收标准

输入：四份 V1.1 商用升级基线、M6-01 至 M6-04 经营事实、现有代码规范和 M5 完整验收结果。

输出：不可变 CSV 报表快照、本人列表/详情/下载、幂等与请求哈希、实时导出权限复核、内容哈希和审计日志，以及管理端白色主题页签。

验收标准：迁移与回滚说明齐全；不改订单事实；同键重放不重复；异参冲突；跨账号不可见；下载字节可校验；CSV 无敏感字段并防公式注入；测试、构建、MySQL 迁移和隔离 API 验证通过。

## 2. 测试驱动与缺陷闭环

- 先新增迁移契约、策略、CSV 和服务测试，首次有效红灯为缺少 M6-05 类与迁移；
- CSV 测试覆盖 UTF-8 BOM、SHA-256、指标版本和 `= + @` 公式前缀中和；
- 实际 MySQL 验收发现 JDBC 将 `DATETIME(3)` 返回 `LocalDateTime`，下载接口首次返回 500；
- 先新增 `downloadsSnapshotWhenMysqlReturnsDatetimeAsLocalDateTime` 复现测试并确认红灯，再按 Asia/Shanghai 转换，测试和隔离 API 均转绿。

## 3. 验证结果

### 3.1 后端测试与打包

| 验证 | 命令 | 结果 |
|---|---|---|
| M6-05 定向测试 | `mvn.cmd -q "-Dtest=M6ReportSnapshotMigrationContractTest,AnalyticsReportPolicyTest,AnalyticsReportCsvTest,AnalyticsReportSnapshotServiceTest" test` | 通过 |
| 全量测试 | `mvn.cmd -q clean verify` | 148 个测试，失败 0、错误 0、跳过 0 |
| 打包 | `mvn.cmd -q -DskipTests package` | 通过 |

Mockito 输出未来 JDK 动态代理提示，不影响当前测试结果。

### 3.2 管理端

| 验证 | 命令 | 结果 |
|---|---|---|
| 生产构建 | `npm.cmd run build` | 通过，1776 个模块转换完成 |
| UI 规则扫描 | `detect.mjs --json AnalyticsView.vue api.ts` | `[]`，0 项问题 |

当前会话未提供浏览器控制技能所要求的运行工具，因此没有生成交互式浏览器截图；未以其他工具替代或伪报。页面的类型检查、生产打包、权限显示逻辑和真实后端 API 已分别验证。

### 3.3 MySQL 8 与隔离 API

命令：`verify-m1-v3-security.ps1 -VerifyM6Report`。脚本使用全新临时 MySQL 数据目录和合成数据，验证结束自动停止并清理，不连接开发数据库。

```text
M6_REPORT_IDEMPOTENCY=PASS
M6_REPORT_REQUEST_HASH_CONFLICT=PASS
M6_REPORT_SELF_SCOPE=PASS
M6_REPORT_DOWNLOAD_INTEGRITY=PASS
M6_REPORT_SENSITIVE_DATA_GUARD=PASS
M6_REPORT_ROWS=2
M6_REPORT_DUPLICATE_KEYS=0
M6_REPORT_CONTENT_MATCH=1
M6_REPORT_CREATE_AUDIT=1
M6_REPORT_DOWNLOAD_AUDIT=1
M6_REPORT_SNAPSHOT=PASS
M6_REPORT_TABLES=1
M6_REPORT_INDEXES=3
M6_REPORT_CONSTRAINTS=3
M6_LATEST_FLYWAY=2026080101;SUCCESS=1
M6_REPORT_DATABASE_CONTRACT=PASS
M1_V3_SECURITY=PASS
```

下载样本 607 字节，响应 SHA-256 与实际字节一致；哈希值属于一次性合成验收数据，不作为固定业务契约。

## 4. 每步自检

| 检查项 | 结果 |
|---|---|
| 需求覆盖 | 快照生成、本人列表/详情/下载、过期、幂等、哈希、审计和管理端均覆盖 |
| 权限 | 复用 `analytics:export`；列表/详情/下载强制本人，下载再次校验门店范围 |
| 错误处理 | 非法格式 400、同键异参 409、跨账号 404、过期 410、权限撤销 403 |
| 日志与敏感数据 | 审计不保存 CSV；CSV 不含会员、证件、健康、密码和支付凭据 |
| 模块边界 | 分析模块只通过查询端口获取交易事实，不直读订单私有表 |
| 数据库 | expand-only 迁移、唯一键、索引、CHECK、兼容与回滚说明已同步 |
| 文档 | 数据库、M6 API、经营口径、阶段状态和本验收记录已同步 |

结论：M6-05 已通过代码、测试、构建、全新 MySQL 迁移和隔离 API 验收，可以进入 M6-06 营销治理与合规触达。
