import { spawnSync } from 'node:child_process'

const options = Object.fromEntries(process.argv.slice(2).map((entry) => {
  const [key, ...rest] = entry.replace(/^--/, '').split('=')
  return [key, rest.join('=')]
}))
const { client, admin, project, root } = options
const envFile = options['env-file']
const demoCookie = options.cookie
const accountPassword = options['account-password'] || 'Face@123'
if (!client || !admin || !project || !root || !envFile) {
  throw new Error('SC6 runtime checker requires client, admin, project, root and env-file.')
}

const suffix = Date.now().toString().slice(-9)
const phone = `138${suffix.slice(-8)}`
const key = (prefix) => `${prefix}-${suffix}`.slice(0, 96)
const expect = (condition, message) => { if (!condition) throw new Error(message) }
const value = (object, camel, snake) => object?.[camel] ?? object?.[snake]

function sql(statement) {
  const result = spawnSync('docker', [
    'compose', '--project-name', project, '--project-directory', root,
    '--env-file', envFile, '-f', `${root}/compose.yaml`,
    'exec', '-T', 'mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot face_salon',
  ], { input: statement, encoding: 'utf8' })
  if (result.status !== 0) throw new Error(`SC6 SQL assertion failed: ${result.stderr}`)
  return result.stdout.trim().split(/\r?\n/).filter(Boolean)
}

async function request(base, path, {
  method = 'GET', token, body, headers = {}, expected = [200],
} = {}) {
  const content = body === undefined ? undefined : JSON.stringify(body)
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

async function pollSql(statement, predicate, message, attempts = 20) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const current = sql(statement).at(-1)
    if (predicate(current)) return current
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(message)
}

sql(`
INSERT INTO member (tenant_id, home_shop_id, shop_id, member_no, global_member_no, name, phone, source, status)
VALUES (1, 1, 1, 'SC6-${suffix}', 'SC6-G-${suffix}', 'SC6 Runtime Member', '${phone}', 'SC6_RUNTIME', 'ACTIVE');
SET @member_id := LAST_INSERT_ID();
INSERT INTO member_shop_profile (tenant_id, member_id, shop_id, source, status)
VALUES (1, @member_id, 1, 'SC6_RUNTIME', 'ACTIVE');
INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, phone, password_hash, role_code, member_id, status)
SELECT 1, 1, 1, 'sc6-member-${suffix}', 'SC6 Runtime Member', '${phone}', password_hash, 'MEMBER', @member_id, 'ACTIVE'
FROM account WHERE username = 'admin' LIMIT 1;
SET @member_account_id := LAST_INSERT_ID();
INSERT INTO account_shop_role (tenant_id, account_id, shop_id, role_id, status)
SELECT 1, @member_account_id, 1, id, 'ACTIVE' FROM role_definition WHERE tenant_id = 1 AND role_code = 'MEMBER';

INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, password_hash, role_code, status)
SELECT 1, 1, 1, 'sc6-operator-${suffix}', 'SC6 Ordinary Admin', password_hash, 'ADMIN', 'ACTIVE'
FROM account WHERE username = 'admin' LIMIT 1;
SET @operator_id := LAST_INSERT_ID();
INSERT INTO account_shop_role (tenant_id, account_id, shop_id, role_id, status)
SELECT 1, @operator_id, 1, id, 'ACTIVE' FROM role_definition WHERE tenant_id = 1 AND role_code = 'ADMIN';

SET @staff_id := (SELECT id FROM staff WHERE tenant_id = 1 AND shop_id = 1 ORDER BY id LIMIT 1);
SET @admin_id := (SELECT id FROM account WHERE tenant_id = 1 AND username = 'admin' LIMIT 1);
INSERT INTO appointment (tenant_id, shop_id, appointment_no, member_id, staff_id, start_at, end_at,
  occupied_start_at, occupied_end_at, status, fulfillment_status, source, terms_confirmed_at, completed_at)
VALUES (1, 1, 'SC6-A-${suffix}', @member_id, @staff_id, DATE_SUB(NOW(3), INTERVAL 2 HOUR),
  DATE_SUB(NOW(3), INTERVAL 1 HOUR), DATE_SUB(NOW(3), INTERVAL 2 HOUR),
  DATE_SUB(NOW(3), INTERVAL 1 HOUR), 'COMPLETED', 'PENDING_CUSTOMER_CONFIRMATION', 'ONLINE', NOW(3), NOW(3));
SET @appointment_id := LAST_INSERT_ID();
INSERT INTO service_record (tenant_id, record_no, shop_id, appointment_id, member_id, staff_id,
  actual_start_at, actual_end_at, service_summary, status, fulfillment_status, created_by, updated_by)
VALUES (1, 'SC6-SR-${suffix}', 1, @appointment_id, @member_id, @staff_id,
  DATE_SUB(NOW(3), INTERVAL 2 HOUR), DATE_SUB(NOW(3), INTERVAL 1 HOUR),
  'SC6 runtime care', 'COMPLETED', 'PENDING_CUSTOMER_CONFIRMATION', @admin_id, @admin_id);
SET @service_id := LAST_INSERT_ID();
INSERT INTO customer_confirmation (tenant_id, shop_id, appointment_id, service_record_id, member_id,
  confirmation_type, status, due_at, version, created_by, updated_by)
VALUES (1, 1, @appointment_id, @service_id, @member_id, 'SERVICE_RESULT', 'PENDING',
  DATE_ADD(NOW(3), INTERVAL 24 HOUR), 0, @admin_id, @admin_id);
`)

