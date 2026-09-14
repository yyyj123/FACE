# SC1 后续阶段文件级实施计划

日期：2026-08-03

适用范围：SC2-SC10

状态：SC1 设计基线；所有“计划新增”路径须在对应阶段获批后才可创建

## 使用约定

- `[现有]` 表示 SC1 已确认存在的真实路径；`[计划新增]` 表示后续阶段建议落点，不表示已经实现。
- 每阶段开始时必须重新核对 PRD、当前 Git HEAD、迁移目录最大版本和上阶段证据，计划文件名可在不改变语义的前提下调整。
- 每阶段只能修改本节列出的业务模块；若发现必须扩展范围，应停止并更新 ADR/计划，不能隐式越界。
- 所有新写路径遵守 ADR-001：单一事实源、应用端口、单事务拥有者、固定锁顺序、幂等和 outbox。
- 每阶段完成后在 `docs/verification/<阶段>/` 留证、提交并停止，等待人工明确放行。

## SC2：身份、单店化与内容基础

目标：在不复制会员账户的前提下完成 V3 统一登录、短信验证/找回、两个运营角色、单店呈现和内容发布基础。

后端与数据库：

- `[现有] backend-next/src/main/java/com/face/platform/client/AuthController.java`
- `[现有] backend-next/src/main/java/com/face/platform/client/ClientAuthService.java`
- `[现有] backend-next/src/main/java/com/face/platform/v3/auth/V3AuthController.java`
- `[现有] backend-next/src/main/java/com/face/platform/v3/auth/V3AuthSessionService.java`
- `[现有] backend-next/src/main/java/com/face/platform/security/TenantAccessService.java`
- `[现有] backend-next/src/main/java/com/face/platform/member/MemberService.java`
- `[现有] backend-next/src/main/java/com/face/platform/masterdata/MasterDataQueryService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/identity/IdentityApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/identity/SmsVerificationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/content/ContentApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/v3/content/V3ContentController.java`
- `[计划新增] backend-next/src/main/resources/db/migration/V2026080301__sc2_identity_content_single_shop.sql`

前端与验证：

- `[现有] front-next/src/views/LoginView.vue`
- `[现有] front-next/src/views/HomeView.vue`
- `[现有] front-next/src/router/index.ts`
- `[现有] admin-next/src/components/AppShell.vue`
- `[现有] admin-next/src/router/index.ts`
- `[计划新增] admin-next/src/views/ContentView.vue`
- `[计划新增] admin-next/src/views/SettingsView.vue`
- `[计划新增] backend-next/src/test/java/com/face/platform/identity/IdentityApplicationServiceTest.java`
- `[计划新增] backend-next/src/test/java/com/face/platform/content/Sc2ContentMigrationContractTest.java`
- `[计划新增] scripts/verify-sc2-identity-content.ps1`

边界：只建立身份、角色、单店显示和内容底座；不进入预约时间锁、订单优惠或电商。

## SC3：预约时间锁、候补与条款快照

目标：复用现有预约、排班、项目和资源表，增加可过期时间锁、候补队列和预约条款快照；先收口用户端绕过预约模块的直接写入。

后端与数据库：

- `[现有] backend-next/src/main/java/com/face/platform/appointment/AppointmentService.java`
- `[现有] backend-next/src/main/java/com/face/platform/appointment/AppointmentLifecycleService.java`
- `[现有] backend-next/src/main/java/com/face/platform/appointment/AppointmentStatusPolicy.java`
- `[现有] backend-next/src/main/java/com/face/platform/appointment/AppointmentController.java`
- `[现有] backend-next/src/main/java/com/face/platform/client/ClientCatalogService.java`
- `[现有] backend-next/src/main/java/com/face/platform/client/ClientPortalService.java`
- `[现有] backend-next/src/main/java/com/face/platform/masterdata/StaffScheduleService.java`
- `[现有] backend-next/src/main/java/com/face/platform/resource/ResourceBookingService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/appointment/AppointmentApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/appointment/AppointmentSlotHoldService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/appointment/AppointmentWaitlistService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/v3/appointment/V3AppointmentController.java`
- `[计划新增] backend-next/src/main/resources/db/migration/V2026080302__sc3_appointment_hold_waitlist_terms.sql`

前端与验证：

