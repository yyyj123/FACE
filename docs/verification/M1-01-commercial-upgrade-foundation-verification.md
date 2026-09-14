# M1-01 商用升级基础门禁验证记录

验证日期：2026-07-26  
当前阶段：M1 基础治理  
任务编号：M1-01

## 1. 输入、输出与验收标准

输入基线：

- 《美容PRD_V1.0》
- 《美容数据库V1.0》
- 《美容院店铺管理系统 API接口清单》
- 《美容院店铺管理系统 分阶段开发实施与验收 V1.0》
- `backend-next`、`admin-next`、`front-next` 现有代码及迁移

本次输出：

- 四份 V1.1 优化文档
- 权限点与门店范围同源校验
- 预约完成入口收口
- 数据库契约健康检查
- 审计表、事务 outbox 与权限查询索引迁移
- 全新安装的历史迁移兼容回调
- 数据库连接验证脚本和迁移/回滚说明

验收标准：

- 后端测试及打包通过
- 两个前端生产构建通过
- 隔离 MySQL 8 从基线到最新版本的全部 Flyway 迁移通过
- Spring Boot readiness 为 `UP`
- 不修改历史迁移 checksum，不删除或覆盖历史数据

## 2. 影响模块、表、接口、权限和状态

模块：

- 权限：`TenantAccessService`
- 会员：`MemberService`
- 预约：`AppointmentService`
- 护理记录：`ServiceRecordService`
- 库存：`InventoryService`
- 交易：`TransactionService`
- 运维：`DatabaseContractHealthIndicator`

表与索引：

- 新增 `data_access_log`
- 新增 `outbox_event`
- 新增 `idx_account_shop_role_permission_lookup`
- 迁移前按需创建五张空兼容表：`chezhu`、`fuwuyuyue`、`peijianxinxi`、`weixiujilu`、`peijianchuku`

接口与状态：

- 现有 `/api/v2` URL 与响应契约保持兼容。
- 管理操作改为同时校验权限点和门店范围。
- 通用预约状态接口不再允许直接写入 `COMPLETED`。
- 完成预约必须通过护理记录完成应用服务，以保留事务、审计和后续库存/套餐扩展点。

## 3. 历史数据与迁移策略

- 本次为 Expand 迁移，不删除、不重命名、不缩窄现有列。
- 五张兼容表只在缺失时创建；已有遗留库保持原定义和原数据。
- 全新安装时兼容表为空，历史回填自然成为 no-op，不伪造客户或业务数据。
- 历史 Flyway 文件不修改，避免 checksum 漂移。
- 回滚优先回退应用并关闭新写入；新增表和索引保留。物理删除只能在独立维护窗口、完成备份和空表确认后执行。

## 4. 测试驱动记录

- 预约完成门禁：先增加 `AppointmentCompletionGuardTest`，再收口状态写入。
- 权限范围：先扩展 `TenantAccessServiceTest`，再替换会员、预约、护理、库存和交易管理查询。
- 数据库契约：先增加 `DatabaseContractHealthIndicatorTest`。
- 隔离验证发现复合索引在 `information_schema.statistics` 中返回 8 行；先增加回归测试并确认失败，再将健康条件修正为“索引记录数大于 0”。

当前尚未将套餐核销、提成结算和完整退款冲正实现标记为完成；这些能力按 V1.1 阶段计划继续执行，不能由本次基础门禁替代。

## 5. 验证结果

### 后端

命令：

```powershell
mvn.cmd clean verify
```

结果：

- 17 tests
- 0 failures
- 0 errors
- 生成 `face-chain-platform-0.1.0-SNAPSHOT.jar`
- BUILD SUCCESS

### 管理端

命令：

```powershell
npm.cmd run build
```

结果：Vite 生产构建成功。构建日志含第三方 `@vueuse/core` PURE 注释位置警告和旧 npm 配置警告，不影响本次产物，但应在依赖治理任务中处理。

### 顾客端

命令：

```powershell
npm.cmd run build
```

结果：Vite 生产构建成功。

### 隔离数据库与应用连接

验证环境：

- 独立 MySQL 8 临时实例
- 端口 `3318`
- 独立临时数据目录
- 后端验证端口 `8190`
- 不访问现有开发数据库 `127.0.0.1:3308`

结果：

```text
LATEST=2026072603;SUCCESS=1
TABLES=7/7
INDEX=8
READINESS={"status":"UP"}
TEMP_MIGRATION_VERIFICATION=PASS
```

说明：`INDEX=8` 表示该复合索引的 8 个索引列记录，不是重复创建 8 个索引。

## 6. 自检

| 检查项 | 结果 |
|---|---|
| 需求覆盖 | M1-01 基础治理已覆盖；M2-M6 未冒充完成 |
| 权限 | 管理查询和命令按权限点及门店范围同源校验 |
| 错误处理 | 非法完成入口返回业务冲突；数据库契约异常使 readiness DOWN |
| 日志与敏感数据 | 健康检查和验证脚本不输出密码、手机号、健康信息或支付凭据 |
| 测试 | 回归测试、全量后端测试、双前端构建、隔离迁移均已执行 |
| 文档同步 | API、数据库、PRD、分阶段验收、迁移说明和本记录已同步 |
| 提交 | 当前目录不是 Git 仓库，未擅自初始化仓库，因此无法执行小步提交 |

## 7. 发布限制

现有开发库 `127.0.0.1:3308` 的凭据未由用户提供，因此本次没有对该实例执行迁移，也没有猜测或复用历史硬编码密码。发布前需由授权人员在当前终端设置 `DB_PASSWORD`，再运行：

```powershell
E:\face\scripts\verify-database-connection.ps1
```

真实库只允许在备份完成、迁移窗口批准、影子验证通过后执行 Flyway。真实库验证未通过前，不得标记生产发布完成。
