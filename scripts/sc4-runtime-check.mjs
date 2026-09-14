import crypto from 'node:crypto'
import { spawnSync } from 'node:child_process'

const options = Object.fromEntries(process.argv.slice(2).map((entry) => {
  const [key, ...rest] = entry.replace(/^--/, '').split('=')
  return [key, rest.join('=')]
}))
const client = options.client
const admin = options.admin
const project = options.project
const root = options.root
const envFile = options['env-file']
const secret = options.secret
const demoCookie = options.cookie
const accountPassword = options['account-password'] || 'Face@123'
if (!client || !admin || !project || !root || !envFile || !secret) {
  throw new Error('SC4 runtime checker requires client, admin, project, root, env-file and secret.')
}
const suffix = Date.now().toString().slice(-9)

function sql(statement) {
  const result = spawnSync('docker', [
    'compose', '--project-name', project, '--project-directory', root,
    '--env-file', envFile, '-f', `${root}/compose.yaml`,
    'exec', '-T', 'mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot face_salon',
  ], { input: statement, encoding: 'utf8' })
  if (result.status !== 0) throw new Error(`SC4 SQL assertion failed: ${result.stderr}`)
  return result.stdout.trim().split(/\r?\n/).filter(Boolean)
}

async function request(base, path, { method = 'GET', token, body, headers = {}, expected = [200], rawBody } = {}) {
  const content = rawBody ?? (body === undefined ? undefined : JSON.stringify(body))
  const response = await fetch(`${base}${path}`, {
    method,
    headers: {
      ...(content === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(demoCookie ? { Cookie: demoCookie } : {}),
      ...headers,
    },
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
  const amount = Number(payment.amount).toFixed(2)
  const eventId = `${eventPrefix}-${suffix}`
  const body = JSON.stringify({
    event_id: eventId,
    payment_no: paymentNo,
    status,
    amount,
    external_transaction_no: `DEMO-TXN-${eventId}`,
    channel_status: status === 'SUCCESS' ? 'DEMO_CONFIRMED' : 'DEMO_FAILED',
    ...(status === 'FAILED' ? { failure_code: 'SC4_SYNTHETIC_FAILURE' } : {}),
  })
  const timestamp = Math.floor(Date.now() / 1000)
  const signature = crypto.createHmac('sha256', secret).update(`${timestamp}.${body}`).digest('hex')
  const headers = {
    'X-Payment-Timestamp': String(timestamp),
    'X-Payment-Event-Id': eventId,
    'X-Payment-Signature': signature,
  }
  await request(client, '/api/v3/payment-channels/DEMO_MOCK/callbacks', {
    method: 'POST', headers, rawBody: body,
  })
  return request(client, '/api/v3/payment-channels/DEMO_MOCK/callbacks', {
    method: 'POST', headers, rawBody: body,
  })
}

const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10)
sql(`
SET @service_id := (SELECT ss.service_id FROM staff_service ss JOIN service_item si ON si.id = ss.service_id AND si.status = 'ACTIVE' JOIN staff st ON st.id = ss.staff_id AND st.status = 'ACTIVE' WHERE ss.enabled = 1 ORDER BY ss.service_id, ss.staff_id LIMIT 1);
SET @staff_id := (SELECT ss.staff_id FROM staff_service ss JOIN staff st ON st.id = ss.staff_id AND st.status = 'ACTIVE' WHERE ss.service_id = @service_id AND ss.enabled = 1 ORDER BY ss.staff_id LIMIT 1);
SET @admin_id := (SELECT id FROM account WHERE username = 'admin' LIMIT 1);
UPDATE service_item SET slot_interval_minutes = 60, buffer_before_minutes = 0, buffer_after_minutes = 0, minimum_advance_minutes = 0, same_day_booking_allowed = 1, booking_terms_version = booking_terms_version + 1 WHERE id = @service_id;
INSERT IGNORE INTO staff_schedule (tenant_id, shop_id, staff_id, schedule_date, start_time, end_time, schedule_type, remark)
SELECT st.tenant_id, 1, st.id, '${tomorrow}', '09:00:00', '18:00:00', 'WORK', 'SC4 runtime' FROM staff st WHERE st.id = @staff_id;
INSERT INTO member (tenant_id, home_shop_id, shop_id, member_no, global_member_no, name, phone, source, status)
VALUES (1, 1, 1, 'SC4-${suffix}', 'SC4-G-${suffix}', 'SC4 Runtime Member', '138${suffix.slice(-8)}', 'SC4_RUNTIME', 'ACTIVE');
SET @member_id := LAST_INSERT_ID();
INSERT INTO member_shop_profile (tenant_id, member_id, shop_id, source, status)
VALUES (1, @member_id, 1, 'SC4_RUNTIME', 'ACTIVE');
INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, phone, password_hash, role_code, member_id, status)
SELECT 1, 1, 1, 'sc4-member-${suffix}', 'SC4 Runtime Member', '138${suffix.slice(-8)}', password_hash, 'MEMBER', @member_id, 'ACTIVE' FROM account WHERE username = 'admin' LIMIT 1;
SET @account_id := LAST_INSERT_ID();
INSERT INTO account_shop_role (tenant_id, account_id, shop_id, role_id, status)
SELECT 1, @account_id, 1, id, 'ACTIVE' FROM role_definition WHERE tenant_id = 1 AND role_code = 'MEMBER';
`)
const lastFixture = sql('SELECT CONCAT(ss.service_id,"|",ss.staff_id) FROM staff_service ss JOIN service_item si ON si.id=ss.service_id AND si.status="ACTIVE" JOIN staff st ON st.id=ss.staff_id AND st.status="ACTIVE" WHERE ss.enabled=1 ORDER BY ss.service_id,ss.staff_id LIMIT 1;').at(-1)
const [serviceId, staffId] = lastFixture.split('|').map(Number)

const adminToken = value((await request(admin, '/api/v3/auth/admin-login', { method: 'POST', body: { username: 'admin', password: accountPassword } })).data, 'accessToken', 'access_token')
const memberToken = value((await request(client, '/api/v3/client/identity/password-login', { method: 'POST', body: { phone: `138${suffix.slice(-8)}`, password: accountPassword } })).data, 'accessToken', 'access_token')
expect(adminToken && memberToken, 'SC4 login tokens were not issued.')

const channels = (await request(admin, '/api/v3/benefits/payment-channels?shop_id=1', { token: adminToken })).data
const demoChannel = channels.find((item) => item.code === 'DEMO_MOCK')
expect(demoChannel?.configured === true && !JSON.stringify(channels).includes(secret), 'Payment configuration is unavailable or leaked a secret.')
console.log('SC4_MASKED_PAYMENT_CONFIGURATION=PASS')

async function createCard(cardType, extra = {}) {
  return (await request(admin, '/api/v3/benefits/card-products', {
    method: 'POST', token: adminToken, body: {
      shop_id: 1, package_code: `${cardType}-${suffix}`, name: `SC4 ${cardType}`,
      card_type: cardType, sale_price: 100, principal_amount: 0, gift_amount: 0,
      minimum_spend: 0, validity_days: 365, scope_json: '{}', items: [], ...extra,
    },
  })).data
}
const combo = await createCard('COMBO_TIMES', { items: [{ service_id: serviceId, quantity: 5 }] })
const stored = await createCard('STORED_VALUE', { sale_price: 1000, principal_amount: 1000, gift_amount: 100 })
const discountCard = await createCard('DISCOUNT', { discount_percent: 90, maximum_savings: 50, usage_limit: 10 })
expect(combo && stored && discountCard, 'Three SC4 card types were not created.')
console.log('SC4_THREE_CARD_TYPES=PASS')

async function issueCard(productId, source, marker) {
  return (await request(admin, '/api/v3/benefits/cards/issue', {
    method: 'POST', token: adminToken, headers: { 'Idempotency-Key': key(`issue-${marker}`) },
    body: { shop_id: 1, member_id: Number(sql(`SELECT id FROM member WHERE phone='138${suffix.slice(-8)}';`).at(-1)), package_product_id: productId, source_type: source, source_reference: marker },
  })).data
}
const comboId = value(await issueCard(value(combo, 'id', 'id'), 'OFFLINE_SALE', 'offline'), 'id', 'id')
const storedId = value(await issueCard(value(stored, 'id', 'id'), 'GIFT', 'gift'), 'id', 'id')
await issueCard(value(discountCard, 'id', 'id'), 'REISSUE', 'reissue')
expect(sql("SELECT COUNT(DISTINCT source_type) FROM package_instance WHERE source_type IN ('OFFLINE_SALE','GIFT','REISSUE');").at(-1) === '3', 'Manual card sources were not persisted.')
console.log('SC4_CARD_SOURCES_AND_IDEMPOTENT_ISSUE=PASS')

async function createCoupon(type, extra = {}) {
  return (await request(admin, '/api/v3/benefits/coupon-templates', {
    method: 'POST', token: adminToken, body: {
      shop_id: 1, template_code: `${type}-${suffix}`, name: `SC4 ${type}`,
      coupon_type: type, threshold_amount: 0, benefit_value: 10,
      validity_days: 30, return_on_full_refund: true, ...extra,
    },
  })).data
}
await createCoupon('THRESHOLD_REDUCTION', { threshold_amount: 50, benefit_value: 10 })
const cashCoupon = await createCoupon('CASH', { benefit_value: 99999 })
await createCoupon('DISCOUNT', { benefit_value: 90 })
await createCoupon('SERVICE_EXPERIENCE', { service_id: serviceId, benefit_value: 0 })
expect(sql('SELECT COUNT(DISTINCT coupon_type) FROM coupon_template;').at(-1) === '4', 'Four coupon types were not persisted.')
const memberId = Number(sql(`SELECT id FROM member WHERE phone='138${suffix.slice(-8)}';`).at(-1))
const issuedCoupon = (await request(admin, '/api/v3/benefits/coupons/issue', {
  method: 'POST', token: adminToken, headers: { 'Idempotency-Key': key('coupon-zero') },
  body: { shop_id: 1, member_id: memberId, template_id: value(cashCoupon, 'id', 'id'), source_type: 'ADMIN_DIRECT', source_reference: 'zero order' },
})).data
console.log('SC4_FOUR_COUPON_TYPES=PASS')

async function hold(hour) {
  return (await request(client, '/api/v3/booking/locks', {
    method: 'POST', token: memberToken,
    body: { shop_id: 1, service_id: serviceId, staff_id: staffId, assignment_mode: 'SPECIFIED', start_at: `${tomorrow}T${hour}:00`, bypass_minimum_advance: false },
  })).data
}
const zeroHold = await hold('10:00')
const zeroQuote = (await request(client, '/api/v3/checkout/quotes', { method: 'POST', token: memberToken, body: { lock_token: value(zeroHold, 'lockToken', 'lock_token') } })).data
expect(value(zeroQuote, 'defaultSelection', 'default_selection') === 'NONE', 'Checkout did not default to no discount.')
expect(value(zeroQuote, 'points', 'points')?.available === false, 'SC5 points leaked into SC4.')
const zeroOption = value(zeroQuote, 'discountOptions', 'discount_options').find((item) => value(item, 'referenceId', 'reference_id') === value(issuedCoupon, 'id', 'id'))
expect(Number(value(zeroOption, 'payableAmount', 'payable_amount')) === 0, 'Full coupon did not produce a zero payable quote.')
const zeroKey = key('zero-checkout')
const zeroBody = { lock_token: value(zeroHold, 'lockToken', 'lock_token'), selection_type: 'COUPON', selection_reference_id: value(issuedCoupon, 'id', 'id'), payment_method: 'ZERO_AMOUNT', member_note: 'SC4 zero order' }
const zeroOrder = (await request(client, '/api/v3/checkouts', { method: 'POST', token: memberToken, headers: { 'Idempotency-Key': zeroKey }, body: zeroBody })).data
const zeroReplay = (await request(client, '/api/v3/checkouts', { method: 'POST', token: memberToken, headers: { 'Idempotency-Key': zeroKey }, body: zeroBody })).data
expect(value(zeroOrder, 'orderId', 'order_id') === value(zeroReplay, 'orderId', 'order_id'), 'Zero checkout idempotency failed.')
expect(sql(`SELECT CONCAT(pt.payment_method,'|',pt.amount,'|',a.status,'|',mc.status) FROM payment_transaction pt JOIN sales_order so ON so.id=pt.order_id JOIN appointment a ON a.id=so.appointment_id JOIN member_coupon mc ON mc.used_order_id=so.id WHERE so.id=${value(zeroOrder, 'orderId', 'order_id')};`).at(-1) === 'ZERO_AMOUNT|0.00|CONFIRMED|USED', 'Zero order facts are incomplete.')
console.log('SC4_ZERO_AMOUNT_ORDER_AND_COUPON_LEDGER=PASS')

const onlineQuote = (await request(client, '/api/v3/checkout/quotes', { method: 'POST', token: memberToken, body: { package_product_id: value(combo, 'id', 'id') } })).data
expect(value(onlineQuote, 'defaultSelection', 'default_selection') === 'NONE', 'Card checkout changed the default discount selection.')
const onlineOrder = (await request(client, '/api/v3/checkouts', {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': key('online-card') },
  body: { package_product_id: value(combo, 'id', 'id'), selection_type: 'NONE', payment_method: 'DEMO_MOCK' },
})).data
await callback(onlineOrder.payment, 'SUCCESS', 'online-success')
const onlineOrderId = value(onlineOrder, 'orderId', 'order_id')
expect(sql(`SELECT CONCAT((SELECT COUNT(*) FROM checkout_fulfillment WHERE order_id=${onlineOrderId}),'|',(SELECT COUNT(*) FROM package_instance WHERE source_order_id=${onlineOrderId} AND source_type='ONLINE_PURCHASE'),'|',(SELECT COUNT(*) FROM payment_callback_event WHERE payment_id=(SELECT id FROM payment_transaction WHERE order_id=${onlineOrderId} LIMIT 1)))`).at(-1) === '1|1|1', 'Duplicate success callback duplicated fulfillment or card issuance.')
console.log('SC4_DUPLICATE_CALLBACK_SINGLE_FULFILLMENT=PASS')

const orderCountBefore = sql('SELECT COUNT(*) FROM sales_order;').at(-1)
await request(client, '/api/v3/checkouts', {
  method: 'POST', token: memberToken, expected: [503], headers: { 'Idempotency-Key': key('unconfigured-wechat') },
  body: { package_product_id: value(combo, 'id', 'id'), selection_type: 'NONE', payment_method: 'WECHAT' },
})
expect(sql('SELECT COUNT(*) FROM sales_order;').at(-1) === orderCountBefore, 'Unconfigured real payment channel changed business state.')
console.log('SC4_UNCONFIGURED_REAL_CHANNEL_NO_SIDE_EFFECT=PASS')

const storedHold = await hold('11:00')
const storedOrder = (await request(client, '/api/v3/checkouts', {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': key('stored-checkout') },
  body: { lock_token: value(storedHold, 'lockToken', 'lock_token'), selection_type: 'NONE', payment_method: 'STORED_VALUE', stored_value_card_id: storedId },
})).data
const storedOrderId = value(storedOrder, 'orderId', 'order_id')
expect(sql(`SELECT status FROM benefit_reservation WHERE order_id=${storedOrderId};`).at(-1) === 'RESERVED', 'Stored value was not frozen before service.')
await request(admin, `/api/v3/benefits/orders/${storedOrderId}/consume`, { method: 'POST', token: adminToken, body: { shop_id: 1 } })
const storedInvariant = sql(`SELECT CONCAT(br.status,'|',svb.principal_total,'|',svb.gift_total,'|',svb.principal_remaining,'|',svb.gift_remaining,'|',svb.principal_frozen,'|',svb.gift_frozen) FROM benefit_reservation br JOIN stored_value_batch svb ON svb.id=br.stored_value_batch_id WHERE br.order_id=${storedOrderId};`).at(-1)
expect(storedInvariant?.startsWith('CONSUMED|1000.00|100.00|') && storedInvariant.endsWith('|0.00|0.00'), 'Stored principal/gift consumption invariant failed.')
console.log('SC4_STORED_VALUE_FREEZE_CONSUME_LEDGER=PASS')

const comboHold = await hold('13:00')
const comboQuote = (await request(client, '/api/v3/checkout/quotes', { method: 'POST', token: memberToken, body: { lock_token: value(comboHold, 'lockToken', 'lock_token') } })).data
const comboOption = value(comboQuote, 'comboCards', 'combo_cards').find((item) => value(item, 'id', 'id') === comboId)
expect(Number(value(comboOption, 'availableQuantity', 'available_quantity')) === 5, 'Issued combo card was not offered for its service.')
const comboOrder = (await request(client, '/api/v3/checkouts', {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': key('combo-checkout') },
  body: { lock_token: value(comboHold, 'lockToken', 'lock_token'), selection_type: 'NONE', payment_method: 'ZERO_AMOUNT', combo_card_id: comboId },
})).data
const comboOrderId = value(comboOrder, 'orderId', 'order_id')
expect(sql(`SELECT CONCAT(br.status,'|',pii.remaining_quantity,'|',pii.frozen_quantity,'|',pt.payment_method,'|',pt.amount) FROM benefit_reservation br JOIN package_instance_item pii ON pii.id=br.package_instance_item_id JOIN payment_transaction pt ON pt.order_id=br.order_id WHERE br.order_id=${comboOrderId};`).at(-1) === 'RESERVED|5.0000|1.0000|ZERO_AMOUNT|0.00', 'Combo checkout did not freeze exactly one use.')
await request(admin, `/api/v3/benefits/orders/${comboOrderId}/consume`, { method: 'POST', token: adminToken, body: { shop_id: 1 } })
expect(sql(`SELECT CONCAT(br.status,'|',pii.remaining_quantity,'|',pii.frozen_quantity,'|',(SELECT COUNT(*) FROM package_ledger pl WHERE pl.package_instance_item_id=pii.id AND pl.business_key='WRITE_OFF:ORDER:${comboOrderId}')) FROM benefit_reservation br JOIN package_instance_item pii ON pii.id=br.package_instance_item_id WHERE br.order_id=${comboOrderId};`).at(-1) === 'CONSUMED|4.0000|0.0000|1', 'Combo use was not consumed exactly once after completion.')
console.log('SC4_COMBO_TIME_FREEZE_CONSUME_LEDGER=PASS')

const failedHold = await hold('12:00')
const failedQuote = (await request(client, '/api/v3/checkout/quotes', { method: 'POST', token: memberToken, body: { lock_token: value(failedHold, 'lockToken', 'lock_token') } })).data
const discountOption = value(failedQuote, 'discountOptions', 'discount_options').find((item) => value(item, 'selectionType', 'selection_type') === 'DISCOUNT_CARD')
expect(discountOption, 'Issued discount card was not offered.')
const failedOrder = (await request(client, '/api/v3/checkouts', {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': key('failed-discount') },
  body: { lock_token: value(failedHold, 'lockToken', 'lock_token'), selection_type: 'DISCOUNT_CARD', selection_reference_id: value(discountOption, 'referenceId', 'reference_id'), payment_method: 'DEMO_MOCK' },
})).data
await callback(failedOrder.payment, 'FAILED', 'discount-failed')
expect(sql(`SELECT CONCAT(so.status,'|',btl.status,'|',pi.usage_count) FROM sales_order so JOIN order_pricing_decision opd ON opd.order_id=so.id JOIN booking_time_lock btl ON btl.id=opd.booking_time_lock_id JOIN package_instance pi ON pi.id=opd.discount_card_instance_id WHERE so.id=${value(failedOrder, 'orderId', 'order_id')};`).at(-1) === 'VOID|RELEASED|0', 'Failed payment did not release booking and discount-card usage.')
console.log('SC4_FAILED_PAYMENT_FULL_RELEASE=PASS')

expect(comboId > 0, 'Combo card issue fact is missing.')
console.log('SC4_RUNTIME_ACCEPTANCE=PASS')
