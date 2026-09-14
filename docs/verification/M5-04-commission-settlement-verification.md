# M5-04 提成结算批次验收记录

## 1. 任务与影响

- 输入：四份 V1.2 商业基线、M5 生命周期/API 契约及 M5-02/M5-03 验收结果。
- 输出：结算批次、不可变明细、计算/确认/支付/关闭/作废、职责分离、技师本人结算摘要。
- 模块边界：结算只引用提成流水；不写订单、支付、退款、服务、套餐或库存私有表。
- 历史策略：不回填旧结算、不伪造支付，已确认明细只保留或整批作废。

## 2. 测试先行

实现前新增并确认失败：

```text
CommissionSettlementPolicyTest
M5CommissionSettlementMigrationContractTest
M5CommissionSettlementApiContractTest
```

失败分别证明状态机/职责分离、迁移约束和 API 尚未实现。完成实现后定向测试转绿。

首次新鲜库迁移还由数据库原生约束拒绝了非法风险枚举 `HIGH`；按既有
`NORMAL/SENSITIVE/CRITICAL` 约束修正为 `SENSITIVE` 后重新从空库执行，未绕过约束或手工篡改 Flyway 历史。

## 3. MySQL 8 全历史链

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass \
  -File E:\face\scripts\verify-m1-v3-security.ps1 \
  -DatabasePort 3401 -BackendPort 8261 \
  -VerifyM5CommissionSettlements
```

关键结果：

```text
M5_COMMISSION_ENTRY_LIFECYCLE=PASS
M5_SETTLEMENT_BATCHES=2
M5_SETTLEMENT_ITEMS=2
M5_SETTLEMENT_SETTLED_ENTRIES=2
M5_SETTLEMENT_HISTORY=2
M5_SETTLEMENT_FINAL_STATUS=CLOSED
M5_SETTLEMENT_TEMP_FINANCE_CLEANUP=0
M5_SETTLEMENT_CREATE_IDEMPOTENCY=PASS
M5_SETTLEMENT_DUTY_SEPARATION=PASS
M5_SETTLEMENT_ACTIVE_ENTRY_UNIQUENESS=PASS
M5_SETTLEMENT_PAYMENT_AND_CLOSE=PASS
M5_SETTLEMENT_TERMINAL_GUARD=PASS
M5_SETTLEMENT_TECHNICIAN_SELF_SUMMARY=PASS
M5_COMMISSION_SETTLEMENT=PASS
M5_SETTLEMENT_TABLES=2
M5_SETTLEMENT_PERMISSIONS=3
M5_SETTLEMENT_CONSTRAINTS=5
LATEST=2026073005;SUCCESS=1
M1_V3_SECURITY=PASS
```

命令退出码为 0，临时数据库与后端端口已清理。

## 4. 全量测试与构建

```text
mvn.cmd -q test
TESTS=128;FAILURES=0;ERRORS=0;SKIPPED=0;FILES=65

mvn.cmd -q -DskipTests package
exit code 0

admin-next: npm.cmd run build
1773 modules transformed; built successfully

front-next: npm.cmd run build
190 modules transformed; built successfully
```

全量测试首次发现 API 文档契约仍要求保留内部冲正端点标识；已恢复该标识并明确其不注册公开 HTTP 路由，复跑 128 项测试全部通过。

## 5. 每步自检

1. 需求：结算批次、明细、汇总、支付事实、关闭、作废和 SELF 摘要已覆盖。
2. 权限：创建/计算与确认职责分离；技师不获得结算管理/审批权限。
3. 错误：状态、版本、幂等、无可结算流水、重复占用均返回失败，不伪造成功。
4. 日志：审计/outbox 仅保存批次标识和安全状态。
5. 数据库：生成列唯一索引、CHECK、外键、兼容和回滚说明已同步。
6. 文档：M5 API、数据库、阶段状态和验收记录已同步。

## 6. 阶段快照

```text
E:\face\backups\m5\m5-04\face-m5-source-20260730-102231.zip
bytes=95666210
sha256=14D218E0D94E52C2494BA04B6BA45E961757FC7064049EC7FF87E29D7453B36D
```

## 7. 结论

M5-04 验收通过。下一阶段为 M5-05：售后工单、通用审批、提成调整审批与职责分离。
