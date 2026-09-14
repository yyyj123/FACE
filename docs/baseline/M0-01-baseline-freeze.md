# M0-01 商用升级基线冻结

冻结日期：2026-07-28  
当前阶段：M0 基线与保护  
状态：已验收  
适用范围：`backend-next`、`admin-next`、`front-next`、`face_salon`

## 1. 基线权威顺序

发生冲突时按以下顺序处理：

1. 四份 V1.1 商用升级文档。
2. 本冻结记录中的兼容决策与物理映射。
3. `PRODUCT.md`、`DESIGN.md` 和 `docs/architecture`。
4. 当前代码、Flyway 和物理数据库。

当前实现与上级基线不一致时，不静默改变历史数据或旧接口；记录为后续阶段差距，并使用向前兼容迁移修复。

## 2. 工程与版本冻结

| 项目 | M0 冻结值 |
|---|---|
| 后端 | `backend-next`，Java 21，Spring Boot，模块化单体 |
| 管理端 | `admin-next`，Vue 3 |
| 顾客/技师端 | `front-next`，Vue 3，同工程按角色形成独立信息架构 |
| 旧系统 | `backend`、`admin`、`front` 仅作为迁移兼容源，不新增商用能力 |
| 数据库 | MySQL 8、`utf8mb4`、明确 `Asia/Shanghai` 会话时区 |
| 结构基线 | `docs/database/face_salon_mysql8.sql` + `backend-next` Flyway |
| 最新迁移 | `V2026072801__item_analytics_foundation.sql` |
| API | `/api/v1` 冻结，`/api/v2` 兼容运行，新商用契约使用 `/api/v3` |
| 主题 | 白色与浅灰产品界面，深玫瑰为主操作色 |

## 3. 术语冻结

| 业务术语 | 物理实现/兼容名 | 决策 |
|---|---|---|
| 会员/顾客 | `member` + `member_shop_profile` | 不新增重复 customer 主表 |
| 员工/技师 | `staff` + `staff_shop_assignment` | 账号与人员分离 |
| 服务项目 | `service_item` | 交易与服务记录保留快照 |
| 预约 | `appointment` | 完成只能由护理完成应用服务触发 |
| 护理/服务记录 | `service_record` | 不新增重复 service_order 主表 |
| 销售订单 | `sales_order` + `sales_order_item` | 资金流水独立 |
| 支付/退款 | `payment_transaction` + `refund_transaction` | 退款冲正，不覆盖原支付 |
| 库存 | `stock_balance` + `inventory_movement` | 余额与追加流水同事务 |
| 套餐权益 | M4 新增物理模型 | 现有展示型套餐不能冒充权益套餐 |
| 提成 | M5 新增物理模型 | 必须保存规则版本和冲正链 |

## 4. 角色与数据范围冻结

| 角色代码 | 名称 | 数据范围 | M0 说明 |
|---|---|---|---|
| `OWNER` | 品牌负责人 | `TENANT` | 已有物理定义 |
| `REGIONAL_MANAGER` | 区域经理 | `REGION` | V1.1 规范代码；当前角色定义缺失，列入 M1 差距 |
| `MANAGER` | 店长 | `SHOP` | 已有物理定义 |
| `FRONT_DESK` | 前台 | `SHOP` | 已有物理定义 |
| `BEAUTICIAN` | 技师 | `SELF` | 必须同时限制本人 `staff_id` |
| `WAREHOUSE` | 仓管 | `SHOP` | 已有物理定义 |
| `FINANCE` | 财务 | `TENANT` 或受控门店 | 当前为租户范围，后续按授权细化 |
| `MEMBER` | 会员 | `SELF` | 只能访问本人主体与本人业务记录 |

功能权限、租户、门店/区域和资源所有者必须在同一次授权决策中校验，前端隐藏不构成授权。

## 5. 状态冻结

### 5.1 预约

`PENDING → CONFIRMED → CHECKED_IN → IN_SERVICE → COMPLETED`

旁路终态：`CANCELLED`、`NO_SHOW`。取消和爽约必须记录原因；通用状态接口禁止写入 `COMPLETED`。

### 5.2 护理记录

V3 规范：`DRAFT → IN_PROGRESS → COMPLETED / VOIDED`。

当前 V2 物理值使用 `IN_PROGRESS / COMPLETED / VOID`。迁移期由 V3 适配层将 `VOID` 映射为 `VOIDED`，不得直接改写历史值。

### 5.3 订单

当前兼容状态：`UNPAID → PARTIALLY_PAID → PAID → PARTIALLY_REFUNDED / REFUNDED`，未收款订单可进入 `VOID`。

V3 对外状态采用文档定义；支付和退款保持独立不可变流水。

### 5.4 库存调拨

V3 规范：`DRAFT → SUBMITTED → APPROVED → IN_TRANSIT → RECEIVED`，另有 `REJECTED/CANCELLED`。

当前 V2 仅有 `PENDING/APPROVED/REJECTED`，不得标记为 M4 完成，后续通过 Expand 迁移扩展。

### 5.5 客户确认

`PENDING → CONFIRMED / REJECTED`。客户确认独立于预约和护理完成状态。

## 6. 数据库冻结

- 现有 Flyway 文件 checksum 不得修改。
- 只允许向前 Expand 迁移；删除、改名和收窄列不属于当前升级路径。
- 业务表必须携带租户范围，门店业务同时携带门店范围。
- 库存、资金、套餐、积分和提成使用不可变追加流水。
- M1 的 `data_access_log`、`outbox_event` 已有结构，但业务消费尚未完成，不能冒充已交付。
- 目标阶段表按 V1.1 分阶段新增，不一次性空建。

## 7. API 冻结

- `/api/v1`：冻结，只修复安全问题。
- `/api/v2`：继续兼容，作为应用服务复用层。
- `/api/v3`：新商用契约；统一 envelope、稳定业务错误码、`request_id`、金额字符串、带时区时间、Header 幂等和版本控制。
- 当前仅经营分析存在 V3 只读接口；M1-M3 V3 路由尚未通过验收。

## 8. 阶段边界

- M0 只做保护、冻结、测试数据、差异矩阵和恢复验证，不新增业务能力。
- M1-M3 不上线套餐权益、复杂积分、自动提成和多支付通道。
- M4 实现套餐、资产、支付适配、采购批次、退款和对账。
- M5 实现提成、售后、审批和通知。
- M6 在 M5 完成后建设完整运营、营销、培训、开放平台、性能、安全和灾备。
- 已完成的 M6-01 至 M6-04 只作为不写业务事实的只读分析切片保留，不代表 M6 阶段完成。

## 9. 变更控制

状态机、表结构、接口契约、权限、支付、套餐、库存或提成发生变化前，必须：

1. 建立任务编号和验收标准。
2. 列出影响模块、表、接口、权限、状态和历史数据。
3. 先补关键规则失败测试。
4. 提供 Flyway、索引、兼容、核对和回滚说明。
5. 运行测试、构建、迁移和必要运行验证。
6. 同步四份基线和阶段验证记录。
