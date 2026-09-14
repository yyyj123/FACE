# M0-01 基线冻结与恢复验证记录

验证日期：2026-07-28  
当前阶段：M0 基线与保护  
任务编号：M0-01  
状态：通过

## 1. 输入、输出和验收标准

输入：

- 四份 V1.1 商用升级基线。
- `PRODUCT.md`、`DESIGN.md`、现有代码、Flyway 和验证脚本。
- MySQL 8、Maven 和两个 Vue 3 前端工程。

输出：

- 数据库、接口、角色、状态和术语冻结记录。
- 需求—接口—表—测试差异矩阵。
- 虚构数据保护规则。
- 可恢复的隔离数据库备份和源码回滚快照。
- 后端测试、双前端构建、迁移与 readiness 证据。

验收标准：

- 四份 V1.1 文档与工程规范不存在未记录冲突。
- 备份可在全新隔离库恢复，核心表行数和 Flyway 版本一致。
- 恢复库连接后的 readiness 为 `UP`。
- 后端全量测试和两个前端生产构建通过。
- 无真实客户敏感数据进入测试日志。
- 回滚资产具有 SHA-256 清单。

## 2. 影响

| 项目 | 处理 |
|---|---|
| 状态机 | 不修改，仅冻结术语与兼容映射 |
| 数据库 | 不修改业务结构，不连接现有开发/生产库 |
| API | 不修改，仅冻结 v1/v2/v3 版本策略 |
| 权限 | 不修改，仅记录角色代码冲突 |
| 历史数据 | 不回填、不覆盖、不删除 |
| 测试数据 | 使用全新隔离实例和虚构标记 |

## 3. 基线复核结果

- `PRODUCT.md` 已将套餐、支付和提成从“永久非目标”修正为 M0-M3 延期、M4/M5 实现。
- 规范角色代码冻结为 `REGIONAL_MANAGER`；当前物理角色定义缺失，列入 M1 差距。
- 护理记录 `VOID/VOIDED` 和库存调拨状态差异已冻结为适配/Expand 策略。
- 已完成的 M6-01 至 M6-04 只作为只读销售分析切片保留，不代表 M6 完成。

## 4. 验证结果

### 4.1 四文档一致性

- 四份文件均为 V1.1，基线日期均为 `2026-07-26`。
- 当前阶段均为“M1 商用升级基线与安全闭环”。
- 状态均为“受控基线 / Conditional Go”。
- PRD、数据库、API 和实施文档的阶段、V3、迁移及强制门禁关键语义均存在。
- 结果：`M0_DOCUMENT_CONSISTENCY=PASS`。

### 4.2 隔离数据库备份恢复

环境：

- MySQL 8.0.41。
- 隔离数据库端口 `3320`。
- 隔离后端端口 `8192`。
- 未连接现有开发数据库或生产数据库。
- 数据来源为仓库基线 schema、Flyway 和虚构 M0 标记。

结果：

```text
SOURCE_LATEST=2026072801;SUCCESS=1
SOURCE_TABLES=52
SOURCE_tenant=1
SOURCE_shop=1
SOURCE_member=1
SOURCE_staff=2
SOURCE_appointment=0
SOURCE_service_record=0
SOURCE_stock_balance=0
SOURCE_sales_order=0
SOURCE_data_access_log=1
SOURCE_outbox_event=0
RESTORE_SUMMARY_MATCH=PASS
RESTORE_READINESS=UP
SYNTHETIC_MARKER_RESTORED=PASS
M0_BACKUP_RESTORE=PASS
```

已验证备份：

- 文件：`backups/m0/face-salon-m0-synthetic-20260728-132543.sql`
- SHA-256：`2D9CEAD843E808A3F77F37A9CC52735E3261E15EED560F8E878DBAF500F4DF90`
- 独立 `.sha256` 清单和无敏感正文的恢复摘要均已生成。

两次未通过的验证备份已改名为 `.unverified.sql`，不会被误认为可发布恢复点。

恢复脚本在验证过程中先后暴露并修复了四个自动化问题：MySQL 退出等待、JDBC URL 变量边界、单行结果自动解包和 Maven/Java 子进程切换。每次失败均未连接现有数据库、未标记通过，最终从全新数据目录完整复跑成功。

### 4.3 后端测试

- 命令：`mvn.cmd clean verify`
- 测试：24 个
- 失败：0
- 错误：0
- 跳过：0
- 构建：`BUILD SUCCESS`
- 产物：`backend-next/target/face-chain-platform-0.1.0-SNAPSHOT.jar`

现有 Mockito 动态 agent 和 unchecked 编译提示为非阻断警告，列入后续依赖治理。

### 4.4 前端构建

| 工程 | 命令 | 结果 |
|---|---|---|
| `admin-next` | `npm.cmd run build` | 通过 |
| `front-next` | `npm.cmd run build` | 通过 |

管理端构建存在旧 npm 配置和第三方 `@vueuse/core` PURE 注释非阻断警告。

### 4.5 回滚点

- 数据库：已验证 SQL dump、SHA-256 和恢复摘要。
- 源码：通过 `scripts/create-m0-source-snapshot.ps1` 建立压缩快照和独立 SHA-256。
- 当前工作区不是 Git 仓库，因此未伪造 Git 提交；M0 使用只读源码快照作为当前回滚点。

## 5. 阶段自检

| 检查项 | 结果 |
|---|---|
| 需求覆盖 | M0 的保护、冻结、虚构数据、差异矩阵、备份恢复和回滚点均覆盖 |
| 权限 | 未修改权限；区域经理代码冲突已记录到 M1 |
| 错误处理 | 恢复脚本区分主错误和清理错误，失败备份显式隔离 |
| 日志 | 不输出密码、手机号、健康信息或支付凭据 |
| 测试 | 后端 24 项、双前端构建、文档一致性、迁移/恢复/readiness 均验证 |
| 文档同步 | PRODUCT、冻结记录、差异矩阵、Runbook 和本记录已同步 |
| 阶段边界 | 未修改业务状态、表、接口或权限，未提前进入 M1 |

结论：M0 开发/影子环境基线达到验收条件。生产发布前仍须由授权人员对真实生产备份执行同一 Runbook；该发布限制不阻塞后续受控开发阶段。
