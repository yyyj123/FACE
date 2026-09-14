import crypto from 'node:crypto'
import { spawnSync } from 'node:child_process'

const options = Object.fromEntries(process.argv.slice(2).map((entry) => {
  const [key, ...rest] = entry.replace(/^--/, '').split('=')
  return [key, rest.join('=')]
}))
const { client, admin, project, root, secret } = options
const envFile = options['env-file']
const demoCookie = options.cookie
const accountPassword = options['account-password'] || 'Face@123'
if (!client || !admin || !project || !root || !envFile || !secret) {
  throw new Error('SC5 runtime checker requires client, admin, project, root, env-file and secret.')
}
const suffix = Date.now().toString().slice(-9)
const phone = `139${suffix.slice(-8)}`

function sql(statement) {
  const result = spawnSync('docker', [
    'compose', '--project-name', project, '--project-directory', root,
    '--env-file', envFile, '-f', `${root}/compose.yaml`,
    'exec', '-T', 'mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot face_salon',
  ], { input: statement, encoding: 'utf8' })
  if (result.status !== 0) throw new Error(`SC5 SQL assertion failed: ${result.stderr}`)
  return result.stdout.trim().split(/\r?\n/).filter(Boolean)
}

async function request(base, path, { method = 'GET', token, body, headers = {}, expected = [200], rawBody } = {}) {
  const content = rawBody ?? (body === undefined ? undefined : JSON.stringify(body))
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { ...(content === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(demoCookie ? { Cookie: demoCookie } : {}), ...headers },
    body: content,
  })
  const text = await response.text()
  let payload
  try { payload = text ? JSON.parse(text) : undefined } catch { payload = undefined }
  if (!expected.includes(response.status)) {
    throw new Error(`${method} ${path} returned ${response.status}: ${payload?.message ?? text}`)
  }
  return { status: response.status, data: payload?.data, payload }
}

const value = (object, camel, snake) => object?.[camel] ?? object?.[snake]
const key = (prefix) => `${prefix}-${suffix}`.slice(0, 80)
const expect = (condition, message) => { if (!condition) throw new Error(message) }

async function callback(payment, status, eventPrefix) {
  const paymentNo = value(payment, 'paymentNo', 'payment_no')
  const amount = Number(value(payment, 'amount', 'cashAmount', 'cash_amount')).toFixed(2)
  const eventId = `${eventPrefix}-${suffix}`
  const body = JSON.stringify({ event_id: eventId, payment_no: paymentNo, status, amount,
    external_transaction_no: `DEMO-TXN-${eventId}`, channel_status: 'DEMO_CONFIRMED' })
  const timestamp = Math.floor(Date.now() / 1000)
  const signature = crypto.createHmac('sha256', secret).update(`${timestamp}.${body}`).digest('hex')
  await request(client, '/api/v3/payment-channels/DEMO_MOCK/callbacks', {
    method: 'POST', rawBody: body, headers: {
      'X-Payment-Timestamp': String(timestamp), 'X-Payment-Event-Id': eventId,
      'X-Payment-Signature': signature,
    },
  })
}

sql(`
INSERT INTO member (tenant_id, home_shop_id, shop_id, member_no, global_member_no, name, phone, source, status)
VALUES (1, 1, 1, 'SC5-${suffix}', 'SC5-G-${suffix}', 'SC5 Runtime Member', '${phone}', 'SC5_RUNTIME', 'ACTIVE');
SET @member_id := LAST_INSERT_ID();
INSERT INTO member_shop_profile (tenant_id, member_id, shop_id, source, status)
VALUES (1, @member_id, 1, 'SC5_RUNTIME', 'ACTIVE');
INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, phone, password_hash, role_code, member_id, status)
SELECT 1, 1, 1, 'sc5-member-${suffix}', 'SC5 Runtime Member', '${phone}', password_hash, 'MEMBER', @member_id, 'ACTIVE'
FROM account WHERE username = 'admin' LIMIT 1;
SET @account_id := LAST_INSERT_ID();
INSERT INTO account_shop_role (tenant_id, account_id, shop_id, role_id, status)
SELECT 1, @account_id, 1, id, 'ACTIVE' FROM role_definition WHERE tenant_id = 1 AND role_code = 'MEMBER';
`)
const memberId = Number(sql(`SELECT id FROM member WHERE phone='${phone}';`).at(-1))
const adminToken = value((await request(admin, '/api/v3/auth/admin-login', {
  method: 'POST', body: { username: 'admin', password: accountPassword },
})).data, 'accessToken', 'access_token')
const memberToken = value((await request(client, '/api/v3/client/identity/password-login', {
  method: 'POST', body: { phone, password: accountPassword },
})).data, 'accessToken', 'access_token')
expect(adminToken && memberToken, 'SC5 login tokens were not issued.')