- `[现有] front-next/src/views/BookingView.vue`
- `[现有] front-next/src/views/AppointmentsView.vue`
- `[现有] front-next/src/views/ServiceDetailView.vue`
- `[现有] admin-next/src/views/AppointmentsView.vue`
- `[现有] admin-next/src/views/MasterDataView.vue`
- `[计划新增] backend-next/src/test/java/com/face/platform/appointment/AppointmentSlotHoldConcurrencyTest.java`
- `[计划新增] backend-next/src/test/java/com/face/platform/appointment/AppointmentWaitlistPolicyTest.java`
- `[计划新增] scripts/verify-sc3-appointment-flow.ps1`

边界：不创建支付订单，不处理套餐/优惠券预占；预约只输出后续结算所需的稳定上下文。

## SC4：服务订单、支付、套餐与优惠权益

目标：复用销售订单、支付退款、套餐实例和账户账本，加入零元订单、套餐类型、优惠券及权益预占/确认/释放。

后端与数据库：

- `[现有] backend-next/src/main/java/com/face/platform/transaction/TransactionService.java`
- `[现有] backend-next/src/main/java/com/face/platform/transaction/PaymentApplicationService.java`
- `[现有] backend-next/src/main/java/com/face/platform/transaction/RefundApplicationService.java`
- `[现有] backend-next/src/main/java/com/face/platform/transaction/TransactionPackageContextService.java`
- `[现有] backend-next/src/main/java/com/face/platform/packageaccount/PackageAccountService.java`
- `[现有] backend-next/src/main/java/com/face/platform/packageaccount/PackageCatalogService.java`
- `[现有] backend-next/src/main/java/com/face/platform/packageaccount/MemberAccountApplicationService.java`
- `[现有] backend-next/src/main/java/com/face/platform/payment/PaymentAdapterRegistry.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/transaction/CheckoutApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/benefit/BenefitReservationPort.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/coupon/CouponApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/v3/transaction/V3OrderController.java`
- `[计划新增] backend-next/src/main/resources/db/migration/V2026080303__sc4_order_cards_coupon_benefits.sql`

前端与验证：

- `[现有] admin-next/src/views/TransactionsView.vue`
- `[现有] admin-next/src/views/AssetsView.vue`
- `[现有] front-next/src/views/BenefitsView.vue`
- `[现有] front-next/src/views/BookingView.vue`
- `[计划新增] front-next/src/views/OrdersView.vue`
- `[计划新增] front-next/src/views/OrderDetailView.vue`
- `[计划新增] front-next/src/views/CouponsView.vue`
- `[计划新增] backend-next/src/test/java/com/face/platform/transaction/CheckoutAtomicityTest.java`
- `[计划新增] backend-next/src/test/java/com/face/platform/coupon/CouponReservationConcurrencyTest.java`
- `[计划新增] scripts/verify-sc4-order-payment-benefit.ps1`

边界：仅服务订单和虚拟权益；不进入商品购物车、物流履约和评价。

## SC5：积分账本、商品交易与履约起点

目标：把积分批次/到期/冲正建立为权威账本，并在现有 SKU 和库存账本之上增加商品 SPU、购物车、地址和商品订单。

后端与数据库：

- `[现有] backend-next/src/main/java/com/face/platform/inventory/InventoryService.java`
- `[现有] backend-next/src/main/java/com/face/platform/inventory/InventoryController.java`
- `[现有] backend-next/src/main/java/com/face/platform/outbox/OutboxEventService.java`
- `[现有] backend-next/src/main/java/com/face/platform/transaction/TransactionService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/points/PointsAccountApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/commerce/CommerceCatalogService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/commerce/CartApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/commerce/CommerceOrderApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/commerce/ShippingAddressService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/v3/commerce/V3CommerceController.java`
- `[计划新增] backend-next/src/main/resources/db/migration/V2026080304__sc5_points_commerce_fulfillment.sql`

前端与验证：