const memberId = Number(sql(`SELECT id FROM member WHERE phone='${phone}';`).at(-1))
const serviceId = Number(sql(`SELECT id FROM service_record WHERE record_no='SC6-SR-${suffix}';`).at(-1))
const confirmationId = Number(sql(`SELECT id FROM customer_confirmation WHERE service_record_id=${serviceId};`).at(-1))
const adminToken = value((await request(admin, '/api/v3/auth/admin-login', {
  method: 'POST', body: { username: 'admin', password: accountPassword },
})).data, 'accessToken', 'access_token')
const operatorToken = value((await request(admin, '/api/v3/auth/admin-login', {
  method: 'POST', body: { username: `sc6-operator-${suffix}`, password: accountPassword },
})).data, 'accessToken', 'access_token')
const memberToken = value((await request(client, '/api/v3/client/identity/password-login', {
  method: 'POST', body: { phone, password: accountPassword },
})).data, 'accessToken', 'access_token')
expect(adminToken && operatorToken && memberToken, 'SC6 login tokens were not issued.')

const confirmKey = key('confirm')
const confirmed = (await request(client, `/api/v3/customer-confirmations/${confirmationId}/action`, {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': confirmKey },
  body: { action: 'CONFIRMED', version: 0 },
})).data
const replayed = (await request(client, `/api/v3/customer-confirmations/${confirmationId}/action`, {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': confirmKey },
  body: { action: 'CONFIRMED', version: 0 },
})).data
expect(confirmed.status === 'CONFIRMED' && replayed.id === confirmed.id, 'Customer confirmation replay failed.')
expect(sql(`SELECT CONCAT((SELECT COUNT(*) FROM service_fulfillment_fact WHERE confirmation_id=${confirmationId}),'|',(SELECT fulfillment_status FROM service_record WHERE id=${serviceId}))`).at(-1) === '1|COMPLETED', 'Repeated completion created duplicate fulfillment or failed to complete.')
console.log('SC6_CUSTOMER_CONFIRMATION_IDEMPOTENCY=PASS')

sql(`
SET @member_id := ${memberId};
SET @staff_id := (SELECT id FROM staff WHERE tenant_id = 1 AND shop_id = 1 ORDER BY id LIMIT 1);
SET @admin_id := (SELECT id FROM account WHERE tenant_id = 1 AND username = 'admin' LIMIT 1);
INSERT INTO appointment (tenant_id, shop_id, appointment_no, member_id, staff_id, start_at, end_at,
  occupied_start_at, occupied_end_at, status, fulfillment_status, source, terms_confirmed_at, completed_at)
VALUES (1, 1, 'SC6-AUTO-A-${suffix}', @member_id, @staff_id, DATE_SUB(NOW(3), INTERVAL 4 HOUR),
  DATE_SUB(NOW(3), INTERVAL 3 HOUR), DATE_SUB(NOW(3), INTERVAL 4 HOUR),
  DATE_SUB(NOW(3), INTERVAL 3 HOUR), 'COMPLETED', 'PENDING_CUSTOMER_CONFIRMATION', 'ONLINE', NOW(3), NOW(3));
SET @appointment_id := LAST_INSERT_ID();
INSERT INTO service_record (tenant_id, record_no, shop_id, appointment_id, member_id, staff_id,
  actual_start_at, actual_end_at, service_summary, status, fulfillment_status, created_by, updated_by)
VALUES (1, 'SC6-AUTO-SR-${suffix}', 1, @appointment_id, @member_id, @staff_id,
  DATE_SUB(NOW(3), INTERVAL 4 HOUR), DATE_SUB(NOW(3), INTERVAL 3 HOUR),
  'SC6 auto confirmation', 'COMPLETED', 'PENDING_CUSTOMER_CONFIRMATION', @admin_id, @admin_id);
SET @service_id := LAST_INSERT_ID();
INSERT INTO customer_confirmation (tenant_id, shop_id, appointment_id, service_record_id, member_id,
  confirmation_type, status, due_at, version, created_by, updated_by)
VALUES (1, 1, @appointment_id, @service_id, @member_id, 'SERVICE_RESULT', 'PENDING',
  DATE_SUB(NOW(3), INTERVAL 1 SECOND), 0, @admin_id, @admin_id);
`)
const autoServiceId = Number(sql(`SELECT id FROM service_record WHERE record_no='SC6-AUTO-SR-${suffix}';`).at(-1))
const autoConfirmationId = Number(sql(`SELECT id FROM customer_confirmation WHERE service_record_id=${autoServiceId};`).at(-1))
await pollSql(`SELECT CONCAT(status,'|',finalization_source) FROM customer_confirmation WHERE id=${autoConfirmationId};`,
  (current) => current === 'SYSTEM_AUTO_CONFIRMED|SYSTEM', '24-hour system auto confirmation did not run.')
