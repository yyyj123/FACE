# M5-05 售后、审批与提成调整验收记录

## 1. 输入、输出与验收标准

输入为四份 V1.2 商业基线、M5 生命周期/API 契约和 M5-02 至 M5-04 已验收能力。

输出：

- 售后工单、追加日志、状态动作、会员 SELF、重开和退款关联；
- 通用审批实例、步骤、决定、取消、候选权限和职责分离；
- 经审批的正/负提成调整申请和不可变调整流水；
- 迁移、权限、幂等、版本、审计、outbox 和敏感数据边界。

验收标准：申请人不能审批本人申请；决定不能覆盖；只有审批通过创建调整流水；原提成不更新；售后退款复用真实退款应用服务；测试、构建和新鲜数据库历史链全部通过。

## 2. 测试驱动证据

实现前新增并确认失败：

```text
AfterSalePolicyTest
ApprovalPolicyTest
M5AfterSaleApprovalMigrationContractTest
M5AfterSaleApprovalApiContractTest
CommissionAdjustmentPolicyTest
```

失败证明状态机、审批职责、迁移、API 和调整金额规则尚未实现；完成实现后定向测试及全量测试均转绿。

## 3. MySQL 8 全历史链

执行：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass \
  -File E:\face\scripts\verify-m1-v3-security.ps1 \
  -DatabasePort 3403 -BackendPort 8263 \
  -VerifyM5AfterSaleApproval
```

关键证据：

```text
M5_AFTERSALE_CASES=2
M5_AFTERSALE_LOGS=8
M5_APPROVAL_INSTANCES=2
M5_APPROVAL_DECISIONS=2
M5_ADJUSTMENT_ENTRIES=1
M5_ADJUSTMENT_ORIGINAL_AMOUNT=29.80
M5_AFTERSALE_FINAL_STATUS=CLOSED
M5_AFTERSALE_REFUND_STATUS=PENDING
M5_TEMP_MANAGER_CLEANUP=0
M5_ADJUSTMENT_APPROVAL_IDEMPOTENCY=PASS
M5_APPROVAL_DUTY_SEPARATION=PASS
M5_APPROVAL_IMMUTABLE_DECISION=PASS
M5_AFTERSALE_STATE_MACHINE=PASS
M5_AFTERSALE_MEMBER_SELF=PASS
M5_AFTERSALE_REFUND_BOUNDARY=PASS
M5_AFTERSALE_APPROVAL_ADJUSTMENT=PASS
M5_AFTERSALE_APPROVAL_TABLES=5
M5_AFTERSALE_APPROVAL_PERMISSIONS=6
M5_AFTERSALE_APPROVAL_CONSTRAINTS=6
LATEST=2026073006;SUCCESS=1
M1_V3_SECURITY=PASS
```

命令退出码为 0，临时数据库、后端端口和临时管理角色已清理。

## 4. 全量测试与构建

```text
mvn.cmd -q test
TESTS=134;FAILURES=0;ERRORS=0;SKIPPED=0;FILES=70

mvn.cmd -q -DskipTests package
exit code 0

admin-next: npm.cmd run build
1773 modules transformed; built successfully

front-next: npm.cmd run build
190 modules transformed; built successfully
```

## 5. 自检

1. 需求：售后、审批、调整、退款关联、SELF 和职责分离已覆盖。
2. 权限：管理动作、候选审批权限、会员 SELF 均由后端校验。
3. 状态：关闭工单、审批终态和已应用调整均不可覆盖。
4. 幂等：工单创建、日志、审批决定和调整申请/应用均有唯一键。
5. 日志：普通日志/outbox 不记录健康、支付凭据或完整售后正文。
6. 数据库：迁移、索引、检查约束、兼容和回滚说明已同步。

## 6. 源码快照

```text
SOURCE_SNAPSHOT=E:\face\backups\m5\m5-05\face-m5-source-20260730-153138.zip
SOURCE_SNAPSHOT_BYTES=95700157
SOURCE_SNAPSHOT_SHA256=3AAAFEDAA2805E7470054A1D724B7724F0F99DD4532D592CF959F82A045262A3
M5_SOURCE_SNAPSHOT=PASS
```

## 7. 结论

M5-05 验收通过。下一阶段为 M5-06：站内通知以及管理端、技师端、会员端业务界面。
