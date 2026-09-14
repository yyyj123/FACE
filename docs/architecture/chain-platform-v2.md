# FACE 连锁平台 V2

## 本阶段结果

当前升级采用并行迁移，不直接替换仍在运行的 Vue 2 和 Spring Boot 2 兼容系统。

- `backend-next`：Java 21、Spring Boot 4.1、Spring Security、Flyway、MySQL。
- `admin-next`：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus 按需加载。
- 旧系统继续使用 `8080/8081/8082`。
- V2 后端使用 `8090`，V2 管理端使用 `5173`。
- 新数据结构由 Flyway 版本 `2026072401` 管理。

## 安全边界

V2 接口禁止从请求参数直接信任 `tenantId` 或 `shopId`。

```text
Token
  -> token + account
  -> tenant_id
  -> account_shop_role
  -> region/shop scope
  -> filtered query
```

账号只有在 `account_shop_role` 中存在有效授权时才能进入 V2。总部角色可以访问租户内门店；区域角色只能访问授权区域；门店角色只能访问授权门店。

## 数据结构

### 组织与权限

- `tenant`
- `region`
- `shop.tenant_id/region_id/shop_code`
- `role_definition`
- `permission_definition`
- `role_permission`
- `account_shop_role`
- `staff_shop_assignment`

### 会员与跨店账户

- `member.tenant_id/home_shop_id/global_member_no`
- `member_shop_profile`
- `member_account`
- `member_account_ledger`

余额、赠送余额和积分均使用账户加不可变流水，不允许直接覆盖历史余额。

### 项目、订单、库存

- `service_item.tenant_id/is_chain_standard`
- `shop_service_price`
- `sales_order.tenant_id/business_date/currency_code`
- `payment_transaction`
- `refund_transaction`
- `stock_location`
- `stock_balance`
- `inventory_movement.tenant_id/idempotency_key`
- `idempotency_record`

## 启动

```powershell
$env:DB_PASSWORD = '<本机数据库密码>'
powershell -ExecutionPolicy Bypass -File E:\face\scripts\start-face-next.ps1
```

访问：

- V2管理端：`http://127.0.0.1:5173`
- V2健康检查：`http://127.0.0.1:8090/face-next/api/v2/health`

V2管理端当前沿用原系统账号完成登录，然后由V2后端重新解析租户和门店权限。

## 验证

```powershell
$env:DB_PASSWORD = '<本机数据库密码>'
powershell -ExecutionPolicy Bypass -File E:\face\scripts\verify-chain-foundation.ps1

cd E:\face\backend-next
mvn test
mvn package

cd E:\face\admin-next
npm run build
```

## 备份与恢复

迁移前备份：

`E:\face\backups\database\face_salon_before_chain_v2_20260724_0115_tables.sql`

若需要恢复，不要手工逐列删除。停止应用后，以空的 `face_salon` 数据库导入该备份，并重新创建原有两个视图：

- `v_low_stock_products`
- `v_today_appointment_summary`

正式环境必须先在预发布数据库演练恢复，并记录恢复耗时。

## 下一阶段

1. 把会员管理切换到 `member + member_shop_profile`。（已完成）
2. 把预约管理全部切换到 `appointment + appointment_item`。
3. 上线订单、支付、退款和会员账户流水。
4. 上线库存地点、库存余额和门店调拨。
5. 按页面逐步迁移现有 Vue 2 管理端，最终关闭旧生成式控制器。

## 第二阶段：连锁会员中心

新版会员中心位于 `/members`，由 `backend-next` 的 `/api/v2/members` 提供接口。

- 总部、区域、门店角色只能读取各自范围内的会员。
- 前端传入的门店编号必须再次经过服务端权限校验。
- 相同手机号在集团内视为同一会员，跨店新增时建立门店关系而不重复建档。
- 新会员自动建立余额、赠送金、积分三类账户。
- 编辑使用 `version` 乐观锁，避免两个员工相互覆盖资料。
- 停用只停用当前门店关系；没有其他有效门店关系时才冻结集团会员账户。
- 新增、编辑、停用、恢复均写入 `audit_log`。

