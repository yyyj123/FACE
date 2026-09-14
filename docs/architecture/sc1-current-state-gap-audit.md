# SC1 当前实现与 V3.0 真实差距审计

状态：SC1 事实基线

日期：2026-08-03

基线：`sc0/v1.0.0` / `b707f5c72e9c3e1dc50c1a6b73950ef7c87993f5`

## 1. 审计方法与证据等级

本报告只把当前仓库中的 SQL、Java、Vue、测试和路由视为实现证据。历史 PRD 或架构说明只能解释意图，不能证明能力存在。

- **A 级证据**：表/迁移、写服务、接口和回归测试能够形成闭环。
- **B 级证据**：至少有表与服务/API，但页面、状态、权限或测试不完整。
- **C 级证据**：只有表、页面壳、接口声明或历史文档，不能形成闭环。
- **缺失**：迁移和生产代码中均不存在对应模型。
- **冻结**：代码存在，但 V3.0 已明确停止新增交付。

当前后端不是 JPA 实体模型。`backend-next` 主要以 `JdbcTemplate + Map<String,Object> + request record + policy class` 实现；因此下文的 **ENTITIES** 指真实 Java 请求记录、策略类和投影结构，不虚构不存在的持久化实体。

## 2. 总结结论

| 领域 | 证据 | 可直接复用 | V3.0 主要差距 | 结论 |
|---|---|---|---|---|
| MEMBER | B | 租户内会员、门店档案、账户、账号会话 | 手机验证码/找回、两管理角色、风险阈值、积分重复字段 | 部分实现 |
| APPOINTMENT | B | 排班、服务技能、预约、冲突校验、状态历史 | 时间锁、候补、条款快照、30 天/7 天窗口、不指定技师 | 部分实现 |
| ORDER | B | 订单/明细、支付/退款、幂等、适配器、对账骨架 | 零元支付、优惠决策、订单超时、商城拆单/包裹 | 部分实现 |
| PACKAGE | B | 次数组合套餐产品、实例、项目余额、不可变流水 | 三卡类型、冻结权益、储值批次、折扣规则、来源 | 仅次数组合卡基础 |
| AFTERSALE | B | 工单、日志、退款关联、状态策略、页面 | 7 天门禁、48 小时确认、一次重开、阈值、实物验货 | 部分实现 |
| MARKETING | B | 营销投递活动、同意、受众快照、站内投递 | 护理/商城优惠券、价格活动、内容发布状态 | 投递治理可复用，优惠缺失 |
| NOTIFICATION | B | outbox 投影、站内消息、已读、幂等投影 | 模板版本、失败原因、优先级、短信/微信适配器 | 站内基础可复用 |
| INVENTORY | B | 地点余额、冻结量、流水、锁、调整 | 商城多 SKU、退回待检/报损、预警流程；调拨/采购应冻结 | 基础库存可复用 |

不存在可以跳过增量设计直接验收的领域。SC2-SC6 必须在复用现有表的前提下补齐，而不是新建第二套会员、预约、订单、套餐、售后、营销、通知或库存主模型。

## 3. [MEMBER] 会员、账号与权限

### TABLES

- 主档：`member`、`member_shop_profile`。
- 登录与授权：`account`、`auth_session`、兼容 `token`、`role_definition`、`permission_definition`、`role_permission`、`account_shop_role`。
- 资产摘要：`member_account`、`member_account_ledger`。
- 关键来源：`docs/database/face_salon_mysql8.sql`、`V2026072401__chain_foundation.sql`、`V2026072402__member_center_foundation.sql`、`V2026072405__member_account_backfill.sql`、`V2026072802__v3_auth_session_foundation.sql`。

### ENTITIES / SERVICES

- `MemberCreateRequest`、`MemberUpdateRequest`、`MemberStatusRequest`。
- `MemberService`：租户内手机号去重、门店关联、资料更新、状态、账户初始化和审计。
- `ClientAuthService`：会员注册、账号密码登录和兼容 token。
- `V3AuthSessionService`：V3 access/refresh session。
- `TenantAccessService`：角色、权限、门店范围、会员本人/员工本人解析。

### APIS

- `/api/v2/members`：列表、详情、创建、更新、停用、恢复。
- `/api/v2/client/auth/register`、`/api/v2/client/auth/login`。
- `/api/v3/auth/login`、`refresh`、`logout`。
- `/api/v2/client/me`、`dashboard`。

