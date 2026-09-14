# SC1 状态机与事务边界

状态：Accepted for staged implementation

日期：2026-08-03

## 1. 设计原则

- `STATE_OWNER`：每个状态只由一个领域应用服务推进，控制器、前端和其他模块不得直接更新。
- `TRANSACTION_OWNER`：一个用例必须有唯一事务编排者；参与模块通过端口写自己的表。
- `IDEMPOTENCY`：关键写接口先通过 `idempotency_record`，领域表再用业务唯一键防二次副作用。
- `LOCKING`：余额、时段和版本更新必须使用稳定行锁顺序或乐观锁，不能先读后无条件写。
- `OUTBOX`：事务内写业务事实和 outbox，事务后投影通知/外部动作；不得在数据库事务中等待第三方网络调用。

## 2. 状态所有者

| 状态 | 唯一所有者 | 当前实现 | 后续动作 |
|---|---|---|---|
| 会员 ACTIVE/INACTIVE | Member application | `MemberService` | 保留，资产状态通过端口联动 |
| 账号/会话 | Auth application | `ClientAuthService`、`V3AuthSessionService` | SC2 统一手机号与两角色 |
| 预约 | Appointment application | `AppointmentService`、`AppointmentStatusPolicy` | SC3 合并用户端写路径 |
| 时间锁/候补 | Appointment application | 缺失 | SC3 新增 |
| 订单财务状态 | Order application | `TransactionService`、`TransactionStatusPolicy` | SC4 扩展但不复制订单 |
| 支付/退款 | Payment/Refund application | `PaymentApplicationService`、`RefundApplicationService` | SC4 补零元与渠道配置 |
| 卡项与卡次 | Benefit application | `PackageAccountService` | SC4 扩三类卡和冻结 |
| 优惠券 | Coupon application | 缺失 | SC4 新增 |
| 积分批次 | Points application | 缺失 | SC5 新增，旧字段变投影 |
| 库存余额/流水 | Inventory application | `InventoryService` | SC5 增加商城冻结/退回 |
| 顾客确认 | Fulfillment application | `CustomerConfirmationService` | SC6 原子编排最终核销 |
| 售后 | AfterSale application | `AfterSaleApplicationService`、`AfterSalePolicy` | SC6 加 SLA/验货 |
| 营销投递 | Marketing delivery | `MarketingCampaignApplicationService` | 保留，与价格活动分离 |
| 站内通知 | Notification application | `NotificationProjectionService` | 扩模板和渠道尝试 |

## 3. 当前与目标状态机

### 3.1 预约、时间锁与候补

当前预约：

```text
PENDING → CONFIRMED → CHECKED_IN → IN_SERVICE → COMPLETED
   └──────────────→ CANCELLED
CONFIRMED → NO_SHOW
```

V3.0 新预约目标：

```text
CONFIRMED
→ ARRIVED
→ IN_SERVICE
→ PENDING_CUSTOMER_CONFIRMATION
   ├→ COMPLETED
   └→ AFTER_SALES_PROCESSING
```

- 历史 `PENDING/CHECKED_IN` 保留兼容读取；新预约支付前不落 `PENDING` 预约，使用独立 time lock。
- `CHECKED_IN` 在 API 兼容层映射为 `ARRIVED`，不直接改写历史状态。
- `AppointmentApplicationService` 是唯一状态写入者；履约服务通过命令端口请求推进。

时间锁：

```text
HELD → CONVERTED
  ├→ EXPIRED
  └→ RELEASED
```

候补：

```text
WAITING → MATCHED → WAITING_CONFIRMATION → CONFIRMED
                           ├→ EXPIRED
                           ├→ CANCELLED
                           └→ INVALID
```

候补通知失败不自动创建预约；只有顾客重新确认条款并完成支付/权益冻结后才转 `CONFIRMED`。

### 3.2 订单、支付与退款

保留 `sales_order.status` 为财务状态：

```text
UNPAID → PARTIALLY_PAID → PAID
   └───────────────→ VOID
PAID → PARTIALLY_REFUNDED → REFUNDED
```