expect(sql(`SELECT COUNT(*) FROM service_fulfillment_fact WHERE confirmation_id=${autoConfirmationId};`).at(-1) === '1', 'Auto confirmation fulfillment fact is missing.')
console.log('SC6_24H_AUTO_CONFIRMATION=PASS')

const review = (await request(client, '/api/v3/reviews', {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': key('review-create') },
  body: { service_record_id: serviceId, staff_rating: 2, effect_rating: 4,
    environment_rating: 4, visibility: 'PUBLIC', content: '希望门店改善服务沟通。', wants_contact: false },
})).data
expect(Number(review.averageRating) === 3.3 && review.moderationStatus === 'PENDING' && review.afterSaleCaseId,
  'Review average, moderation or low-score after-sale linkage is incorrect.')
const reviewId = review.id
const afterSaleId = review.afterSaleCaseId
const afterSaleVersion = Number(sql(`SELECT version FROM after_sale_case WHERE id=${afterSaleId};`).at(-1))
const approved = (await request(admin, `/api/v3/admin/reviews/${reviewId}/moderation`, {
  method: 'POST', token: adminToken, headers: { 'Idempotency-Key': key('review-approve') },
  body: { shop_id: 1, version: review.version, action: 'APPROVE', note: '内容合规' },
})).data
expect(approved.moderationStatus === 'APPROVED', 'Public review moderation failed.')
const edited = (await request(client, `/api/v3/reviews/${reviewId}`, {
  method: 'PUT', token: memberToken, headers: { 'Idempotency-Key': key('review-edit') },
  body: { service_record_id: serviceId, version: approved.version, staff_rating: 5,
    effect_rating: 5, environment_rating: 5, visibility: 'PUBLIC', content: '沟通后已改善。', wants_contact: false },
})).data
expect(edited.currentVersionNo === 2 && edited.moderationStatus === 'PENDING', 'Edited public review was not versioned and hidden for re-review.')
const deleted = (await request(client, `/api/v3/reviews/${reviewId}?version=${edited.version}`, {
  method: 'DELETE', token: memberToken, headers: { 'Idempotency-Key': key('review-delete') },
})).data
expect(deleted.deletedAt, 'Review was not logically deleted.')
expect(sql(`SELECT CONCAT((SELECT COUNT(*) FROM service_review_version WHERE review_id=${reviewId}),'|',(SELECT COUNT(*) FROM after_sale_case WHERE id=${afterSaleId}),'|',(SELECT COUNT(*) FROM audit_log WHERE entity_type='SERVICE_REVIEW' AND entity_id=${reviewId}))`).at(-1) === '3|1|4', 'Review deletion removed history, after-sale linkage or audit evidence.')
console.log('SC6_REVIEW_VERSION_MODERATION_AUDIT=PASS')