### PERMISSIONS

- 当前管理权限：`member:view`、`member:manage`。
- 本人权限通过 `TenantAccessService.requireMemberId` 和 token 绑定的 `account.member_id` 实现。
- 当前角色仍为 `OWNER/REGIONAL_MANAGER/MANAGER/FRONT_DESK/BEAUTICIAN/WAREHOUSE/FINANCE/MEMBER`，与 V3.0 只保留 `ADMIN/SUPER_ADMIN` 不一致。

### PAGES

- 管理端：`admin-next/src/views/MembersView.vue`。
- 用户端：`LoginView.vue`、`ProfileView.vue`、`BenefitsView.vue`。

### EVIDENCE 与差距

- 已实现：同租户手机号复用已有会员、会员门店档案、密码登录、V3 session、资料乐观锁和审计。
- 缺失：短信验证码注册/登录、验证码找回密码、DEMO/生产验证码隔离、管理员账号管理、风险阈值累计判断。
- 冲突 1：`member.points` 与 `member_account(account_type='POINTS')` 同时表达积分余额；V3.0 又要求批次+流水，不能继续双写。
- 冲突 2：`MemberService` 直接写 `member_account`，会员生命周期和资产账户所有权没有端口隔离。
- 决策：`member` 是唯一会员主档；`member.points` 冻结为兼容投影；SC5 新积分批次上线后只由积分模块写，会员模块通过账户初始化端口协作。

## 4. [APPOINTMENT] 预约、排班与候补

### TABLES

- `staff`、`staff_shop_assignment`、`staff_service`、`staff_skill_version`、`staff_schedule`。
- `service_item`、`shop_service_price`、`service_resource`、`resource_booking`。
- `appointment`、`appointment_item`、`appointment_status_history`。
- 当前不存在：时间锁表、候补表、预约条款版本/快照表。

### ENTITIES / SERVICES

- `AppointmentCreateRequest`、`AppointmentRescheduleRequest`、`AppointmentStatusRequest`、`AppointmentStatusPolicy`。
- `AppointmentService`：管理侧查询、创建、改期、状态流转、排班/技能/冲突校验。
- `AppointmentLifecycleService`：预约状态历史和履约联动。
- `ClientCatalogService`：用户端技师与日期可用性查询。
- `ClientPortalService`：用户端预约创建、取消和本人查询。
- `StaffScheduleService`、`ResourceBookingService`：排班与房间/设备资源。

### APIS

- 管理端 `/api/v2/appointments`：列表、资源、availability、创建、改期、状态。
- 用户端 `/api/v2/client/public/availability`。
- 用户端 `/api/v2/client/appointments`：本人列表、创建、取消和冻结技师兼容状态操作。

### PERMISSIONS

- 管理权限：`appointment:view`、`appointment:manage`。
- 用户本人范围由 `requireMemberId` 绑定；冻结技师兼容逻辑仍使用 `BEAUTICIAN`。

### PAGES

- 管理端：`AppointmentsView.vue`、`MasterDataView.vue`。
- 用户端：`BookingView.vue`、`AppointmentsView.vue`、`ServiceDetailView.vue`。

### EVIDENCE 与差距

- 已实现：员工排班、项目技能、时间重叠检查、行锁、乐观锁、预约状态历史和本人范围。
- 当前状态：`PENDING → CONFIRMED/CANCELLED → CHECKED_IN/NO_SHOW → IN_SERVICE → COMPLETED`，与 V3.0 的支付前时间锁及 `ARRIVED/PENDING_CUSTOMER_CONFIRMATION` 不一致。
- 缺失：项目级开始间隔/前后缓冲/最短提前/当天预约/改期次数；未来 30 天与 7 天分页；不指定技师的低负载轮询；支付前完整区间锁；15 分钟候补；条款版本、确认和截止时间快照。
- 冲突：`ClientCatalogService` 与 `ClientPortalService` 直接读取/写入预约和排班表，与 `AppointmentService` 形成第二条规则路径。
- 决策：继续使用 `appointment` 作为唯一预约；新增时间锁、候补和条款附属表；SC3 先建立统一 `AppointmentApplicationService`/端口，再让管理端和用户端共同调用。