V3.0 新增独立履约状态，不把发货/服务完成塞入财务状态：

```text
PENDING → READY → FULFILLING → COMPLETED
   ├→ CANCELLED
   └→ AFTER_SALES_PROCESSING
```

支付：

```text
PENDING → SUCCESS
   ├→ FAILED
   └→ CANCELLED
```

`ZERO_AMOUNT` 是支付类型/方式，记录直接以 `SUCCESS` 成立，仍有幂等键和业务对象；不是跳过支付表。

退款：

```text
PENDING → APPROVED → PROCESSING → SUCCESS
   └→ REJECTED             └→ FAILED → PROCESSING
```

只有 `SUCCESS` 才触发优惠券、积分、卡项和库存恢复；处理中不能提前关闭售后。

### 3.3 卡项、优惠券与积分

卡产品：

```text
DRAFT → ACTIVE → INACTIVE
```

卡实例：

```text
ACTIVE ↔ FROZEN
ACTIVE → EXHAUSTED | EXPIRED | CANCELLED
```

次数/储值权益不依靠覆盖状态表达预留，使用追加流水：

```text
AVAILABLE → RESERVED → CONSUMED
              └→ RELEASED
CONSUMED → REVERSAL
```

优惠券：

```text
UNCLAIMED → AVAILABLE → LOCKED → USED
                       ├→ EXPIRED
                       └→ CANCELLED
USED → RETURNED（仅退款策略允许且退款 SUCCESS）
```

积分批次：

```text
AVAILABLE → PARTIALLY_CONSUMED → CONSUMED
    ├→ FROZEN → AVAILABLE/CONSUMED
    └→ EXPIRED
```

积分扣回引用原始发放流水和批次，不创建负余额或任意新批次抵消。

### 3.4 履约、确认、评价与售后

顾客确认：

```text
PENDING → CONFIRMED
   ├→ DISPUTED
   └→ SYSTEM_AUTO_CONFIRMED
```

- 24 小时到期任务必须用同一完成命令，不能另写一套自动完成 SQL。
- `CONFIRMED/SYSTEM_AUTO_CONFIRMED` 在一个事务编排中完成卡项/储值/体验券核销、积分发放、预约完成和 outbox。
- `DISPUTED` 原子进入 `AFTER_SALES_PROCESSING` 并创建或关联售后。

评价当前只有 `VISIBLE/HIDDEN`，目标拆成版本与审核：

```text
DRAFT_VERSION → PENDING_REVIEW → PUBLIC
                      ├→ REJECTED
                      └→ HIDDEN
任意当前版本 → DELETED（逻辑删除，历史版本保留）
```

售后复用当前主状态并增加 SLA/退货子状态：

```text
OPEN → TRIAGED → PROCESSING
→ WAITING_CUSTOMER → CLOSED
PROCESSING → REJECTED
WAITING_CUSTOMER → REOPENED（最多一次）→ PROCESSING
```

商品退货的寄回、待检和验货是附属 fulfillment 状态；未验货通过，不得触发退款/积分/运费恢复。

### 3.5 营销与通知

营销投递现状继续保留：

```text
DRAFT → PENDING_APPROVAL → APPROVED → RUNNING → COMPLETED
  └──────────────→ CANCELLED
PENDING_APPROVAL → REJECTED
```

价格活动另有发布状态，不能复用投递审批状态：

```text
DRAFT → SCHEDULED → ACTIVE → OFFLINE
```

通知投影：

```text
PENDING → PROJECTED
   └→ FAILED → PENDING
```

站内消息：`UNREAD → READ`。外部渠道 delivery attempt 单独记录 `PENDING/SENT/DELIVERED/FAILED/UNAVAILABLE`。

### 3.6 库存

库存余额没有业务状态，采用数量约束：

```text
available = quantity_on_hand - quantity_reserved
available >= 0
```

库存动作只能追加 movement：`RECEIPT/ADJUST/RESERVE/RELEASE/SHIP/RETURN_PENDING_INSPECTION/RESTORE/DAMAGE`。冻结的调拨和采购状态机不扩展到 V3.0 新功能。