await request(admin, `/api/v3/after-sales/cases/${afterSaleId}/solution`, {
  method: 'POST', token: operatorToken, expected: [403], headers: { 'Idempotency-Key': key('risk-denied') },
  body: { shop_id: 1, version: afterSaleVersion, resolution_type: 'COMPENSATION_COUPON',
    risk_amount: 501, note: 'ordinary admin threshold test', evidence: { source: 'SC6_RUNTIME' } },
})
expect(sql(`SELECT status FROM after_sale_case WHERE id=${afterSaleId};`).at(-1) === 'OPEN', 'Threshold rejection changed the after-sale case.')
const decision = (await request(admin, `/api/v3/after-sales/cases/${afterSaleId}/solution`, {
  method: 'POST', token: adminToken, headers: { 'Idempotency-Key': key('risk-approved') },
  body: { shop_id: 1, version: afterSaleVersion, resolution_type: 'COMPENSATION_COUPON',
    risk_amount: 501, note: 'super admin approved compensation', evidence: { source: 'SC6_RUNTIME' } },
})).data
expect(decision.status === 'WAITING_CUSTOMER' && decision.requiresSuperAdmin === true,
  'Super-admin threshold decision did not enter customer confirmation.')
const reopened = (await request(client, `/api/v3/after-sales/cases/${afterSaleId}/customer-response`, {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': key('reopen-one') },
  body: { shop_id: 1, version: decision.version, accepted: false, reason: '需要进一步处理' },
})).data
expect(reopened.status === 'REOPENED' && reopened.reopenCount === 1, 'First after-sale reopen failed.')
const secondDecision = (await request(admin, `/api/v3/after-sales/cases/${afterSaleId}/solution`, {
  method: 'POST', token: adminToken, headers: { 'Idempotency-Key': key('second-solution') },
  body: { shop_id: 1, version: reopened.version, resolution_type: 'REDO_SERVICE',
    risk_amount: 0, note: '安排一次复做护理', evidence: { source: 'SC6_RUNTIME' } },
})).data
await request(client, `/api/v3/after-sales/cases/${afterSaleId}/customer-response`, {
  method: 'POST', token: memberToken, expected: [409], headers: { 'Idempotency-Key': key('reopen-two-denied') },
  body: { shop_id: 1, version: secondDecision.version, accepted: false, reason: '再次重开应被拒绝' },
})
await request(client, `/api/v3/after-sales/cases/${afterSaleId}/customer-response`, {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': key('accept-final') },
  body: { shop_id: 1, version: secondDecision.version, accepted: true, reason: '接受方案' },
})
expect(Number(sql(`SELECT COUNT(*) FROM after_sale_asset_ledger WHERE case_id=${afterSaleId};`).at(-1)) === 2,
  'After-sale asset decisions were not append-only.')
console.log('SC6_AFTERSALE_THRESHOLD_48H_REOPEN=PASS')

sql(`
INSERT INTO after_sale_case (tenant_id, shop_id, case_no, member_id, service_record_id,
  category, origin_type, priority, summary, entry_deadline_at, status, resolution_type,
  resolution_note, customer_response_due_at, create_idempotency_key, create_request_hash, created_by)
SELECT 1, 1, 'SC6-AUTO-CLOSE-${suffix}', ${memberId}, ${serviceId}, 'OTHER', 'MANUAL', 'NORMAL',
  'SC6 auto-close runtime', DATE_ADD(NOW(3), INTERVAL 7 DAY), 'WAITING_CUSTOMER', 'REJECT',
  'runtime auto-close', DATE_SUB(NOW(3), INTERVAL 1 SECOND), 'auto-close-${suffix}', SHA2('auto-close-${suffix}',256), id
FROM account WHERE tenant_id=1 AND username='admin' LIMIT 1;
`)
const autoCloseId = Number(sql(`SELECT id FROM after_sale_case WHERE case_no='SC6-AUTO-CLOSE-${suffix}';`).at(-1))
await pollSql(`SELECT status FROM after_sale_case WHERE id=${autoCloseId};`,
  (current) => current === 'CLOSED', '48-hour system auto close did not run.')
expect(sql(`SELECT COUNT(*) FROM after_sale_case_log WHERE case_id=${autoCloseId} AND action='SYSTEM_AUTO_CLOSED';`).at(-1) === '1', 'Auto-close audit log is missing.')
console.log('SC6_48H_AUTO_CLOSE=PASS')