## 5. [ORDER] 订单、支付与退款

### TABLES

- `sales_order`、`sales_order_item`。
- `payment_transaction`、`payment_callback_event`、`refund_transaction`。
- `idempotency_record`、`outbox_event`。
- 对账骨架：`reconciliation_batch`、`reconciliation_item`、`reconciliation_resolution`。

### ENTITIES / SERVICES

- `OrderCreateRequest`、`OrderItemRequest`、`PaymentRequest`、`Refund*Request`。
- `TransactionService`、`PaymentApplicationService`、`RefundApplicationService`。
- `TransactionStatusPolicy`、`RefundExecutionPolicy`。
- `PaymentChannelAdapter`、`PaymentAdapterRegistry`、`SandboxPaymentChannelAdapter`、`PaymentCallbackVerifier`。
- `CommandIdempotencyService`、`OutboxEventService`。

### APIS

- `/api/v2/transactions`：查询/资源、创建订单、支付、退款、作废。
- `/api/v3/payments/{id}`、`/api/v3/payments/{id}/refunds`。
- `/api/v3/refunds/{id}`：审批、执行、详情和会员退款列表。
- `/api/v3/payment-callbacks/{channel}`。

### PERMISSIONS

- `order:view`、`order:manage`、`refund:request`、`refund:approve`、`refund:execute`。
- 关键 V3 写接口使用 `Idempotency-Key`；V2 DTO 中部分幂等键位于请求体，尚未统一。

### PAGES

- 管理端：`TransactionsView.vue`、`ReconciliationView.vue`。
- 用户端：没有独立“我的订单/支付”页面；现有预约和资产页只能看到部分关联事实。

### EVIDENCE 与差距

- 已实现：订单快照、正数支付、支付适配器注册、回调去重、退款保留额/审批/执行、对账骨架、资金状态和审计。
- 缺失：`ZERO_AMOUNT` 支付（现有 `payment_transaction.amount > 0`）、支付超时任务、生产渠道配置页、护理优惠四选一、优惠/券/积分决策快照、商城母子单、包裹和运费。
- 状态差异：订单仅 `UNPAID/PARTIALLY_PAID/PAID/PARTIALLY_REFUNDED/REFUNDED/VOID`；尚无 V3.0 商城履约与关闭原因维度。
- 冲突：`TransactionService` 直接读写会员账户并读取预约、服务和库存表，跨域事务集中在一个类中。
- 决策：`sales_order`/`sales_order_item` 是唯一订单主模型；通过附属决策、履约和包裹表扩展，不新建平行 `commerce_order`。跨域资产操作改为端口调用。

## 6. [PACKAGE] 套餐与会员资产

### TABLES

- 当前主线：`package_product`、`package_product_item`、`package_instance`、`package_instance_item`、`package_ledger`。
- 通用资产摘要：`member_account`、`member_account_ledger`。
- 旧表：`care_package`，只含展示型套餐字段，已被新套餐生命周期取代。

### ENTITIES / SERVICES

- `PackageCatalogService`、`PackageAccountService`、`MemberAccountApplicationService`。
- `PackageLifecyclePolicy`、`MemberAccountPolicy`。
- `TransactionPackageContextService` 用于校验套餐来源订单。

### APIS

- `/api/v3/package-products`：查询、创建、修改。
- `/api/v3/members/{memberId}/packages`：查询、发放。
- `/api/v3/package-instances/{id}/ledger|write-offs|freeze|unfreeze`。
- `/api/v3/member-accounts/*`：账户、流水、入账、扣减、冻结和冲正。

### PERMISSIONS

- `package:view/manage/writeoff/reverse`。
- `account:view/manage`。

### PAGES

- 管理端：`AssetsView.vue`。
- 用户端：`BenefitsView.vue`；冻结技师端在服务页仍有核销兼容入口，不属于 V3.0 当前产品。

### EVIDENCE 与差距

- 已实现：按项目次数套餐、购买来源订单校验、实例余额、核销/冲正、幂等、乐观锁和不可变流水。
- 缺失：`COMBO_TIMES/STORED_VALUE/DISCOUNT` 显式卡类型；预约冻结次数；本金/赠送金批次；折扣适用范围和封顶；五种发卡来源；历史卡唯一键。
- 冲突：旧 `care_package` 与当前 `package_product` 重复；`member_account` 的 BALANCE/GIFT_BALANCE 聚合不足以表达一人多张储值卡及原批次退款。
- 决策：冻结 `care_package`；扩展 `package_product/package_instance` 为三类卡的共同壳。次数继续复用现有 item/ledger；储值和折扣采用类型附属表，不创建第二套卡主表。

