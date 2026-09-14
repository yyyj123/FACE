# SC1 数据复用与新增迁移方案

状态：Accepted for staged implementation

日期：2026-08-03

## 1. 强制原则

- `REUSE`：优先扩展现有主表和领域服务，不复制已经存在的会员、预约、订单、卡项、售后、通知或库存模型。
- `ADD_MIGRATION`：所有结构变化只通过新的 Flyway 版本追加。
- `NO_DUPLICATE_MODEL`：同一业务事实只能有一个写入所有者；旧字段可作为兼容投影，但不能与新模型并列成为事实源。
- `FLYWAY_APPEND_ONLY`：不得编辑 `V2026080204` 及以前的历史迁移、checksum 或 `flyway_schema_history`。
- 所有 backfill 必须可重复验证、可统计、遇到歧义即停止，不覆盖非空冲突数据。

## 2. 唯一事实源与模块所有权

| 业务事实 | 唯一事实源 | 兼容/投影 | 禁止事项 |
|---|---|---|---|
| 会员身份 | `member` + `member_shop_profile` | 旧 `member.shop_id` | 禁止新建 `customer` 主表 |
| 登录账号/会话 | `account` + `auth_session` | 兼容 `token` | 禁止用户端另建账号表 |
| 管理角色 | `role_definition` + `account_shop_role` | 历史角色保留 | 禁止用前端隐藏替代权限 |
| 预约 | `appointment` + item/history | 无 | 禁止商城或用户端另建预约表 |
| 时间锁 | 计划新增 `appointment_time_lock` | 无 | 禁止把未支付订单伪装为确认预约 |
| 订单 | `sales_order` + `sales_order_item` | 无 | 禁止另建 `commerce_order` 主表 |
| 支付/退款 | `payment_transaction` / `refund_transaction` | 对账为投影 | 禁止订单直接覆盖支付事实 |
| 卡主档/实例 | `package_product` / `package_instance` | `care_package` 冻结 | 禁止第三套卡表 |
| 次数权益 | `package_instance_item` + `package_ledger` | instance 余额为快照 | 禁止无流水改剩余次数 |
| 储值卡权益 | 计划新增批次/流水，挂到 `package_instance` | `member_account` 为汇总投影 | 禁止只覆盖总余额 |
| 积分 | 计划新增 `point_batch` + `point_ledger` | `member.points`、POINTS account 为兼容投影 | 禁止继续直接加减 `member.points` |
| 售后 | `after_sale_case` + log | approval/refund 为关联事实 | 禁止服务/商城各建工单主表 |
| 营销投递 | `marketing_campaign` 系列表 | notification 为交付结果 | 禁止把它当订单价格活动 |
| 优惠活动/券 | 计划新增 `promotion_activity`、`coupon_*` | 无 | 禁止复用消息投递状态机 |
| 站内通知 | `notification_message` | outbox 是输入事件 | 禁止外部渠道伪造 DELIVERED |
| SKU 库存 | `product` + `stock_balance` + `inventory_movement` | `product.stock_quantity` 为兼容投影 | 禁止新建第二套 SKU 库存余额 |

## 3. 已识别重复模型的关闭方案

### 3.1 `member.points`、POINTS account 与新积分批次

1. SC5 前禁止新增 `member.points` 写路径。
2. 新建 `point_batch`、`point_ledger`，余额由未到期批次汇总。
3. backfill 以当前 POINTS account 为优先；若不存在则读取 `member.points`。两者非零且不一致时写入迁移异常表并停止该会员，不自动取较大值。
4. 完成后 POINTS account 和 `member.points` 只由积分模块在同一事务末尾更新为兼容投影。

### 3.2 `care_package` 与 `package_product`

- `package_product/package_instance` 是唯一新卡主线。
- `care_package` 不再写入；有真实历史数据时，SC7 通过来源映射转换为 `LEGACY_IMPORT` 的独立 package product/instance。
- 不直接改名、不删除旧表，避免破坏历史页面和证据。

### 3.3 `product.stock_quantity` 与 `stock_balance`

- `stock_balance.quantity_on_hand/quantity_reserved` 为强一致余额，`inventory_movement` 为追加事实。
- `product.stock_quantity` 只作为现有页面的汇总投影，由库存模块统一同步。
- 商城多规格不新建 SKU 主表：新增 `commerce_product` 作为 SPU，并让现有 `product` 行关联 SPU；一行 `product` 即一个 SKU。

### 3.4 用户端与管理端预约双写路径

- `AppointmentService` 演进为唯一预约应用服务。
- `ClientPortalService` 只做本人身份适配和 DTO 映射，不得直接 INSERT/UPDATE `appointment`。
- `ClientCatalogService` 只调用统一 availability 端口，不复制时段算法。

### 3.5 跨模块 SQL

- 允许同库外键和只读查询在过渡期存在，但新增写操作必须通过明确端口。
- 首批端口：`MemberAccountProvisioningPort`、`AppointmentAvailabilityPort`、`BenefitReservationPort`、`InventoryReservationPort`、`PaymentPort`、`AfterSaleResolutionPort`、`NotificationPort`。
- 模块所有者在自己的 `@Transactional` 应用服务内写表；跨模块长流程通过 outbox/补偿，不用一个控制器直接写多域表。

