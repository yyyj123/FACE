# M4-03 套餐与会员账户验收记录

验收日期：2026-07-29  
阶段：M4-03 套餐、次卡、会员账户、核销、冻结、有效期和冲正  
状态：通过

## 1. 需求覆盖

| 要求 | 实现与证据 |
|---|---|
| 套餐产品 | 创建、查询、版本、上下架、项目和次数快照 |
| 已支付订单发放 | 只允许已支付订单，订单+套餐唯一，幂等重放安全 |
| 技师核销 | 关联本人护理记录和护理项目，校验有效期、状态、余次和版本 |
| 套餐冲正 | 原流水不变，新增反向流水，恢复实例和项目余额 |
| 会员账户 | 余额、赠送金、积分三账户；入账、扣减、冻结、解冻 |
| 账户冲正 | 原流水不变，新增反向流水；账户ID与流水ID不同仍正确更新 |
| 权限与范围 | 门店权限、技师 SELF、会员 SELF、跨租户隔离 |
| 原子性 | 余额、流水、审计、outbox 同事务；故障注入全部回滚 |
| 三端贯通 | 管理端套餐账户、技师端核销、会员端我的资产 |

## 2. 测试先行与回归

新增或补强：

- `M4PackageAccountMigrationContractTest`
- `PackageAccountModuleBoundaryContractTest`
- `M4PackageAccountApiContractTest`
- `PackageLifecyclePolicyTest`
- `MemberAccountPolicyTest`
- `MemberAccountApplicationServiceValidationTest`
- `MemberAccountReversalContractTest`

浏览器验收发现账户冲正查询把流水主键误作账户主键；回归测试先记录失败，
修复为同时返回 `mal.id AS ledgerId` 与 `ma.id AS id` 后转绿。

后端全量命令：

```powershell
cd E:\face\backend-next
mvn test
```

结果：

```text
Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 3. 生产构建

三端浏览器验收前分别执行顾客/技师端和管理端生产构建，再使用 Vite preview
提供静态产物，避免开发依赖缓存影响验收。

```text
front-next: vue-tsc -b && vite build — PASS
admin-next: vue-tsc -b && vite build — PASS
```

## 4. 隔离 MySQL 8、迁移与业务回归

命令：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File E:\face\scripts\verify-m1-v3-security.ps1 `
  -DatabasePort 3342 -BackendPort 8202 -VerifyM4Ui
```

关键结果：

```text
LATEST=2026072901;SUCCESS=1
M4_TABLES=5
M4_PACKAGE_BALANCE=3.0000/3.0000
M4_WRITE_OFF_ROWS=1
M4_REVERSAL_ROWS=1
M4_ACCOUNT_REVERSALS=1
M4_OUTBOX_ROWS=6
M4_ROLLBACK_INSTANCES=0
M4_PACKAGE_ISSUE_IDEMPOTENCY=PASS
M4_PACKAGE_WRITE_OFF_AND_REVERSAL=PASS
M4_MEMBER_ACCOUNT_VERSION_AND_FREEZE=PASS
M4_FAILURE_INJECTION_ROLLBACK=PASS
M4_DATABASE_CONTRACT=PASS
M4_THREE_CLIENT_UI=PASS
M1_V3_SECURITY=PASS
```

验收使用独立临时 MySQL、数据库和后端，不访问开发库。失败注入验证套餐发放失败时
实例、流水与 outbox 均不残留。

## 5. 三端浏览器与视觉验收

- 技师端：护理工作台可进入套餐核销区，显示不可覆盖流水说明。
- 会员端 390px：套餐、有效期、项目余次、三类账户和资产导航可用。
- 管理端：套餐产品、会员资产、发放、冻结、调账和冲正入口可用。
- 三端均为白色主题，无横向溢出、错误提示或浏览器控制台错误。

截图：

- `E:\FACE\.artifacts\预览\M4-技师端-套餐核销.png`
- `E:\FACE\.artifacts\预览\M4-会员端-我的资产.png`
- `E:\FACE\.artifacts\预览\M4-管理端-套餐账户.png`

## 6. 安全与敏感数据自检

- 验收使用合成会员、订单、护理记录和账户数据。
- 日志与证据未输出手机号、证件、健康详情、密码、原始令牌或支付凭据。
- 关键写具备幂等键、请求摘要、乐观版本、审计和 outbox。
- 验收结束后临时数据库、后端、前端预览和浏览器进程由脚本清理。

## 7. 阶段结论

M4-03 门禁全部通过，可以进入 M4-04 退款申请、审批、执行和资金/套餐冲正闭环。
M4-03 不冒充 M4 全阶段完成；采购收货、支付适配器、对账和最终验收仍待后续阶段。
