# M5-01 基线与契约验收记录

## 1. 验收范围

- 四份 V1.2 商业基线通过 WPS/PDF 文本与页面基线复核。
- `PRODUCT.md`、`DESIGN.md`、现有 M4 架构、迁移、权限、测试和白色主题实现完成盘点。
- 新增 M5 差距/影响、生命周期和 V3 API 受控补充契约。
- 新增自动化文档契约测试。
- M5-01 不新增表、不修改状态机、不注册 M5 业务路由、不启用新写路径。

## 2. 交付物

- `docs/baseline/M5-01-commission-settlement-aftersales-gap-and-impact.md`
- `docs/architecture/m5-commission-settlement-aftersales-lifecycle.md`
- `docs/architecture/api-contract-m5.md`
- `backend-next/src/test/java/com/face/platform/v3/api/M5ApiContractDocumentTest.java`

## 3. 失败测试证据

首次执行：

```text
mvn.cmd -q -Dtest=M5ApiContractDocumentTest test
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```

失败原因：测试要求的回滚措辞为“不执行 DROP”，而受控文档的实际明确规则为
“禁止直接 DROP”。断言收敛到正式规则后再次执行，退出码为 0。

## 4. 自动化测试和构建

### 4.1 后端

```text
mvn.cmd -q -Dtest=M5ApiContractDocumentTest test
exit code 0

mvn.cmd -q test
TESTS=107;FAILURES=0;ERRORS=0;SKIPPED=0;FILES=52

mvn.cmd -q -DskipTests package
exit code 0
```

### 4.2 前端

```text
admin-next: npm.cmd run build
1773 modules transformed; built successfully

front-next: npm.cmd run build
190 modules transformed; built successfully
```

## 5. MySQL 8 与历史业务链

执行：

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass \
  -File E:\face\scripts\verify-m1-v3-security.ps1 \
  -DatabasePort 3381 -BackendPort 8241 \
  -VerifyM4Ui -VerifyM4Refund -VerifyM4Purchase -VerifyM4Payment
```

结果：

- 全新 MySQL 8 实例迁移成功。
- `LATEST=2026073002;SUCCESS=1`
- M2 主数据/资源冲突：PASS
- M3 三端护理与库存事务链：PASS
- M4 套餐、账户、退款、采购批次、支付回调和对账：PASS
- M4 管理/技师/会员三端白色主题关键页面：PASS
- M1 V3 安全、租户/门店范围、刷新旋转和退出撤销：PASS
- 命令退出码：0

## 6. 数据库与连接结论

- M5-01 未新增 Flyway 迁移，当前最新版本保持 `2026073002`。
- 全新数据库可从基础结构完整迁移到当前版本。
- 数据库连接、readiness、租户门店范围和 M4 历史业务链均未出现回归。
- M5-02 才允许新增 Expand 迁移；必须先补规则版本失败测试和迁移契约测试。

## 7. 自检

1. 需求覆盖：规则版本、提成入账/冻结/冲正、结算、售后、审批和通知均有受控边界。
2. 权限：管理、财务、前台、技师 SELF 和会员 SELF 已列出，申请/审批职责分离。
3. 错误处理：幂等、版本、状态、规则冲突、结算冲突和依赖不可用均有契约。
4. 日志与隐私：禁止手机号、证件、健康信息、支付凭据和完整售后正文进入普通日志/outbox。
5. 测试：关键规则测试先行清单已冻结；文档契约测试通过。
6. 数据库：未无授权改表；迁移、兼容、回滚和核对方案完整。
7. 文档：仓库基线、生命周期、API 和验收记录已同步。

## 8. 结论

M5-01 验收通过。下一任务为 M5-02：先新增规则冲突、发布后不可变、金额确定性、
权限和迁移契约失败测试，再实现提成规则版本与来源快照。