## 4. 计划迁移序列

以下是计划新增路径，不代表 SC1 已创建这些 SQL。实际编码前仍须再次检查迁移目录最大版本。

### SC2

计划新增：`backend-next/src/main/resources/db/migration/V2026080301__sc2_identity_content_single_shop.sql`

- 追加 `ADMIN`、`SUPER_ADMIN` 角色及权限映射，保留历史角色但不再用于新账号。
- 新增短信/DEMO 验证挑战、管理员安全设置、单店配置和风险阈值表。
- 扩展首页内容的草稿/定时发布/下线信息；现有 `banner` 数据 backfill 为已发布内容。
- 会员手机号唯一性提升为租户维度；发现重复手机号时迁移失败并输出冲突，不自动合并。

### SC3

计划新增：`backend-next/src/main/resources/db/migration/V2026080302__sc3_appointment_hold_waitlist_terms.sql`

- 给 `service_item` 追加预约间隔、缓冲、最短提前、当天预约、取消/改期规则版本字段。
- 新增 `appointment_term_version`、`appointment_term_snapshot`。
- 新增 `appointment_time_lock`、`appointment_waitlist`、`appointment_waitlist_attempt`。
- 继续复用 `appointment`、`appointment_item`、`staff_schedule`、`resource_booking`。

### SC4

计划新增：`backend-next/src/main/resources/db/migration/V2026080303__sc4_order_cards_coupon_benefits.sql`

- 扩展 `package_product/package_instance` 的卡类型和来源；次数卡继续复用现有 item/ledger。
- 新增储值本金/赠送金批次与流水、折扣卡规则/适用范围。
- 新增 `coupon_template`、`member_coupon`、`coupon_ledger`。
- 新增订单优惠决策与权益冻结附属表。
- 调整 `payment_transaction` 以显式支持 `ZERO_AMOUNT`，不删除历史正金额约束事实；迁移需用新约束替换并加支付类型校验。

### SC5

计划新增：`backend-next/src/main/resources/db/migration/V2026080304__sc5_points_commerce_fulfillment.sql`

- 新增积分账户参数、批次、流水、规则、任务、签到和到期通知计划。
- 新增 `commerce_product` SPU，现有 `product` 作为 SKU 关联；复用 stock balance/movement。
- 新增购物车、收货地址、运费模板、订单拆分关系、包裹和包裹-订单关系。
- 扩展 `sales_order` 为服务/卡项/商城共用订单，并以父订单字段支持拆单；不新建平行订单主表。

### SC6

计划新增：`backend-next/src/main/resources/db/migration/V2026080305__sc6_fulfillment_review_aftersale.sql`

- 扩展 `customer_confirmation` 的截止时间、自动确认来源和最终核销关联。
- 新增评价版本、审核、回复和逻辑删除历史；旧 `review` backfill 为第一个版本。
- 扩展售后 SLA、处理方案、证据、顾客确认、一次重开计数和商品退货验收。

### SC7

计划新增：`backend-next/src/main/resources/db/migration/V2026080306__sc7_legacy_import_batches.sql`

- 新增导入批次、文件哈希、行预检、冲突、执行结果和来源映射。
- 为历史卡建立 `source_system + original_card_no` 唯一约束。
- 余额/次数导入只通过 `LEGACY_IMPORT` 流水，不直接覆盖余额。

### SC8-SC9

- 不计划以生产 Flyway 写入固定 DEMO 账号或演示数据。演示数据通过隔离 profile 的重置脚本加载，避免生产误用。
- 部署、Secret、备份、监控和渠道启用属于配置/运维资产，不另建业务主表；若需渠道配置，只保存密文引用和掩码元数据。

### SC10

计划新增：`backend-next/src/main/resources/db/migration/V2026080307__sc10_wechat_identity_binding.sql`

- 新增微信身份绑定和订阅授权引用，关联现有 `account/member`。
- 不复制会员资产、预约、订单或状态机。

## 5. 数据 backfill 与核对

每个迁移必须输出并由测试断言：源行数、目标行数、冲突数、跳过数、重复键数和余额汇总。资金、积分、卡次和库存必须同时核对总量与逐账户/逐 SKU 余额。

迁移失败策略：

- 唯一身份冲突：停止该记录或整批，不自动合并。
- 资金/积分/次数不一致：进入冲突表，禁止选择较大值或覆盖现值。
- 缺少外键对象：隔离并报告，不创建空白会员、空订单或空卡。
- 历史迁移 checksum 变化：立即失败，不执行 repair 掩盖差异。

## 6. 编码前门禁

后续阶段开始编码前必须满足：

1. 迁移文件版本未占用且高于当前最大版本。
2. 新表有明确模块所有者、唯一键、租户范围、版本/流水和删除策略。
3. 变更没有引入第二套主表或余额表。
4. 跨模块写入已通过端口或 outbox 设计，不新增直接 SQL 写别域表。
5. backfill 测试先失败，再实现迁移；`migrate + validate + flyway_schema_history` 全部通过。