const pointsAdmin = (await request(admin, '/api/v3/admin/points?shop_id=1', { token: adminToken })).data
const earnRule = pointsAdmin.rules.find((item) => item.ruleType === 'EARN' && item.targetType === 'GLOBAL')
const checkinTask = pointsAdmin.tasks.find((item) => item.taskType === 'CHECKIN')
expect(earnRule && checkinTask, 'Default points rule or check-in task is missing.')
await request(admin, `/api/v3/admin/points/rules/${earnRule.id}`, { method: 'PUT', token: adminToken,
  body: { shop_id: 1, points_per_yuan: 2, points_per_currency: earnRule.pointsPerCurrency,
    minimum_points: earnRule.minimumPoints, step_points: earnRule.stepPoints,
    max_discount_ratio: earnRule.maxDiscountRatio, max_discount_amount: earnRule.maxDiscountAmount,
    single_cap_points: 5000, member_period_cap_points: 20000,
    validity_months: earnRule.validityMonths, status: earnRule.status, version: earnRule.version },
})
const configuredRewards = [5, 5, 10, 10, 15, 15, 30]
await request(admin, `/api/v3/admin/points/tasks/${checkinTask.id}`, { method: 'PUT', token: adminToken,
  body: { shop_id: 1, reward_points: configuredRewards[0], cycle_days: configuredRewards.length,
    daily_rewards: configuredRewards, cycle_bonus_points: 50, repeat_cycle: true,
    member_period_cap_points: 5000, status: checkinTask.status, version: checkinTask.version },
})
console.log('SC5_POINTS_RULE_TASK_CONFIGURATION=PASS')

async function grant(points, validity, marker) {
  return (await request(admin, '/api/v3/admin/points/grants', {
    method: 'POST', token: adminToken, headers: { 'Idempotency-Key': key(`grant-${marker}`) },
    body: { shop_id: 1, member_id: memberId, points, source_type: 'ADMIN',
      reference_type: 'SC5_RUNTIME', reason: marker, validity_months: validity },
  })).data
}
await grant(12000, 1, 'EARLY')
await grant(12000, 12, 'LATE')
expect(sql(`SELECT COUNT(*) FROM points_batch pb JOIN points_account pa ON pa.id=pb.account_id WHERE pa.member_id=${memberId};`).at(-1) === '2', 'Independent points batches were not created.')
console.log('SC5_POINTS_BATCH_ACCOUNT_LEDGER=PASS')

const checkinKey = key('checkin')
const firstCheckin = (await request(client, '/api/v3/client/points/check-in', {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': checkinKey }, body: {},
})).data
const replayCheckin = (await request(client, '/api/v3/client/points/check-in', {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': checkinKey }, body: {},
})).data
expect(value(firstCheckin, 'id', 'id') === value(replayCheckin, 'id', 'id'), 'Daily check-in was not idempotent.')
console.log('SC5_CHECKIN_CYCLE_IDEMPOTENCY=PASS')