## 7. [AFTERSALE] 售后与顾客确认

### TABLES

- `after_sale_case`、`after_sale_case_log`。
- `approval_instance`、`approval_step`。
- `refund_transaction`。
- 履约确认：`customer_confirmation`。

### ENTITIES / SERVICES

- `AfterSaleApplicationService`、`AfterSalePolicy`、`AfterSaleNotificationResolver`。
- `CustomerConfirmationService`。
- `ApprovalApplicationService`、`RefundApplicationService` 为关联能力。

### APIS

- `/api/v3/after-sales/cases`：列表、创建、详情、动作、退款、重开。
- `/api/v2/client/confirmations`：本人待确认与确认/拒绝。
- 用户端也调用 `/api/v3/after-sales/cases` 创建和重开。

### PERMISSIONS

- `aftersale:view/create/manage`、`approval:view/decide`、`refund:request/approve/execute`。
- 会员本人通过 token 范围访问；当前 V3 售后创建接口仍允许管理权限和本人逻辑共存。

### PAGES

- 管理端：`OperationsView.vue` 中的售后与审批区域。
- 用户端：`AfterSalesView.vue`，确认入口位于 `AppointmentsView.vue`/个人流程。

### EVIDENCE 与差距

- 已实现：追加工单日志、乐观锁、幂等、退款关联、受控状态流转、用户拒绝护理结果。
- 缺失：服务完成后 24 小时自动确认；服务完成后 7 天申请门禁；结果提交后 48 小时顾客确认；有效期内只允许重开一次；处理方案明细；凭证；风险阈值累计；商品退回待检和验货。
- 当前 `CustomerConfirmationService` 只把状态改为 `CONFIRMED/REJECTED`，未原子触发卡项/储值/券/积分最终核销或售后。
- 决策：`after_sale_case` 是服务和商品售后的唯一工单；新增附属 SLA、方案执行、证据和退货验收表，不创建 `service_aftersale`/`mall_aftersale` 平行主表。

## 8. [MARKETING] 营销、活动与优惠

### TABLES

- 投递治理：`marketing_campaign`、`marketing_campaign_status_history`、`marketing_campaign_audience`、`marketing_delivery_attempt`。
- 同意：`member_marketing_consent`、`member_marketing_consent_history`。
- 当前不存在护理优惠券、商城优惠券、用户券、券流水或价格活动规则表。

### ENTITIES / SERVICES

- `MarketingCampaignApplicationService`、`MarketingCampaignPolicy`。
- `MarketingConsentService`、`MarketingConsentPolicy`。
- `MemberMarketingAudienceQueryAdapter`、`MarketingNotificationDeliveryAdapter`。

### APIS

- `/api/v3/marketing/campaigns`：创建、更新、提交、审批、执行、取消、受众和投递。
- `/api/v3/me/marketing-consents`：本人查询与更新。

### PERMISSIONS

- `marketing:view/manage/approve/execute`。
- 审批要求创建/提交人与审批人职责分离；只有 `IN_APP` 可真实执行。

### PAGES

- 管理端：`MarketingView.vue`。
- 用户端：`BenefitsView.vue` 只展示同意与既有资产，不存在优惠券领取/使用页面。

### EVIDENCE 与差距

- 已实现：营销消息活动、同意历史、执行时受众快照、投递记录和站内渠道。
- 缺失：V3.0 四类护理券、商城券、用户领券/锁券/核销/返券、价格活动、补偿券、内容活动展示。
- 边界判断：当前 `marketing_campaign` 是“受众投递活动”，不是订单价格活动。后续可新增 `promotion_activity`/`coupon_*`，但不得把投递状态当作价格规则状态。

## 9. [NOTIFICATION] 通知中心

### TABLES

- `outbox_event`。
- `notification_projection_checkpoint`、`notification_message`。
- `marketing_delivery_attempt` 记录营销投递尝试，但不替代通知消息。

### ENTITIES / SERVICES

