# M6-07 培训与开放平台验收记录

## 1. 任务与验收

- 任务编号：M6-07。
- 输入：商用 PRD V1.2、数据库 V1.2、API 清单 V1.2、分阶段实施与验收 V1.2、现有代码规范。
- 输出：培训资格闭环、受控开放目录、双端白色主题页面、迁移/API/架构/验收文档。
- 验收：权限与本人范围、职责分离、状态机、版本、幂等、密钥只显一次、摘要存储、防重放、限流、审计、敏感字段隔离、迁移和浏览器证据全部通过。

## 2. 测试驱动证据

先新增 `TrainingPolicyTest`、`IntegrationClientPolicyTest` 和 `M6TrainingOpenPlatformMigrationContractTest`。首次执行因策略类和迁移尚不存在产生 24 个编译错误；实现后目标测试 5/5 通过。

完整后端命令：

```powershell
cd E:\face\backend-next
mvn -q clean verify
mvn -q -DskipTests package
```

Surefire 汇总：160 个测试，失败 0、错误 0、跳过 0，共 84 个测试套件。

前端命令：

```powershell
cd E:\face\admin-next; npm.cmd run build
cd E:\face\front-next; npm.cmd run build
```

管理端构建转换 1782 个模块，技师端构建转换 199 个模块；两端 TypeScript 与 Vite 生产构建均通过。Impeccable 扫描结果为 `[]`。

## 3. 隔离迁移与运行验收

```powershell
cd E:\face
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-m1-v3-security.ps1 `
  -DatabasePort 3325 -BackendPort 8197 -VerifyM6TrainingOpen
```

关键证据：

- `M6_TRAINING_OPEN_RUNTIME=PASS`
- `M6_TRAINING_OPEN_UI=PASS`
- `M6_TRAINING_OPEN_DATABASE_CONTRACT=PASS`
- `TRAINING_RECORD_STATUS=PASSED;VERSION=3`
- `TRAINING_HISTORY_ROWS=4`、`TRAINING_SELF_VERIFY_ROWS=0`
- `INTEGRATION_CLIENT_STATUS=REVOKED;SECRET_VERSION=2`
- `PLAINTEXT_SECRET_ROWS=0`
- `OPEN_SCOPE=catalog:read`、`OPEN_SENSITIVE_FIELDS=0`
- 6 张新表、6 个唯一键、7 项权限、最新 Flyway `2026080103;SUCCESS=1`
- M1–M5 历史安全、预约、护理、套餐、库存、退款、提成、结算、售后和通知回归全部通过。

## 4. 浏览器证据

- `E:\FACE\.artifacts\预览\M6-员工培训-管理端.png`
- `E:\FACE\.artifacts\预览\M6-培训开放平台-管理端.png`
- `E:\FACE\.artifacts\预览\M6-培训资格-技师端.png`

浏览器控制台错误 0、网络失败 0、横向溢出 0；管理端与技师端均为白色主题。

## 5. 已知非阻塞事项

JDK 输出 Mockito 动态 Agent 的未来兼容警告，当前测试不受影响；应在后续依赖升级任务中按 Mockito 官方方式配置测试 Agent。M6 商用 WPS 文档集中同步安排在 M6-08，总体回归前不会提前改写四份冻结基线。