const card = (await request(admin, '/api/v3/benefits/card-products', {
  method: 'POST', token: adminToken, body: { shop_id: 1, package_code: `SC5-CARD-${suffix}`,
    name: 'SC5 points checkout card', card_type: 'STORED_VALUE', sale_price: 100,
    principal_amount: 100, gift_amount: 0, minimum_spend: 0,
    validity_days: 365, scope_json: '{}', items: [] },
})).data
const quote = (await request(client, '/api/v3/checkout/quotes', {
  method: 'POST', token: memberToken, body: { package_product_id: value(card, 'id', 'id') },
})).data
const pointOption = value(quote, 'discountOptions', 'discount_options')
  .find((item) => value(item, 'selectionType', 'selection_type') === 'POINTS')
expect(pointOption && Number(value(pointOption, 'pointsUsed', 'points_used')) > 0, 'Ordinary checkout did not offer points redemption.')
const ordinary = (await request(client, '/api/v3/checkouts', {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': key('ordinary-points') },
  body: { package_product_id: value(card, 'id', 'id'), selection_type: 'POINTS',
    selection_reference_id: value(pointOption, 'referenceId', 'reference_id'), payment_method: 'DEMO_MOCK' },
})).data
await callback(ordinary.payment, 'SUCCESS', 'ordinary-points')
const ordinaryOrderId = value(ordinary, 'orderId', 'order_id')
expect(sql(`SELECT status FROM points_reservation WHERE reference_type='SALES_ORDER' AND reference_id=${ordinaryOrderId};`).at(-1) === 'CONSUMED', 'Ordinary order points were not consumed after payment.')
expect(sql(`SELECT COUNT(*) FROM points_batch WHERE source_type='ORDER_PAYMENT' AND source_reference_type='SALES_ORDER' AND source_reference_id=${ordinaryOrderId};`).at(-1) === '1', 'Paid ordinary order did not earn points exactly once.')
console.log('SC5_ORDER_PAYMENT_AUTO_EARN=PASS')
expect(sql(`SELECT COUNT(*) FROM points_reservation_allocation pra JOIN points_reservation pr ON pr.id=pra.reservation_id JOIN points_batch pb ON pb.id=pra.batch_id WHERE pr.reference_id=${ordinaryOrderId} AND pb.source_reference_type='SC5_RUNTIME' ORDER BY pb.expires_at LIMIT 1;`).at(-1) === '1', 'FEFO allocation did not retain its batch relation.')
await request(admin, `/api/v3/admin/points/references/SALES_ORDER/${ordinaryOrderId}/restore`, {
  method: 'POST', token: adminToken, body: { shop_id: 1, reason: 'SC5 原批次返还验证' },
})
expect(sql(`SELECT status FROM points_reservation WHERE reference_type='SALES_ORDER' AND reference_id=${ordinaryOrderId};`).at(-1) === 'RESTORED', 'Refund did not restore the original allocated batches.')
console.log('SC5_ORDINARY_POINTS_FEFO_AND_ORIGINAL_BATCH_REFUND=PASS')

async function createProduct(marker, prices = {}) {
  const created = (await request(admin, '/api/v3/admin/mall/products', {
    method: 'POST', token: adminToken, body: { shop_id: 1, category_code: `CAT-${suffix}`,
      category_name: 'SC5 runtime', product_code: `P-${marker}-${suffix}`,
      product_type: 'PHYSICAL', name: `SC5 ${marker}`, delivery_mode: 'DELIVERY',
      freight_template_code: 'STD', separate_shipping: false,
      after_sale_policy: 'SC6', sku_code: `SKU-${marker}-${suffix}`, spec: { marker },
      cash_price: prices.cash ?? 10, points_price: prices.points ?? 100,
      combo_cash_price: prices.comboCash ?? 5, combo_points_price: prices.comboPoints ?? 50,
      cash_enabled: prices.cashEnabled ?? true, points_enabled: prices.pointsEnabled ?? true,
      combo_enabled: prices.comboEnabled ?? true, warning_threshold: 0 },
  })).data
  await request(admin, '/api/v3/admin/mall/inventory/adjustments', {
    method: 'POST', token: adminToken, headers: { 'Idempotency-Key': key(`stock-${marker}`) },
    body: { shop_id: 1, sku_id: created.skuId, delta: prices.stock ?? 2, reason: 'SC5 runtime stock' },
  })
  return created
}
async function addCart(skuId, mode) {
  return request(client, '/api/v3/client/mall/cart', { method: 'POST', token: memberToken,
    body: { sku_id: skuId, purchase_mode: mode, quantity: 1, delivery_mode: 'DELIVERY' } })
}
async function mallCheckout(marker, mallCouponId = null) {
  return (await request(client, '/api/v3/client/mall/checkouts', { method: 'POST', token: memberToken,
    headers: { 'Idempotency-Key': key(marker) }, body: { payment_method: 'DEMO_MOCK', mall_coupon_id: mallCouponId } })).data
}