- `NotificationEvent`、`NotificationMessageDraft`。
- `NotificationProjectionService`、`NotificationProjectionJob`、`NotificationApplicationService`、`NotificationPolicy`。
- `NotificationEventDescriptorResolver` 及售后/审批/提成/结算解析器。

### APIS

- `/api/v3/notifications`：本人列表、单条已读、全部已读。

### PERMISSIONS

- `notification:view:self`；接收人以 `recipient_account_id` 强制本人范围。

### PAGES

- 用户端：`NotificationsView.vue`。
- 管理端：`OperationsView.vue` 中的本人通知区域。

### EVIDENCE 与差距

- 已实现：outbox 到站内消息的可重试投影、事件/收件人去重、已读状态、乐观锁和安全摘要。
- 缺失：模板版本、优先级、可查询失败原因、业务要求的候补/完成确认/积分到期/发货/替换券解析器、短信与微信适配器。
- 当前表约束将渠道锁定为 `IN_APP` 且投递直接记为 `DELIVERED`，符合旧 M5，但不能直接承载外部渠道尝试。
- 决策：`notification_message` 继续作为站内收件箱；外部渠道使用新增 delivery-attempt 附属表，不伪造站内消息状态。

## 10. [INVENTORY] 基础库存

### TABLES

- `product_category`、`product`。
- `stock_location`、`stock_balance`、`inventory_movement`。
- 冻结兼容：`inventory_transfer`、`inventory_transfer_item`、`stock_batch`、`stock_batch_movement`、采购相关表。

### ENTITIES / SERVICES

- `InventoryAdjustmentRequest`、`InventoryTransfer*Request`、`InventoryConsumptionLine`。
- `InventoryService`、`InventoryReceiptApplicationService`、`StockBatchFefoPolicy`。

### APIS

- `/api/v2/inventory`：库存、资源、流水、调拨、调整。
- `/api/v3/purchases` 和批次接口存在，但采购已在 SC0 冻结。

### PERMISSIONS

- `inventory:view`、`inventory:manage`；采购权限属于冻结范围。

### PAGES

- 当前保留：`InventoryView.vue`。
- 冻结：`PurchasesView.vue`；调拨页签位于库存页但不属于 V3.0 新交付。
- 用户端没有商城商品、SKU 或库存页面。

### EVIDENCE 与差距

- 已实现：地点余额、在手/冻结量、行锁、版本、追加流水、手工调整、订单/服务扣减和库存汇总。
- 缺失：多 SKU 商品展示模型、购物车结算冻结、发货扣减、退回待检、恢复/报损、活动库存和 V3.0 单 SKU 调整阈值。
- 冲突：`product.stock_quantity` 与 `stock_balance.quantity_on_hand` 同时保存余额；服务通过 `syncProductStock` 保持同步，存在双写漂移风险。
- 决策：`stock_balance` 是库存事实余额，`inventory_movement` 是追加事实；`product.stock_quantity` 只保留为兼容投影。现有 `product` 继续代表库存 SKU，新建商城 SPU 只关联它，不创建第二套 SKU 库存。

## 11. 页面覆盖结论

管理端已有会员、预约、交易、资产、售后/通知、营销和库存页面，但信息架构仍包含采购、提成、培训和复杂岗位能力。用户端已有服务、预约、资产、售后和通知页面，缺少独立订单、优惠券、积分任务、商城、地址、评价版本和账号安全页面。

SC1 不隐藏页面或改变路由。SC2-SC7 必须按 V3.0 领域逐步替换导航，不能用现有页面文件名推断功能已经完成。

## 12. 编码前必须关闭的问题

1. 确认积分唯一事实源：采用积分批次/流水，冻结 `member.points` 和 POINTS 聚合的写入职责。
2. 确认卡项唯一主模型：`package_product/package_instance`；`care_package` 永久冻结。
3. 确认库存唯一事实源：`stock_balance + inventory_movement`；`product.stock_quantity` 只读投影。
4. 统一预约写路径：用户端不得继续直接写 `appointment`，必须调用预约领域应用服务。
5. 统一订单主模型：扩展 `sales_order`，禁止另建商城订单主表。
6. 跨模块只通过应用端口编排；禁止新代码直接写其他模块拥有的表。
7. 所有新迁移只能追加，禁止修改上述历史 SQL 或 checksum。
