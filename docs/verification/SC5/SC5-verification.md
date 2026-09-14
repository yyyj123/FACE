# SC5 最终验收报告

阶段：SC5（积分账户、任务与商城）  
版本：SC5 1.0.0  
结论：通过  
状态：立即停止，等待人工回复“通过，进入 SC6”

## 1. 基线与可追溯性

| 项目 | 值 |
| --- | --- |
| 产品基线 | `docs/specs/FACE_用户端与运营管理平台增量升级PRD_V3.0.md`，方案一 |
| 首次可追溯快照 SHA | `ff635972a7d4feb4d5192b2f385ebd3703f36326` |
| SC5 起始 tag | `sc4/v1.0.0` |
| SC5 起始 commit SHA | `0348a94bda4fb22ab29a5c388bfc4cbe1e861902` |
| SC5 已验证实现 SHA | `25d9fbb448514f646f51641a5602841af0f1b776` |
| SC5 发布 tag | `sc5/v1.0.0`（本报告提交后创建） |
| 后端 artifact | `face-chain-platform:0.1.0-SNAPSHOT` |
| 用户端 package | `face-client-v2@0.2.0` |
| 运营端 package | `face-chain-admin@0.1.0` |

首次快照只代表 2026-08-03 收到代码后建立的当前首次可追溯状态，不代表或伪造原始开发历史。SC5 从人工批准的 SC4 tag 增量实施；未重写既有迁移，未删除历史技师端、复杂多角色后台、采购、提成或培训资产。

## 2. 实际完成范围

1. 建立积分账户、独立有效期批次、不可变流水、冻结分配、FEFO 消耗、释放、按原批次恢复、30/7/1 天汇总提醒和签到事实。
2. 运营端可配置积分比例、有效期、单笔/活动期上限与签到周期/每日阶梯/周期奖励；全局有效期变化仅影响后续新批次，非超级管理员不能修改全局有效期。
3. 规则优先级为活动 > 项目/卡项 > 全局；按实际支付金额四舍五入，卡项在支付并到账后发放，商城人民币/组合订单在确认收货后按现金部分发放，积分兑换不重复发消费积分。
4. 普通护理/卡项结算增加积分候选，并与活动、护理券和折扣卡互斥；积分按原批次冻结/消费，支付失败释放，全额退款恢复原消费批次并追回原奖励积分。
5. 商城具备分类、商品、SKU、人民币/纯积分/固定组合三种购买模式、购物车、共享库存、结算母单、拆分子单、配送/自提快照、商品券、包裹与子单关联、发货和用户确认收货。
6. 三种购买模式竞争同一 SKU 余额；结算冻结，取消或支付失败释放，发货从冻结转已售，库存调整和状态变化保留流水与业务键。
7. 商城现金支付复用统一支付适配器、签名校验和显式渠道配置；回调事件独立去重，成功后完成积分/券/护理兑换权益，失败后原子释放库存、积分和券。
8. 用户端新增响应式积分与商城页；运营端新增响应式积分/签到、商品/SKU、共享库存、订单、包裹和补偿积分工作台。公开 DEMO 未增加独立技师端入口。

## 3. 未完成与范围外事项

- SC6 的 24 小时完成确认、评价版本/公开审核、低分售后、服务售后、实物退换货与验货未实现。
- 真实微信/支付宝/聚合支付、真实短信、物流轨迹、生产密钥和生产数据未接入；验收只使用显式启用的隔离 `DEMO_MOCK`。
- 自动 15 日收货、退货待检/恢复/报损后的售后资金与积分返还属于后续履约/售后定时任务，本阶段只验证用户主动确认收货。
- 商城本阶段验证固定金额商品券及商品范围；折扣券、独立运费券叠加和复杂地区/重量运费未作为本阶段终态验收声明。

## 4. 修改文件清单

SC5 相对 `sc4/v1.0.0` 共修改/新增 31 个受版本控制文件，集中于：

- `backend-next/.../points/`：积分规则、账户、批次、任务和策略；
- `backend-next/.../store/`：商城、库存、购物车、订单、券和包裹；
- `backend-next/.../checkout/`、`transaction/`：普通订单积分及统一支付/退款闭环；
- `backend-next/src/main/resources/db/migration/V2026080305...V2026080309`：只追加迁移；
- `front-next/src/views/PointsStoreView.vue` 与用户端 API/路由/导航；
- `admin-next/src/views/PointsMallOperationsView.vue` 与运营端 API/路由/导航；
- `scripts/verify-sc5-runtime.ps1`、`scripts/sc5-runtime-check.mjs`、`scripts/sc5-ui-check.mjs`；
- `docs/plans/2026-08-03-sc5.md` 与本报告。