重复执行验收：

```powershell
$env:FACE_ADMIN_PASSWORD = "<管理员密码>"
npm run verify:members
Remove-Item Env:FACE_ADMIN_PASSWORD
```

## 第三阶段：预约与排班中心

新版预约中心位于 `/appointments`，由 `backend-next` 的 `/api/v2/appointments` 提供接口。

- `V2026072403` 将旧预约、会员、项目、美容师技能和排班迁移到新结构，并补齐预约状态历史。
- 可预约时段由服务端根据门店、项目时长、美容师技能、工作排班、请假锁定和已有预约实时计算。
- 创建与改期会锁定美容师并再次校验时间重叠，避免两个前台同时占用同一时段。
- 预约状态按 `待确认 → 已确认 → 已到店 → 服务中 → 已完成` 流转；取消和爽约必须记录原因。
- 改期和状态操作使用 `version` 乐观锁，旧页面提交不会覆盖其他员工刚完成的操作。
- 门店范围、查看权限和管理权限全部由服务端从登录令牌推导，前端传入无权门店会返回 `403`。
- 创建、改期和状态变化会写入 `appointment_status_history` 与 `audit_log`。

重复执行验收：

```powershell
$env:FACE_ADMIN_PASSWORD = "<管理员密码>"
npm run verify:appointments
Remove-Item Env:FACE_ADMIN_PASSWORD
```

验收脚本覆盖创建、按日查询、重复时段冲突、改期、过期版本冲突、完整状态流转、非法终态流转和跨门店越权。

## 第四阶段：交易与结算中心

新版交易中心位于 `/transactions`，由 `backend-next` 的 `/api/v2/transactions` 提供接口。

- `V2026072404` 扩展订单并发版本、已退款金额和退款审核字段，迁移旧系统 5 笔消费记录、10 条订单明细、5 笔支付流水和 5 个零售产品。
- `V2026072405` 为所有会员补齐储值、赠送金和积分账户，并为演示会员建立可验证的期初余额流水。
- 已完成预约可以直接生成订单，同一预约只能结算一次；现场消费也可以直接选择会员、护理项目和零售产品。
- 收款支持现金、银行卡、微信、支付宝和会员余额，支持分次收款与组合收款。
- 支付和退款均要求幂等键，网络重试不会重复扣款或重复退款。
- 退款采用申请与审核两步流转；会员余额支付审核通过后会原路退回余额账户。
- 余额支付与余额退款均写入 `member_account_ledger`，历史余额不能被覆盖或删除。
- 未收款订单可以作废；已收款订单必须通过退款处理，所有关键动作写入 `audit_log`。

重复执行验收：

```powershell
$env:FACE_ADMIN_PASSWORD = "<管理员密码>"
npm run verify:transactions
Remove-Item Env:FACE_ADMIN_PASSWORD
```

验收脚本覆盖订单创建、余额支付、支付幂等、旧版本冲突、退款幂等、退款审核、余额原路退回、不可变流水、订单作废和跨门店越权。

## 第五阶段：库存与调拨中心

新版库存中心位于 `/inventory`，由 `backend-next` 的 `/api/v2/inventory` 提供接口。

- `V2026072406` 启用库存地点、地点余额、冻结数量、不可变流水和调拨审批数据结构。
- 旧系统 5 条出库记录已按期初余额重建，迁移后商品库存与各地点余额合计保持一致。
- 待审核调拨先冻结调出库存；批准后在同一事务内完成调出、调入和双向流水，拒绝则释放冻结。
- 商品订单仅在最终收款成为已收款时扣库；库存不足会回滚收款流水、会员余额和订单状态。
- 退款不自动回库，实物验收后由门店执行“退货入库”。
- 库存调整和调拨创建使用幂等键，库存余额与调拨审核使用乐观锁版本。

详细设计见 `docs/architecture/inventory-center-v2.md`。

重复执行验收：

```powershell
$env:FACE_ADMIN_PASSWORD = "<管理员密码>"
npm run verify:inventory
Remove-Item Env:FACE_ADMIN_PASSWORD
```