const shared = await createProduct('SHARED')
await addCart(shared.skuId, 'POINTS')
const pointMallOrder = await mallCheckout('mall-points')
await addCart(shared.skuId, 'CASH')
const cashMallOrder = await mallCheckout('mall-cash')
await addCart(shared.skuId, 'COMBINATION')
await request(client, '/api/v3/client/mall/checkouts', { method: 'POST', token: memberToken,
  expected: [409], headers: { 'Idempotency-Key': key('mall-combo-conflict') },
  body: { payment_method: 'DEMO_MOCK' } })
expect(sql(`SELECT CONCAT(available_quantity,'|',reserved_quantity) FROM mall_sku_inventory WHERE sku_id=${shared.skuId};`).at(-1) === '0|2', 'Three purchase modes did not compete for one shared SKU balance.')
await callback(cashMallOrder.payment, 'FAILED', 'mall-cash-failed')
expect(sql(`SELECT status FROM mall_order WHERE id=${cashMallOrder.id};`).at(-1) === 'CANCELLED', 'Failed mall payment callback did not cancel the order.')
const comboMallOrder = await mallCheckout('mall-combo-after-release')
await request(client, `/api/v3/client/mall/orders/${comboMallOrder.id}/cancel`, { method: 'POST', token: memberToken, body: {} })
expect(sql(`SELECT CONCAT(available_quantity,'|',reserved_quantity) FROM mall_sku_inventory WHERE sku_id=${shared.skuId};`).at(-1) === '1|1', 'Pending order cancellation did not release shared stock.')
console.log('SC5_THREE_MODES_SHARED_INVENTORY_CONCURRENCY=PASS')

const pointsSku = await createProduct('SPLIT-POINTS', { stock: 1, cashEnabled: false, comboEnabled: false })
const zeroCashSku = await createProduct('SPLIT-CASH', { stock: 1, cash: 0, pointsEnabled: false, comboEnabled: false })
await addCart(pointsSku.skuId, 'POINTS')
await addCart(zeroCashSku.skuId, 'CASH')
const splitOrder = await mallCheckout('mall-split')
expect(splitOrder.status === 'PAID' && splitOrder.subOrders.length === 2, 'Cart was not split by purchase mode.')
const subOrderIds = splitOrder.subOrders.map((item) => item.id)
const pack = (await request(admin, '/api/v3/admin/mall/packages', { method: 'POST', token: adminToken,
  body: { shop_id: 1, sub_order_ids: subOrderIds, logistics_company: 'SC5 Express', tracking_no: `SC5-${suffix}` },
})).data
await request(client, `/api/v3/client/mall/packages/${pack.packageId}/receive`, { method: 'POST', token: memberToken, body: {} })
expect(sql(`SELECT CONCAT((SELECT COUNT(*) FROM mall_package_sub_order WHERE package_id=${pack.packageId}),'|',(SELECT status FROM mall_order WHERE id=${splitOrder.id}))`).at(-1) === '2|COMPLETED', 'Package merge or receipt traceability failed.')
console.log('SC5_CART_SPLIT_PACKAGE_MERGE_TRACEABILITY=PASS')

