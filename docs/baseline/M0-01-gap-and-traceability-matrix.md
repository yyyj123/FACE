# M0-01 差异与追踪矩阵

基线日期：2026-07-28  
目的：冻结“需求—接口—表—测试—阶段”关系，防止将局部实现标记为阶段完成。

## 1. 阶段追踪

| 阶段 | 核心需求 | 当前可复用 | 主要缺口 | M0 结论 |
|---|---|---|---|---|
| M0 | 备份、恢复、差异矩阵、术语、版本、虚构数据 | 四份 V1.1、Flyway、已有隔离迁移脚本 | 正式备份恢复演练与回滚资产 | 本任务完成 |
| M1 | 权限范围、完成收口、V3 基线、连接健康 | `TenantAccessService`、完成门禁、readiness、M1 表结构 | V3 业务契约、会话撤销、request_id、稳定错误码、访问审计消费、outbox 发布 | 未验收 |
| M2 | 组织、人员、项目、技能、房间设备、排班冲突 | 员工、任职、项目、技能、排班、技师冲突 | 房间/设备、资源占用、技能版本、并发集成测试 | 未验收 |
| M3 | 三端预约—到店—护理—耗材—库存—单次收款 | 管理/顾客页面、护理完成事务、库存流水、基础收款 | 技师完整工作台、客户确认、完成后更正、故障注入与三端 E2E | 未验收 |
| M4 | 套餐、资产、支付、退款、采购批次、对账 | 账户/流水基础、支付/退款基础、库存调拨 | 套餐权益、支付适配、采购/批次、完整冲正、日终对账 | 未验收 |
| M5 | 提成、售后、审批、通知 | 退款审核和审计基础 | 规则版本、冻结/冲正、售后工单、通用审批、通知 | 未验收 |
| M6 | 报表、营销、培训、开放平台、安全灾备 | M6-01 至 M6-04 只读经营分析 | 报表快照、导出审计、营销、培训、集成客户端、容量/安全/灾备 | 未验收 |

## 2. V1.1 目标表差距

以下 19 张分阶段目标表当前未出现在受控 Flyway、`backend-next`、`admin-next` 或 `front-next` 实现中：

| 阶段 | 目标表 |
|---|---|
| M2 | `service_resource`、`resource_booking`、`staff_skill_version` |
| M3 | `customer_confirmation`、`service_record_correction` |
| M4 | `package_product`、`package_instance`、`package_ledger`、`purchase_order`、`stock_batch`、`reconciliation_batch` |
| M5 | `commission_rule_version`、`commission_entry`、`after_sale_case`、`approval_instance` |
| M6 | `report_snapshot`、`marketing_campaign`、`training_record`、`integration_client` |

缺失表不得提前空建；必须在所属阶段随用例、约束、索引、迁移和测试一起交付。

## 3. API 差距

- V1.1 API 文件列出的 M1-M3 商用路由共 31 项。
- 当前同路径 `/api/v3` M1-M3 路由为 0 项。
- 当前 `/api/v3` 只有经营分析 2 个只读路由。
- 当前主要业务仍通过 54 个 `/api/v2` 路由运行，可作为 V3 应用服务复用层。

未完成的通用契约包括：

- `request_id` 和 `timestamp` envelope。
- 稳定字符串业务错误码。
- `Idempotency-Key` 请求头消费和请求指纹。
- `If-Match` 或统一 `version` 冲突契约。
- 金额十进制字符串。
- 带时区 ISO-8601 时间。
- V2/V3 兼容契约测试。

## 4. 当前测试差距

当前正式后端测试覆盖策略与少量查询，共 7 个测试类。仍需按阶段补齐：

- M1：跨租户/跨门店/SELF 授权、会话撤销、V3 契约、幂等指纹。
- M2：MySQL 8 并发双订、房间和设备冲突。
- M3：护理完成故障注入、库存原子性、三端 E2E。
- M4：套餐核销、支付回调重放、退款全资产冲正、对账。
- M5：提成规则版本、冻结、重算和退款冲正。
- M6：容量、安全、恢复时间和开放平台契约。

## 5. 关键冲突冻结

| 冲突 | 保守决策 | 后续阶段 |
|---|---|---|
| `PRODUCT.md` 将套餐/提成写成整体非目标 | 改为 M0-M3 延期，M4/M5 实现 | M0 已同步 |
| 最新迁移引用 `REGIONAL_MANAGER`，角色种子缺失 | 规范代码冻结为 `REGIONAL_MANAGER`，不在 M0 修改权限表 | M1 |
| V1.1 护理作废为 `VOIDED`，V2 物理值为 `VOID` | V3 适配映射，不改历史状态 | M1/V3 |
| V1.1 调拨状态比 V2 完整 | Expand 扩展，不覆盖历史调拨 | M4 |
| 技师页面显示通用“完成服务”，后端禁止该入口 | 技师完成必须进入护理完成用例 | M3 |
| M6 只读分析早于 M5 存在 | 只保留可靠销售事实，不扩展利润/提成/套餐指标 | M6 |

## 6. 验收证据索引

| 证据 | 路径 |
|---|---|
| 基线冻结 | `docs/baseline/M0-01-baseline-freeze.md` |
| 数据备份恢复 Runbook | `docs/runbooks/M0-database-backup-restore.md` |
| M0 验证记录 | `docs/verification/M0-01-baseline-and-recovery-verification.md` |
| 文档一致性脚本 | `scripts/verify-m0-document-consistency.ps1` |
| 数据库恢复脚本 | `scripts/verify-m0-backup-restore.ps1` |
| 代码快照脚本 | `scripts/create-m0-source-snapshot.ps1` |