提交序列：

```text
0928059 docs(sc5): define points and store stage plan
06f1878 feat(sc5): add points store and shared inventory schema
a4c76de feat(sc5): implement points and mall transaction services
c2f61ea feat(sc5): add responsive points mall operations
25d9fbb fix(sc5): close points earning and mall payment lifecycle
```

## 5. Flyway 与数据影响

SC5 只追加：

- `V2026080305__sc5_points_store_inventory.sql`
- `V2026080306__sc5_mall_coupons.sql`
- `V2026080307__sc5_default_points_seed_fallback.sql`
- `V2026080308__sc5_legacy_owner_points_seed.sql`
- `V2026080309__sc5_points_earn_and_mall_payment_callbacks.sql`

`V2026080309` 为最终对照补偿迁移，增加商城支付回调字段/去重事件和退款积分追回事实。两轮均动态执行 `migrate`、`validate`，并将迁移目录与 `flyway_schema_history` 对账；脚本没有写死 2026080309。

| 检查 | 第 1 轮 | 第 2 轮 |
| --- | ---: | ---: |
| 版本化迁移数 | 39 | 39 |
| 动态最新版本 | `2026080309` | `2026080309` |
| Flyway validate 数（含 callback） | 40 | 40 |
| 目录/历史差异 | 0 | 0 |
| migrate / validate | PASS | PASS |

## 6. 新增/修改 API

- 用户积分：`GET /api/v3/client/points`、`POST /api/v3/client/points/check-in`。
- 运营积分：`GET /api/v3/admin/points`、规则/任务更新、人工补偿、到期提醒和原批次恢复。
- 用户商城：目录、优惠券、购物车、结算、订单详情/取消、包裹确认收货。
- 运营商城：商品/SKU、库存调整、订单查询、包裹创建、商城券规则和定向发放。
- 普通结算与支付回调扩展积分抵扣、到账、失败释放和退款恢复；商城现金支付由同一签名回调入口路由到商城支付事实。

## 7. 权限与状态机

- 会员只可访问自身积分、券、购物车、订单与包裹；所有查询以 tenant/member/shop 范围约束。
- 运营写操作要求 `points:manage` 或 `mall:manage`；全局积分有效期额外要求 `SUPER_ADMIN`。
- 积分采用 `AVAILABLE/FROZEN` 投影、`RESERVED → CONSUMED/RELEASED/RESTORED` 冻结状态和追加流水。
- 商城支付采用 `PENDING → SUCCESS/FAILED/CANCELLED`，回调事件按渠道事件号和载荷哈希去重。
- 商城订单/子单和库存冻结在同一数据库事务内变化，失败路径不保留负积分、超卖或重复券消费。

## 8. 两轮正式命令

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc5-runtime.ps1 `
  -ProjectName face-sc5-final6 `
  -BackendPort 9490 -AdminPort 9481 -ClientPort 9482 -TechnicianPort 9483

powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-sc5-runtime.ps1 `
  -ProjectName face-sc5-final7 `
  -BackendPort 9590 -AdminPort 9581 -ClientPort 9582 -TechnicianPort 9583