- `[现有] admin-next/src/views/InventoryView.vue`
- `[现有] front-next/src/views/BenefitsView.vue`
- `[计划新增] front-next/src/views/ShopView.vue`
- `[计划新增] front-next/src/views/CartView.vue`
- `[计划新增] front-next/src/views/CheckoutView.vue`
- `[计划新增] front-next/src/views/AddressesView.vue`
- `[计划新增] admin-next/src/views/CommerceOrdersView.vue`
- `[计划新增] admin-next/src/views/PointsView.vue`
- `[计划新增] backend-next/src/test/java/com/face/platform/points/PointsBatchConsumptionTest.java`
- `[计划新增] backend-next/src/test/java/com/face/platform/commerce/CommerceOrderInventoryConcurrencyTest.java`
- `[计划新增] scripts/verify-sc5-points-commerce.ps1`

边界：SC5 建立商品交易及初始履约状态，不实现完整发货/签收/评价闭环。

## SC6：履约、评价与售后闭环

目标：完成商品/服务履约确认、版本化评价和售后 SLA/重开/补偿；跨模块收益、积分与库存调整必须通过拥有模块端口。

后端与数据库：

- `[现有] backend-next/src/main/java/com/face/platform/aftersale/AfterSaleApplicationService.java`
- `[现有] backend-next/src/main/java/com/face/platform/aftersale/AfterSalePolicy.java`
- `[现有] backend-next/src/main/java/com/face/platform/servicecare/CustomerConfirmationService.java`
- `[现有] backend-next/src/main/java/com/face/platform/servicecare/ServiceRecordLifecycleService.java`
- `[现有] backend-next/src/main/java/com/face/platform/v3/aftersale/V3AfterSaleController.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/fulfillment/FulfillmentApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/review/ReviewApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/aftersale/AfterSaleResolutionApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/v3/review/V3ReviewController.java`
- `[计划新增] backend-next/src/main/resources/db/migration/V2026080305__sc6_fulfillment_review_aftersale.sql`

前端与验证：

- `[现有] front-next/src/views/AfterSalesView.vue`
- `[现有] front-next/src/views/AppointmentsView.vue`
- `[现有] admin-next/src/views/OperationsView.vue`
- `[现有] admin-next/src/views/AppointmentsView.vue`
- `[计划新增] front-next/src/views/FulfillmentView.vue`
- `[计划新增] front-next/src/views/ReviewView.vue`
- `[计划新增] admin-next/src/views/AfterSalesView.vue`
- `[计划新增] backend-next/src/test/java/com/face/platform/aftersale/AfterSaleResolutionAtomicityTest.java`
- `[计划新增] backend-next/src/test/java/com/face/platform/review/ReviewVersionPolicyTest.java`
- `[计划新增] scripts/verify-sc6-fulfillment-review-aftersale.ps1`

边界：只闭合 V3.0 用户端/运营端售后；历史复杂审批和岗位后台不扩建。

## SC7：历史数据导入与运营平台移动适配

目标：提供可预检、可重跑、可对账、可回滚批次的旧数据导入，并完成运营平台高频任务的窄屏适配。

后端与数据库：

- `[现有] backend-next/src/main/java/com/face/platform/audit/DataAccessAuditService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/dataimport/LegacyImportApplicationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/dataimport/LegacyImportValidationService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/v3/dataimport/V3LegacyImportController.java`
- `[计划新增] backend-next/src/main/resources/db/migration/V2026080306__sc7_legacy_import_batches.sql`

前端与验证：

- `[现有] admin-next/src/components/AppShell.vue`
- `[现有] admin-next/src/styles/app.css`
- `[计划新增] admin-next/src/views/DataImportView.vue`
- `[计划新增] backend-next/src/test/java/com/face/platform/dataimport/LegacyImportReplayTest.java`
- `[计划新增] backend-next/src/test/java/com/face/platform/dataimport/LegacyImportReconciliationTest.java`
- `[计划新增] scripts/verify-sc7-import-mobile-admin.ps1`

边界：导入只写各权威模块公开的导入端口；不得通过通用 SQL 导入器直接写业务表。

## SC8：公开 DEMO 与一键环境

目标：提供不暴露独立技师端的公开演示入口、稳定演示数据和可重复的一键启动/重置/备份流程。

运行与前端：

- `[现有] compose.yaml`
- `[计划新增] compose.demo.yaml`
- `[现有] scripts/start-face-all.ps1`
- `[计划新增] gateway/demo/index.html`
- `[计划新增] scripts/start-demo.ps1`
- `[计划新增] scripts/stop-demo.ps1`
- `[计划新增] scripts/reset-demo-data.ps1`
- `[计划新增] scripts/backup-demo.ps1`
- `[计划新增] scripts/verify-sc8-public-demo.ps1`
- `[计划新增] docs/operations/sc8-demo-runbook.md`

