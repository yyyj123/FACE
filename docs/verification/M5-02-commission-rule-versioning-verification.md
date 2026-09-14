# M5-02 提成规则版本与来源快照验收记录

## 1. 任务输入、输出与验收标准

输入为四份 V1.2 商业基线、M5-01 生命周期/API 契约、现有权限与数据库迁移规范。

输出包括：

- 提成规则版本、适用范围、来源快照三张表；
- 规则草稿、更新、发布、退役、模拟和快照查询应用服务/API；
- 规则冲突、发布后不可变、十进制计算、稳定快照摘要、幂等、审计和 outbox；
- 最小权限授权及 `SELF` 门店权限修复；
- 新鲜数据库运行时验收脚本和迁移说明。

验收标准：测试先行；发布版本不可覆盖；规则范围冲突可拒绝；来源快照可稳定重放；技师不能查看或管理规则；迁移、历史链、后端测试和双前端构建全部通过。

## 2. 影响模块与迁移方案

- 模块：提成、权限、安全上下文、审计、outbox。
- 表：仅新增 `commission_rule_version`、`commission_rule_scope`、`commission_source_snapshot`。
- 接口：仅新增 `/api/v3/commission/*` 受控路由。
- 历史数据：不回填、不重写；M5-03 以后由业务事件捕获来源快照并生成提成流水。
- 权限：管理层维护规则，财务/区域只读，技师仅看本人后续流水。
- 回滚：先停写并回滚应用，保留新表只读；禁止删除发布规则和来源快照。

## 3. 测试驱动证据

首次执行以下测试均按预期失败：

```text
CommissionRulePolicyTest
M5CommissionRuleMigrationContractTest
M5CommissionRuleApiContractTest
CommissionSourceSnapshotPolicyTest
M5LeastPrivilegeVerificationContractTest
TenantAccessServiceTest#selfScopedRoleCanUseItsGrantedPermissionOnlyInsideAssignedShop
```

失败分别证明规则策略、迁移、API、快照摘要、临时授权清理和 SELF 门店权限尚未实现。实现后定向测试和全量测试均转绿。

## 4. 权限缺陷与修复

新鲜库首次联调发现 `jishi01` 同时拥有 `MANAGER` 和 `BEAUTICIAN` 权限。根因是 M2 验收脚本临时追加管理角色后没有清理，掩盖了后续权限边界。

修复内容：

- M2 完成后精确删除 `jishi01` 的临时 `MANAGER` 门店角色，并查询断言残留为零。
- `requireShopPermission` 纳入 `SELF` 范围，但仅允许其绑定门店；服务记录等模块继续执行“只能操作分配给自己的记录”校验。
- M5 联调打印并断言技师实际权限，确认不含 `commission:rule:view` 和 `commission:rule:manage`。

修复后的技师权限为：

```text
appointment:manage, appointment:view,
care_record:manage, care_record:view,
commission:entry:view, dashboard:view,
inventory:view, member:view,
package:view, package:writeoff,
resource:view, service_record:manage, service_record:view, service:view
```

## 5. 自动化测试与构建

### 后端

```text
mvn.cmd -q test
TESTS=115;FAILURES=0;ERRORS=0;SKIPPED=0;FILES=57

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

## 6. MySQL 8 新鲜库与历史链

执行：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass \
  -File E:\face\scripts\verify-m1-v3-security.ps1 \
  -DatabasePort 3395 -BackendPort 8255 \
  -VerifyM5CommissionRules
```

关键结果：

```text
M2_TEMP_MANAGER_ROLE_CLEANUP=PASS
M3_THREE_ROLE_SERVICE_FLOW=PASS
M4_PACKAGE_ISSUE_IDEMPOTENCY=PASS
M4_PACKAGE_WRITE_OFF_AND_REVERSAL=PASS
M5_RULE_CREATE_IDEMPOTENCY=PASS
M5_RULE_PUBLISHED_IMMUTABILITY=PASS
M5_RULE_SCOPE_CONFLICT=PASS
M5_RULE_DECIMAL_SIMULATION=PASS
M5_RULE_PERMISSION_SCOPE=PASS
M5_COMMISSION_RULE_VERSIONING=PASS
M5_COMMISSION_TABLES=3
M5_COMMISSION_PERMISSIONS=3
M5_COMMISSION_CONSTRAINTS=5
LATEST=2026073003;SUCCESS=1
M1_V3_SECURITY=PASS
```

命令退出码为 0，临时 MySQL 和后端端口均已清理。

## 7. 阶段快照

```text
E:\face\backups\m5\m5-02\face-m5-source-20260730-093955.zip
bytes=95612125
sha256=CF534E74020A697FBB3C8D18D378B7C81853BABB33140A907030EF69BE99BC6B
```

## 8. 每步自检

1. 需求覆盖：规则版本、范围、发布、退役、模拟和来源快照边界已实现。
2. 权限：管理、只读和技师 SELF 分离；已新增真实权限链验证。
3. 错误处理：参数、幂等、版本、状态和范围冲突返回明确错误。
4. 日志：审计/outbox 只记录业务标识和摘要，不记录手机号、健康信息或支付凭据。
5. 测试：规则、快照、权限、迁移及全量回归通过。
6. 数据库：迁移、索引、兼容与回滚说明已同步。
7. 文档：M5 API、数据库说明、产品阶段和验收记录同步。

## 9. 结论

M5-02 验收通过。下一任务为 M5-03：提成流水生成、冻结/解冻、退款冲正和历史重放。