const expiringBatchId = sql(`SELECT pb.id FROM points_batch pb JOIN points_account pa ON pa.id=pb.account_id WHERE pa.member_id=${memberId} AND pb.remaining_points>pb.frozen_points ORDER BY pb.id DESC LIMIT 1;`).at(-1)
sql(`UPDATE points_batch SET expires_at=DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY), status='ACTIVE' WHERE id=${expiringBatchId};`)
const reminder1 = (await request(admin, '/api/v3/admin/points/expiry-reminders', { method: 'POST', token: adminToken, body: { shop_id: 1 } })).data
const reminder2 = (await request(admin, '/api/v3/admin/points/expiry-reminders', { method: 'POST', token: adminToken, body: { shop_id: 1 } })).data
expect(reminder1.created >= 1 && reminder2.created === 0, 'Points expiry reminder was not deduplicated.')
expect(sql(`SELECT COUNT(*) FROM points_expiry_notice pen JOIN points_account pa ON pa.id=pen.account_id WHERE pa.member_id=${memberId} AND pen.days_before=1;`).at(-1) === '1', 'Expiry reminder fact is missing.')
console.log('SC5_POINTS_EXPIRY_REMINDER_DEDUP=PASS')

const couponProduct = await createProduct('COUPON-CASH', { stock: 1, cash: 20, pointsEnabled: false, comboEnabled: false })
const couponRule = (await request(admin, '/api/v3/admin/mall/coupon-rules', { method: 'POST', token: adminToken,
  body: { shop_id: 1, coupon_code: `MC-${suffix}`, name: 'SC5 商品券', coupon_type: 'PRODUCT',
    fixed_amount: 5, minimum_cash_amount: 10, product_ids: [couponProduct.productId], valid_days: 30 },
})).data
const issuedCoupon = (await request(admin, '/api/v3/admin/mall/coupons/issue', { method: 'POST', token: adminToken,
  body: { shop_id: 1, coupon_rule_id: couponRule.couponRuleId, member_id: memberId },
})).data
await addCart(couponProduct.skuId, 'CASH')
const couponOrder = await mallCheckout('mall-coupon-cash', issuedCoupon.memberCouponId)
expect(Number(couponOrder.cashAmount) === 15, 'Product-scoped coupon was not applied to eligible cash goods.')
await callback(couponOrder.payment, 'SUCCESS', 'mall-coupon-success')
await callback(couponOrder.payment, 'SUCCESS', 'mall-coupon-success')
expect(sql(`SELECT CONCAT((SELECT status FROM member_mall_coupon WHERE id=${issuedCoupon.memberCouponId}),'|',(SELECT status FROM mall_order WHERE id=${couponOrder.id}),'|',(SELECT COUNT(*) FROM mall_payment_callback_event WHERE mall_payment_id=${couponOrder.payment.id}))`).at(-1) === 'USED|PAID|1', 'Mall payment callback or coupon use was not idempotent.')
const couponPackage = (await request(admin, '/api/v3/admin/mall/packages', { method: 'POST', token: adminToken,
  body: { shop_id: 1, sub_order_ids: couponOrder.subOrders.map((item) => item.id), logistics_company: 'SC5 Express', tracking_no: `SC5-C-${suffix}` },
})).data
await request(client, `/api/v3/client/mall/packages/${couponPackage.packageId}/receive`, { method: 'POST', token: memberToken, body: {} })
expect(sql(`SELECT COUNT(*) FROM points_batch WHERE source_type='MALL_RECEIPT' AND source_reference_type='MALL_ORDER' AND source_reference_id=${couponOrder.id};`).at(-1) === '1', 'Received cash mall order did not earn points exactly once.')
console.log('SC5_MALL_CASH_COUPON_SCOPE=PASS')
console.log('SC5_MALL_PAYMENT_CALLBACK_IDEMPOTENCY=PASS')
console.log('SC5_MALL_RECEIPT_AUTO_EARN=PASS')

expect(pointMallOrder.status === 'PAID', 'Points mall order was not paid atomically.')
console.log('SC5_RUNTIME_ACCEPTANCE=PASS')