数据策略：演示数据使用环境级、可重置 seed 工具，**不**把演示数据写入生产 Flyway 迁移。公开入口仅显示用户端与运营管理平台。

边界：不新增业务状态和数据库结构；如发现结构缺口必须回到相应业务阶段处理。

## SC9：生产部署、安全与可运维性

目标：形成生产配置、备份恢复、监控告警、安全基线和灾难恢复验收证据。

运行与文档：

- `[现有] compose.yaml`
- `[计划新增] compose.production.yaml`
- `[现有] backend-next/src/main/java/com/face/platform/api/DatabaseContractHealthIndicator.java`
- `[现有] backend-next/src/main/java/com/face/platform/config/SecurityConfig.java`
- `[现有] backend-next/src/main/java/com/face/platform/v3/config/V3SecurityConfiguration.java`
- `[计划新增] docs/operations/production-runbook.md`
- `[计划新增] docs/operations/backup-restore-runbook.md`
- `[计划新增] docs/operations/monitoring-alerting.md`
- `[计划新增] docs/security/production-security-baseline.md`
- `[计划新增] scripts/backup-production.ps1`
- `[计划新增] scripts/restore-production.ps1`
- `[计划新增] scripts/verify-sc9-production-readiness.ps1`

数据策略：本阶段不新增业务 Flyway 迁移；只验证动态迁移、最小权限、备份恢复和部署可重复性。

边界：不以运维优化为名修改业务规则、接口状态机或新增后台角色。

## SC10：微信小程序复用统一后端

目标：新增微信小程序交付面，但身份绑定、会员、预约、订单、权益和售后全部复用统一后端及既有状态机。

后端、小程序与验证：

- `[现有] backend-next/src/main/java/com/face/platform/v3/auth/V3AuthSessionService.java`
- `[现有] backend-next/src/main/java/com/face/platform/client/ClientController.java`
- `[现有] backend-next/src/main/java/com/face/platform/appointment/AppointmentService.java`
- `[现有] backend-next/src/main/java/com/face/platform/transaction/TransactionService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/wechat/WechatIdentityBindingService.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/wechat/WechatCodeSessionPort.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/wechat/WechatCodeSessionAdapter.java`
- `[计划新增] backend-next/src/main/java/com/face/platform/v3/wechat/V3WechatAuthController.java`
- `[计划新增] backend-next/src/main/resources/db/migration/V2026080307__sc10_wechat_identity_binding.sql`
- `[计划新增] miniprogram/app.json`
- `[计划新增] miniprogram/app.ts`
- `[计划新增] miniprogram/services/api.ts`
- `[计划新增] miniprogram/pages/login/index.ts`
- `[计划新增] miniprogram/pages/home/index.ts`
- `[计划新增] backend-next/src/test/java/com/face/platform/wechat/WechatIdentityBindingTest.java`
- `[计划新增] scripts/verify-sc10-miniprogram-contract.ps1`

复用门禁：小程序不得新增独立会员表、订单表、预约表、账户表或业务状态机；契约测试必须证明 H5 与小程序对同一命令得到相同领域结果。

## 阶段依赖与停止条件

| 阶段 | 依赖 | 完成后必须停止并等待 |
| --- | --- | --- |
| SC2 | SC1 决策基线 | `通过，进入 SC3` |
| SC3 | SC2 身份与单店基础 | `通过，进入 SC4` |
| SC4 | SC3 预约上下文 | `通过，进入 SC5` |
| SC5 | SC4 订单与权益原子性 | `通过，进入 SC6` |
| SC6 | SC5 商品交易/积分账本 | `通过，进入 SC7` |
| SC7 | SC6 业务事实源稳定 | `通过，进入 SC8` |
| SC8 | SC7 导入与响应式验收 | `通过，进入 SC9` |
| SC9 | SC8 可重复演示环境 | `通过，进入 SC10` |
| SC10 | SC9 生产后端契约稳定 | 项目级人工验收 |

任何阶段若出现重复建模、跨模块直接写表、修改历史迁移或无法证明回滚的迁移，结论必须为“不通过”，不得进入下一阶段。
