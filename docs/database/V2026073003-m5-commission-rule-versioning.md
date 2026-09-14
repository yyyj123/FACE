# V2026073003 M5 提成规则版本与来源快照迁移说明

## 1. 变更范围

迁移文件：`backend-next/src/main/resources/db/migration/V2026073003__m5_commission_rule_versioning.sql`

本迁移采用 Expand 策略，仅新增提成规则版本、规则范围、来源快照及权限数据，不修改订单、服务记录、退款、套餐、库存和既有财务流水。

### 新增表

- `commission_rule_version`：保存规则代码、不可变业务版本、计提方式、金额参数、生效区间、发布/退役事实和乐观锁版本。
- `commission_rule_scope`：保存规则适用的门店、角色、员工、服务、商品或套餐范围。
- `commission_source_snapshot`：保存服务/销售事实的金额、人员、发生时间、稳定 JSON 与 SHA-256 摘要，供后续入账和历史重算。

### 新增权限

- `commission:rule:view`
- `commission:rule:manage`
- `commission:entry:view`

授权遵循最小权限：

- `OWNER`、`MANAGER`：规则查看、规则管理、流水查看。
- `REGIONAL_MANAGER`、`FINANCE`：规则查看、流水查看。
- `BEAUTICIAN`：仅流水查看，且由应用层继续执行本人 SELF 范围校验。

## 2. 索引与约束

- `uk_commission_rule_version(tenant_id, rule_code, version_no)`：同租户同规则版本唯一。
- `idx_commission_rule_effective`：按门店、状态、来源和生效区间匹配规则。
- `idx_commission_rule_priority`：按来源、优先级和状态处理冲突。
- `uk_commission_rule_scope`：同规则版本的范围项不可重复。
- `idx_commission_scope_lookup`：按范围类型和值反查适用规则。
- `uk_commission_source_snapshot(tenant_id, source_type, source_id, staff_id)`：同一业务事实对同一员工只生成一份快照。
- `idx_commission_source_staff_time`：技师历史流水按时间查询。
- `idx_commission_source_hash`：按稳定摘要核验重放一致性。
- 数据库 CHECK 约束负责来源类型、计提方式、状态、金额非负、生效区间和发布字段完整性。
- 外键均限定在既有租户、门店、账号、员工和会员主数据内，不使用级联删除历史证据。

## 3. 兼容策略

- `/api/v1`、`/api/v2` 原有路由和表结构不变。
- 新表初始为空，不回填或猜测历史提成；历史来源必须由受控应用服务捕获快照。
- 已发布规则只允许退役，不允许覆盖更新；新业务变化创建下一业务版本。
- 来源快照同业务键同事实可重放，同键异事实返回冲突，不覆盖历史。
- `SELF` 角色可在其被分配的门店使用已授予权限，具体业务仍必须校验本人技师/会员身份；这修复了旧权限查询未纳入 `SELF` 的缺口，不扩大权限定义。

## 4. 回滚说明

1. 应用先回滚到不注册 M5 提成路由的版本，停止新建规则、发布和来源快照捕获。
2. 保留三个新增表为只读证据，不直接 DROP，不删除已发布规则和来源快照。
3. 若必须物理清理，先导出三个表、权限映射和 Flyway 历史，并确认尚未产生任何 M5-03 提成流水或结算引用；再由 DBA 在独立维护窗口执行回滚脚本。
4. 不通过修改 `flyway_schema_history`、覆盖发布规则或删除快照来回滚业务事实。

## 5. 验证结果

- MySQL 8 全新实例从基线成功执行 19 个迁移，最新版本为 `2026073003`。
- 三张表、三个权限、五个关键唯一/检查约束均存在。
- 规则创建幂等、发布后不可变、范围冲突、十进制计算和技师越权拒绝均通过运行时验证。
- M2 验收夹具的临时管理角色已精确撤销并断言零残留。
- M3 技师 SELF 开工、M4 套餐/账户历史链和 M1 安全链均通过回归。

