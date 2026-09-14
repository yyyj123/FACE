# M5-03 提成流水、冻结与退款冲正验收记录

## 1. 任务输入、输出与验收标准

输入为四份 V1.2 商业基线、M5-01 生命周期/API 契约、M5-02 规则版本与来源快照，以及现有模块边界和迁移规范。

输出包括：

- 事件驱动的自动提成入账与投影检查点；
- 不可变入账/冲正流水及追加式状态历史；
- 流水查询、详情、冻结和解冻 API；
- 部分退款比例冲正、最终尾差上限和事件重放幂等；
- 规则版本、来源快照和历史金额复算；
- 管理范围与技师 SELF 范围隔离；
- MySQL 8 新鲜数据库全历史链验证。

验收标准：测试先行；原流水不覆盖；事件和业务唯一键双重幂等；退款只追加负向冲正；冻结/解冻使用版本、幂等、审计；技师只能查看本人；迁移、全量测试、后端打包和双前端构建全部通过。

## 2. 影响模块与迁移方案

- 提成：新增流水、历史、规则匹配、来源快照引用、冻结/解冻、退款冲正和投影服务。
- 服务/支付/退款：接口和私有表不变，继续发布既有 outbox 事件。
- 权限：新增 `commission:freeze`；管理/财务可冻结，技师仅 SELF 查看。
- 审计/outbox：入账、冲正、冻结、解冻只记录安全业务标识，不记录手机号、护理健康信息或支付凭据。
- 迁移：Expand-only；迁移前相关事件建立跳过基线，不回填或猜测历史提成。
- 回滚：先停投影和写接口，保留新表只读；历史纠错只能追加反向流水。

## 3. 测试驱动证据

实现前新增并确认失败：

```text
CommissionEntryPolicyTest
M5CommissionEntryMigrationContractTest
M5CommissionEntryApiContractTest
CommissionEventProjectionContractTest
CommissionSourceSnapshotSelfScopeTest
```

失败分别证明状态机、退款比例与累计上限、迁移约束、API、outbox 投影以及技师来源快照 SELF 边界尚未实现。完成实现后，定向测试和全量测试均转绿。

## 4. MySQL 8 新鲜数据库与历史链

执行：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass \
  -File E:\face\scripts\verify-m1-v3-security.ps1 \
  -DatabasePort 3399 -BackendPort 8259 \
  -VerifyM5CommissionEntries
```

关键结果：

```text
M2_TEMP_MANAGER_ROLE_CLEANUP=PASS
M3_THREE_ROLE_SERVICE_FLOW=PASS
M4_PACKAGE_ISSUE_IDEMPOTENCY=PASS
M4_PACKAGE_WRITE_OFF_AND_REVERSAL=PASS
M5_COMMISSION_RULE_VERSIONING=PASS
M5_ENTRY_ROWS=2
M5_ENTRY_HISTORY=4
M5_ENTRY_PROJECTIONS=3
M5_ENTRY_FAILED_PROJECTIONS=0
M5_ENTRY_ORIGINAL_AMOUNT=29.80
M5_ENTRY_REVERSAL_AMOUNT=-7.45
M5_ENTRY_ORDER_PAYABLE=268.00
M5_ENTRY_REFUND_AMOUNT=67.00
M5_ENTRY_EVENT_IDEMPOTENCY=PASS
M5_ENTRY_FREEZE_UNFREEZE=PASS
M5_ENTRY_REFUND_REVERSAL=PASS
M5_ENTRY_HISTORICAL_REPRODUCTION=PASS
M5_ENTRY_SELF_PERMISSION=PASS
M5_COMMISSION_ENTRY_LIFECYCLE=PASS
M5_ENTRY_TABLES=3
M5_ENTRY_PERMISSIONS=1
M5_ENTRY_CONSTRAINTS=5
LATEST=2026073004;SUCCESS=1
M1_V3_SECURITY=PASS
```

命令退出码为 0，临时数据库与后端端口已清理。

## 5. 自动化测试与构建

### 后端

```text
mvn.cmd -q test
TESTS=123;FAILURES=0;ERRORS=0;SKIPPED=0;FILES=62

mvn.cmd -q -DskipTests package
exit code 0
```

### 前端

```text
admin-next: npm.cmd run build
1773 modules transformed; built successfully

front-next: npm.cmd run build
190 modules transformed; built successfully
```

## 6. 权限与安全自检

- `commission:freeze` 未授予技师；技师冻结请求返回 403。
- 技师流水列表和详情按绑定 `staff_id` 限制。
- 来源快照详情同步执行 SELF 校验；越权对象按 404 防枚举。
- 投影错误只保存安全错误类别，不输出客户、护理、健康或支付敏感正文。
- 入账、冲正、冻结和解冻均保留历史与审计；同键重放不重复写。

## 7. 阶段快照

```text
E:\face\backups\m5\m5-03\face-m5-source-20260730-100858.zip
bytes=95644695
sha256=8CB62DCCA7F8753696BBA4ED1F3A550580AE76AC10D95DDE38A09201FCC3DB31
```

## 8. 阶段结论

M5-03 验收通过。下一阶段为 M5-04：提成结算批次、不可变结算明细、确认/支付/关闭状态机和职责分离。