```

两轮都使用显式固定端口和独立 Compose project/全新 MySQL volume；先预检端口，再执行静态契约、全量测试、双前端生产构建、Flyway、镜像、首次健康、五容器整体重启、API/SPA 和 SC5 合成业务矩阵；结束后删除容器、网络和 volume。未自动随机选择端口。

## 9. 测试、构建与容器结果

| 检查 | 第 1 轮 | 第 2 轮 |
| --- | --- | --- |
| 官方脚本退出码 | 0 | 0 |
| 固定端口预检 | PASS | PASS |
| Docker/B0 契约 | PASS | PASS |
| 后端全量测试 | 204/204 PASS | 204/204 PASS |
| 用户端测试/生产构建 | PASS | PASS |
| 运营端测试/生产构建 | PASS | PASS |
| Flyway migrate/validate/动态对账 | PASS | PASS |
| 五容器首次健康/API/SPA | PASS | PASS |
| 五容器整体重启复检 | PASS | PASS |
| SC5 业务矩阵 | PASS | PASS |
| 独立数据库 volume 清理 | PASS | PASS |

两轮一致的关键终态标记：

```text
SC5_RESPONSIVE_UI_CONTRACT=PASS
MIGRATION_DIRECTORY_HISTORY_MATCH=PASS;COUNT=39;LATEST=2026080309
DOCKER_INITIAL_HEALTH=PASS
DOCKER_RESTART_HEALTH=PASS
SC5_POINTS_RULE_TASK_CONFIGURATION=PASS
SC5_POINTS_BATCH_ACCOUNT_LEDGER=PASS
SC5_CHECKIN_CYCLE_IDEMPOTENCY=PASS
SC5_ORDER_PAYMENT_AUTO_EARN=PASS
SC5_ORDINARY_POINTS_FEFO_AND_ORIGINAL_BATCH_REFUND=PASS
SC5_THREE_MODES_SHARED_INVENTORY_CONCURRENCY=PASS
SC5_CART_SPLIT_PACKAGE_MERGE_TRACEABILITY=PASS
SC5_POINTS_EXPIRY_REMINDER_DEDUP=PASS
SC5_MALL_CASH_COUPON_SCOPE=PASS
SC5_MALL_PAYMENT_CALLBACK_IDEMPOTENCY=PASS
SC5_MALL_RECEIPT_AUTO_EARN=PASS
SC5_RUNTIME_ACCEPTANCE=PASS
SC5_DOCKER_ACCEPTANCE_CLEANUP=PASS
```

## 10. 数据、安全、隐私和审计核对

- 积分与库存均由批次/冻结分配/追加流水投影，验收中无负余额、无共享 SKU 超卖、无重复积分到账或重复回调事件。
- 退款奖励积分以原发放批次为依据按比例追回；已消费导致无法即时扣回的差额记录为 `OUTSTANDING` 追回事实，不以无符号余额透支掩盖。
- 商品券按配置商品范围计算；纯积分与固定组合商品拒绝使用现金券。
- 回调先验证时间戳/HMAC，再识别支付事实；相同事件号不同载荷返回冲突。
- 合成会员、手机号、支付密钥和物流单号只在临时验收库使用；清理后不保留真实客户数据或生产密钥。
- 页面继续沿用现有玫瑰/铜色、紧凑运营信息密度和移动端单列响应式规则；`impeccable` 约束用于保持现有视觉系统、清晰层级与可操作布局。本阶段未保存浏览器截图，因此不把像素级视觉回归列为已执行证据。

## 11. 工具与组件版本

| 组件 | 版本 |
| --- | --- |
| Windows | 11 / 10.0.26200 |
| Windows PowerShell | 5.1.26100.7462 |
| Git | 2.45.1.windows.1 |
| Docker Engine | 29.6.1 |
| Docker Compose | v5.1.4 |
| MySQL 镜像 | 8.4 |
| Maven | 3.9.16 |
| Java | Eclipse Adoptium 21.0.11 |
| Flyway | 12.4.0 |
| Node.js | v24.18.0 |
| npm | 11.16.0 |

## 12. 回滚与已知风险

1. 已执行的 Flyway 不得修改或删除；数据/结构回退必须追加补偿迁移。应用代码可按提交逆序 `git revert`。
2. 当前只在 Windows 11、Docker Desktop、MySQL 8.4 和合成数据上验证；不能据此声明 Linux 生产部署、真实支付、真实物流或真实大并发已经认证。
3. Maven 保留 Mockito 动态 agent 的未来 JDK 警告；npm 保留 `sass_binary_site` 未来弃用和第三方 Rollup PURE 注释警告。当前测试、构建和运行均成功。
4. 本阶段无压力测试；并发结论来自数据库行锁、条件更新、唯一键以及同一共享 SKU 冲突场景，不代表已完成容量基准。
5. V3.0 PRD 是唯一产品基线；V2.1、旧报告和历史截图只作来源记录，没有据此恢复已停止范围。

## 13. 阶段门禁

SC5 未进入 SC6 的完成确认、评价和售后实现。最终结论：SC5 验收通过。立即停止，等待人工回复“通过，进入 SC6”。