await request(admin, '/api/v3/admin/points/grants', {
  method: 'POST', token: adminToken, headers: { 'Idempotency-Key': key('return-points') },
  body: { shop_id: 1, member_id: memberId, points: 1000, source_type: 'ADMIN',
    reference_type: 'SC6_RUNTIME', reason: 'return inspection gate', validity_months: 12 },
})
const product = (await request(admin, '/api/v3/admin/mall/products', {
  method: 'POST', token: adminToken, body: { shop_id: 1, category_code: `SC6-${suffix}`,
    category_name: 'SC6 runtime', product_code: `SC6-P-${suffix}`, product_type: 'PHYSICAL',
    name: 'SC6 return item', delivery_mode: 'DELIVERY', freight_template_code: 'FREE',
    separate_shipping: false, after_sale_policy: 'SEVEN_DAY', sku_code: `SC6-SKU-${suffix}`,
    spec: { stage: 'SC6' }, cash_price: 0, points_price: 100, combo_cash_price: 0,
    combo_points_price: 0, cash_enabled: false, points_enabled: true,
    combo_enabled: false, warning_threshold: 0 },
})).data
await request(admin, '/api/v3/admin/mall/inventory/adjustments', {
  method: 'POST', token: adminToken, headers: { 'Idempotency-Key': key('return-stock') },
  body: { shop_id: 1, sku_id: product.skuId, delta: 1, reason: 'SC6 return runtime stock' },
})
await request(client, '/api/v3/client/mall/cart', {
  method: 'POST', token: memberToken,
  body: { sku_id: product.skuId, purchase_mode: 'POINTS', quantity: 1, delivery_mode: 'DELIVERY' },
})
const mallOrder = (await request(client, '/api/v3/client/mall/checkouts', {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': key('return-order') },
  body: { payment_method: 'DEMO_MOCK' },
})).data
expect(mallOrder.status === 'PAID', 'Points mall order was not paid atomically.')
const mallOrderItemId = Number(sql(`SELECT id FROM mall_order_item WHERE mall_order_id=${mallOrder.id} ORDER BY id LIMIT 1;`).at(-1))
const pack = (await request(admin, '/api/v3/admin/mall/packages', {
  method: 'POST', token: adminToken,
  body: { shop_id: 1, sub_order_ids: mallOrder.subOrders.map((item) => item.id),
    logistics_company: 'SC6 Express', tracking_no: `SC6-${suffix}` },
})).data
await request(client, `/api/v3/client/mall/packages/${pack.packageId}/receive`, {
  method: 'POST', token: memberToken, body: {},
})
const returnRequest = (await request(client, '/api/v3/mall/returns', {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': key('return-create') },
  body: { shop_id: 1, mall_order_id: mallOrder.id, reason_code: 'QUALITY',
    reason_detail: 'SC6 runtime quality return', items: [{ order_item_id: mallOrderItemId, quantity: 1 }] },
})).data
expect(sql(`SELECT COUNT(*) FROM after_sale_asset_ledger WHERE case_id=${returnRequest.afterSaleCaseId};`).at(-1) === '0', 'Assets changed before return approval.')
const reviewedReturn = (await request(admin, `/api/v3/mall/returns/${returnRequest.id}/review`, {
  method: 'POST', token: adminToken, headers: { 'Idempotency-Key': key('return-review') },
  body: { shop_id: 1, version: returnRequest.version, approved: true, reason: '同意寄回' },
})).data
const shippedReturn = (await request(client, `/api/v3/mall/returns/${returnRequest.id}/ship`, {
  method: 'POST', token: memberToken, headers: { 'Idempotency-Key': key('return-ship') },
  body: { shop_id: 1, version: reviewedReturn.version, tracking_no: `RT-${suffix}` },
})).data
expect(shippedReturn.status === 'PENDING_INSPECTION', 'Return did not enter inspection pending.')
expect(sql(`SELECT COUNT(*) FROM after_sale_asset_ledger WHERE case_id=${returnRequest.afterSaleCaseId} AND asset_type IN ('POINTS','FREIGHT','CASH');`).at(-1) === '0', 'Points, freight or cash returned before inspection.')
const inspectedReturn = (await request(admin, `/api/v3/mall/returns/${returnRequest.id}/inspection`, {
  method: 'POST', token: adminToken, headers: { 'Idempotency-Key': key('return-inspect') },
  body: { shop_id: 1, version: shippedReturn.version, passed: true, stock_disposition: 'RESTORE',
    reason: '商品完好，验货通过', evidence: { inspection: 'SC6_RUNTIME' } },
})).data
expect(inspectedReturn.status === 'INSPECTION_PASSED', 'Return inspection did not pass.')
expect(sql(`SELECT CONCAT((SELECT COUNT(*) FROM after_sale_asset_ledger WHERE case_id=${returnRequest.afterSaleCaseId} AND asset_type='POINTS'),'|',(SELECT status FROM points_reservation WHERE reference_type='MALL_ORDER' AND reference_id=${mallOrder.id}),'|',(SELECT available_quantity FROM mall_sku_inventory WHERE sku_id=${product.skuId}))`).at(-1) === '1|RESTORED|1', 'Inspection did not restore points and inventory exactly once.')
console.log('SC6_RETURN_INSPECTION_ASSET_GATE=PASS')
console.log('SC6_RUNTIME_ACCEPTANCE=PASS')