## 4. 事务所有者与边界

### 4.1 创建订单并锁定时段/权益

`CheckoutApplicationService`（SC4 计划新增）是事务所有者：

1. 校验幂等记录和报价版本。
2. 锁定预约 time lock 行。
3. 通过 `BenefitReservationPort` 锁定一项优惠/卡项/积分方案。
4. 创建 `sales_order`、明细和决策快照。
5. 写 outbox 后提交。
6. 事务外调用支付适配器；失败时由补偿命令释放权益和 time lock。

外部网络调用不得持有数据库行锁。

### 4.2 支付成功或零元成立

`PaymentCompletionApplicationService` 是事务所有者：

1. 锁定 idempotency、payment、order。
2. 验证回调签名/金额/渠道和当前状态。
3. 支付置 `SUCCESS`，订单财务状态置 `PAID`。
4. time lock 转预约、权益 reservation 转正式冻结/使用。
5. 写 outbox；重复回调读取原结果，不重复发卡或预约。

### 4.3 履约最终完成

`FulfillmentCompletionApplicationService`（SC6 计划新增）是事务所有者：

1. 锁定 customer confirmation、appointment/service record、相关权益流水。
2. 推进预约与履约状态。
3. 通过端口完成卡次/储值/体验券核销和积分发放。
4. 写订单履约状态与 outbox。

任何步骤冲突则整笔回滚；不允许先核销后补确认。

### 4.4 退款成功

`RefundApplicationService` 拥有退款状态，外部退款在事务外执行。回调/查询确认成功后，由 `RefundCompletionApplicationService` 在单一事务中：

- 退款置 `SUCCESS`；
- 更新订单退款汇总；
- 按原引用冲正积分、卡项、储值和优惠券；
- 商品退货必须先验证验货通过；
- 写 outbox。

### 4.5 商城结算和库存

- 加购物车不锁库存。
- 提交结算时 `CommerceCheckoutApplicationService` 先拆分订单，再按稳定 SKU 顺序锁 `stock_balance` 并增加 reserved。
- 发货时 Inventory application 将 reserved 转为 on-hand 扣减并追加 `SHIP` movement。
- 退货先追加 `RETURN_PENDING_INSPECTION`，验货通过后才 `RESTORE`，否则 `DAMAGE`。

## 5. 稳定锁顺序

同一事务按以下顺序获取锁，多个同类对象按主键升序：

1. `idempotency_record`；
2. `sales_order`；
3. `payment_transaction/refund_transaction`；
4. `appointment_time_lock/appointment`（按 staff、start、id）；
5. `member_coupon/package_instance/point_batch`；
6. `stock_balance`（按 location、product）；
7. `after_sale_case/customer_confirmation`。

发现现有路径使用相反顺序时，后续阶段必须先重构或拆成 outbox/补偿，不允许带着已知死锁顺序继续加功能。

## 6. 幂等与版本

- API 层：所有关键写使用 header `Idempotency-Key`，同键同 hash 返回原结果，同键异 hash 为 409。
- 领域层：订单号、支付号、退款号、卡实例发放键、流水 business key、事件 id 均有唯一约束。
- 更新：聚合根必须带 `version`/`If-Match`；流水不更新，只追加 reversal。
- 定时任务：使用稳定业务键（例如 `AUTO_CONFIRM:{confirmationId}`）并走同一命令服务。
- 外部回调：原始 event id/渠道流水号唯一，验证签名后再进入幂等事务。

## 7. 跨模块写入门禁

- 控制器只调用一个应用用例，不直接使用 `JdbcTemplate`。
- 模块 A 不得直接 INSERT/UPDATE 模块 B 的主表或流水表。
- 查询可通过 query port；为兼容保留的直连查询必须登记并在对应阶段移除。
- 同库事务由编排服务调用各模块 command port，所有实现加入同一 Spring `REQUIRED` 事务；外部系统使用 outbox。
- SC2 首先引入会员账户初始化端口；SC3 首先合并预约写路径；SC4 首先拆分 TransactionService 的账户/预约/库存直接写入。完成这些前不得新增相同方向的直接 SQL。
