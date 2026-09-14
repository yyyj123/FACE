# M3 核心业务闭环验收记录

验收日期：2026-07-28  
阶段：M3 预约—到店—护理—确认—订单支付纵向闭环  
状态：通过

## 1. 需求覆盖

| M3 要求 | 实现与证据 |
|---|---|
| 预约状态机 | `PENDING → CONFIRMED → CHECKED_IN → IN_SERVICE → COMPLETED`；取消、爽约及非法跳转由领域策略拦截 |
| 服务状态机 | 开始服务创建 `IN_PROGRESS` 服务记录；完成后为 `COMPLETED`，已完成事实禁止覆盖 |
| 技师本人范围 | 技师只能查询、开始和完成分配给本人的预约与护理记录 |
| 护理完成原子性 | 护理事实、耗材领用、库存流水、库存余额、预约完成、顾客确认、审计与 outbox 在同一事务提交 |
| 顾客独立确认 | 服务完成自动生成 `PENDING` 确认；顾客可确认或反馈问题，确认状态不改写服务事实 |
| 追加式更正 | 已完成护理只允许向 `service_record_correction` 追加更正，保留原因、基准版本、幂等键和操作人 |
| 订单与支付幂等 | 创建订单、支付及完成服务均比较幂等键和请求哈希；同键异载荷返回冲突 |
| 三端贯通 | 管理端可追踪服务与更正；技师端执行本人护理；顾客端查看并确认护理结果 |
| 兼容性 | V1/V2 路由保持；V2 顾客/技师端由适配器调用同一领域服务；新能力由 `/api/v3` 暴露 |

## 2. 测试先行与规则测试

本阶段新增或补强以下自动化测试：

- `AppointmentCompletionGuardTest`
- `AppointmentSelfScopeTest`
- `ClientAppointmentProjectionContractTest`
- `TenantIdentityScopeTest`
- `CustomerConfirmationServiceTest`
- `ServiceRecordCorrectionServiceTest`
- `ServiceModuleBoundaryContractTest`
- `ServiceResourceLegacyCompatibilityContractTest`
- `TransactionIdempotencyContractTest`
- `OutboxEventServiceTest`
- `M3MigrationContractTest`

其中客户端预约投影和旧库护理知识兼容先记录失败，再完成实现并转绿。

## 3. 后端全量验证

命令：

```powershell
cd E:\face\backend-next
mvn test
```

结果：

```text
Tests run: 72, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 4. 前端生产构建

顾客/技师端：

```text
face-client-v2: vue-tsc -b && vite build
187 modules transformed
BUILD SUCCESS
```

管理端：

```text
face-chain-admin: vue-tsc -b && vite build
1749 modules transformed
BUILD SUCCESS
```

管理端保留既有 npm `sass_binary_site` 与第三方 `@vueuse/core` PURE 注释警告；没有关闭检查，构建退出码为 0。

## 5. 隔离 MySQL 8、迁移与故障注入

命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File E:\face\scripts\verify-m1-v3-security.ps1 `
  -VerifyM3Ui -DatabasePort 3335 -BackendPort 8207
```

验证使用独立临时 MySQL 8、临时数据库与后端，不访问开发库。结果：

```text
LATEST=2026072804;SUCCESS=1
M3_SUCCESS_RECORD=COMPLETED
M3_SUCCESS_APPOINTMENT=COMPLETED
M3_SUCCESS_CONFIRMATION=CONFIRMED
M3_SUCCESS_MOVEMENTS=1
M3_SUCCESS_OUTBOX=1
M3_SUCCESS_CORRECTIONS=1
M3_SUCCESS_PAYMENTS=1
M3_IDEMPOTENCY_REPLAY=PASS
M3_FAILURE_INJECTION_ROLLBACK=PASS
M3_THREE_ROLE_SERVICE_FLOW=PASS
M3_DATABASE_CONTRACT=PASS
M3_THREE_CLIENT_UI=PASS
M1_V3_SECURITY=PASS
```

故障注入在顾客确认写入点主动抛错，验证服务记录保持 `IN_PROGRESS`、预约保持 `IN_SERVICE`，护理事实、确认、库存流水与 outbox 均为 0，证明跨模块写入整体回滚。

## 6. 浏览器与视觉验收

- 技师端：本人任务、护理记录、耗材同步扣减说明可见，无错误提示或横向溢出。
- 顾客端 390px：待确认护理结果、确认与反馈入口可见；明确提示确认不会改写服务事实。
- 管理端：服务记录、追加更正表单和“不可覆盖原始护理事实”提示可见。
- 三端均为白色主题；浏览器控制台错误数为 0。

证据：

- `E:\FACE\.artifacts\预览\M3-技师护理工作台-桌面.png`
- `E:\FACE\.artifacts\预览\M3-顾客护理确认-窄屏.png`
- `E:\FACE\.artifacts\预览\M3-管理端追加更正-桌面.png`

## 7. 权限、日志与敏感数据自检

- 技师范围依赖账号绑定的 `staff_id`，顾客范围依赖账号绑定的 `member_id`；缺失绑定直接拒绝。
- 管理端追加更正要求 `service_record:correct`；顾客确认查询受本人范围和权限保护。
- 所有关键写操作使用乐观版本、幂等键、请求哈希与审计/outbox。
- 验收数据为隔离合成数据；日志和证据不输出手机号、证件、健康详情、密码、原始令牌或支付凭据。
- 验证结束后临时数据库、后端、Vite 与浏览器进程由脚本清理。

## 8. 阶段结论

M3 门禁全部通过，可以进入 M4。M4 的套餐账户、核销、转赠/冻结、有效期和冲正尚未在本记录中冒充完成；退款、完整库存闭环和提成仍按 V1.1 阶段边界继续实施。
