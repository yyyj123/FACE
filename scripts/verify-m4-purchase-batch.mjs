import { spawnSync } from 'node:child_process'

const options = Object.fromEntries(
  process.argv.slice(2).map((entry) => {
    const [key, ...parts] = entry.replace(/^--/, '').split('=')
    return [key, parts.join('=')]
  }),
)

const backend = options.backend ?? 'http://127.0.0.1:8193/face-next'
const dbPort = Number(options['db-port'] ?? 3321)
const dbPassword = options['db-password']
const mysql = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
const suffix = Date.now().toString().slice(-10)

if (!dbPassword) throw new Error('M4 purchase verifier requires database password.')

function sql(statement) {
  const result = spawnSync(
    mysql,
    [
      '--protocol=TCP',
      '--host=127.0.0.1',
      `--port=${dbPort}`,
      '--user=root',
      '--batch',
      '--skip-column-names',
      '--default-character-set=utf8mb4',
      'face_salon',
    ],
    {
      input: statement,
      encoding: 'utf8',
      env: { ...process.env, MYSQL_PWD: dbPassword },
    },
  )
  if (result.status !== 0) {
    throw new Error(
      `M4 purchase database assertion failed: ${result.stderr?.trim() || 'unknown error'}`,
    )
  }
  return result.stdout.trim().split(/\r?\n/).filter(Boolean)
}

async function request(path, {
  method = 'GET',
  token,
  headers = {},
  body,
  expected = [200],
} = {}) {
  const response = await fetch(`${backend}${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  let payload
  try {
    payload = await response.json()
  } catch {
    payload = undefined
  }
  if (!expected.includes(response.status)) {
    throw new Error(
      `M4 purchase API ${method} ${path} returned ${response.status}: `
        + `${payload?.message ?? payload?.msg ?? 'no message'}`,
    )
  }
  return { status: response.status, payload, data: payload?.data }
}

async function login(username, password = 'Face@123') {
  const result = await request('/api/v3/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  if (!result.data?.access_token) {
    throw new Error(`M4 purchase login failed for ${username}.`)
  }
  return result.data.access_token
}

function key(prefix) {
  return `${prefix}-${suffix}`.slice(0, 80)
}

function number(value) {
  return Number.parseFloat(value)
}

function expectEqual(actual, expected, message) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${expected}, received ${actual}`)
  }
}

function dateKey(value) {
  if (value == null) return null
  const text = String(value)
  const matched = text.match(/^\d{4}-\d{2}-\d{2}/)
  if (matched) return matched[0]
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return text
  return parsed.toISOString().slice(0, 10)
}

const managerUsername = `m4_manager_${suffix}`
sql(`
INSERT INTO staff (
  tenant_id, home_shop_id, shop_id, staff_no, name,
  phone, job_role, level_name, status
)
VALUES (
  1, 1, 1, 'M4M${suffix}', 'M4采购审批人',
  NULL, '店长', '验收账号', 'ACTIVE'
);

INSERT INTO account (
  tenant_id, home_shop_id, shop_id, username, display_name,
  password_hash, role_code, staff_id, status, must_change_password
)
SELECT
  tenant_id, home_shop_id, shop_id, '${managerUsername}', 'M4采购审批人',
  password_hash, 'MANAGER',
  (SELECT id FROM staff WHERE tenant_id = 1 AND staff_no = 'M4M${suffix}'),
  'ACTIVE', 0
FROM account
WHERE tenant_id = 1 AND username = 'admin'
LIMIT 1;

INSERT INTO account_shop_role (
  tenant_id, account_id, shop_id, role_id, status
)
SELECT 1, a.id, 1, r.id, 'ACTIVE'
FROM account a
JOIN role_definition r ON r.role_code = 'MANAGER'
WHERE a.tenant_id = 1 AND a.username = '${managerUsername}';
`)

const adminToken = await login('admin')
const managerToken = await login(managerUsername)
const fixture = sql(`
SELECT CONCAT(p.id, '|', sb.location_id, '|', sb.quantity_on_hand)
FROM product p
JOIN stock_balance sb
  ON sb.tenant_id = p.tenant_id AND sb.product_id = p.id
JOIN stock_location sl
  ON sl.id = sb.location_id
 AND sl.tenant_id = sb.tenant_id
 AND sl.shop_id = p.shop_id
WHERE p.tenant_id = 1 AND p.shop_id = 1
  AND p.status = 'ACTIVE' AND sl.status = 'ACTIVE'
ORDER BY p.id, sb.location_id
LIMIT 1;
`)
if (fixture.length !== 1) {
  throw new Error('M4 purchase fixture requires an active product stock balance.')
}
const [productIdText, locationIdText, openingBalanceText] = fixture[0].split('|')
const productId = Number(productIdText)
const locationId = Number(locationIdText)
const openingBalance = number(openingBalanceText)

const createKey = key('m4-purchase-create')
const createBody = {
  shop_id: 1,
  supplier_name: 'M4合成测试供应商',
  expected_date: '2026-08-15',
  currency_code: 'CNY',
  remark: 'M4采购批次验收',
  lines: [{
    product_id: productId,
    quantity: 5,
    unit_cost: 12.5,
  }],
}
const created = await request('/api/v3/purchase-orders', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': createKey },
  body: createBody,
}).then((result) => result.data)
const replayedCreate = await request('/api/v3/purchase-orders', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': createKey },
  body: createBody,
}).then((result) => result.data)
expectEqual(replayedCreate.id, created.id, 'Purchase create replay changed the order')
const listedCreate = await request(
  '/api/v3/purchase-orders?shop_id=1&status=DRAFT&page=1&page_size=30',
  { token: adminToken },
).then((result) => result.data.records.find((order) => order.id === created.id))
if (!listedCreate || dateKey(listedCreate.expectedDate) !== createBody.expected_date) {
  throw new Error(
    `Purchase list changed the expected date contract: ${JSON.stringify(listedCreate?.expectedDate)}`,
  )
}
await request('/api/v3/purchase-orders', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': createKey },
  body: { ...createBody, supplier_name: '不同供应商' },
  expected: [409],
})

const submitted = await request(`/api/v3/purchase-orders/${created.id}/submit`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m4-purchase-submit') },
  body: { shop_id: 1, version: created.version },
}).then((result) => result.data)
expectEqual(submitted.status, 'SUBMITTED', 'Purchase order was not submitted')

await request(`/api/v3/purchase-orders/${created.id}/decision`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m4-purchase-self-approve') },
  body: {
    shop_id: 1,
    version: submitted.version,
    action: 'APPROVE',
  },
  expected: [403],
})
const approved = await request(`/api/v3/purchase-orders/${created.id}/decision`, {
  method: 'POST',
  token: managerToken,
  headers: { 'Idempotency-Key': key('m4-purchase-approve') },
  body: {
    shop_id: 1,
    version: submitted.version,
    action: 'APPROVE',
    decision_note: 'M4自动验收审批',
  },
}).then((result) => result.data)
expectEqual(approved.status, 'APPROVED', 'Purchase order was not approved')

const itemId = approved.items[0].id
const partialKey = key('m4-purchase-partial-receipt')
const partialBody = {
  shop_id: 1,
  version: approved.version,
  remark: 'M4部分到货',
  lines: [{
    purchase_order_item_id: itemId,
    location_id: locationId,
    received_quantity: 2,
    unit_cost: 12.5,
    vendor_batch_no: `V-${suffix}-A`,
    produced_date: '2026-07-01',
    expiry_date: '2027-06-01',
  }],
}
const partial = await request(`/api/v3/purchase-orders/${created.id}/receipts`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': partialKey },
  body: partialBody,
}).then((result) => result.data)
expectEqual(
  partial.purchaseOrderStatus,
  'PARTIALLY_RECEIVED',
  'Partial receipt did not update purchase status',
)
const partialReplay = await request(
  `/api/v3/purchase-orders/${created.id}/receipts`,
  {
    method: 'POST',
    token: adminToken,
    headers: { 'Idempotency-Key': partialKey },
    body: partialBody,
  },
).then((result) => result.data)
expectEqual(partialReplay.id, partial.id, 'Receipt replay created another receipt')
await request(`/api/v3/purchase-orders/${created.id}/receipts`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': partialKey },
  body: {
    ...partialBody,
    lines: [{ ...partialBody.lines[0], received_quantity: 1 }],
  },
  expected: [409],
})

const balanceAfterPartial = number(sql(`
SELECT quantity_on_hand
FROM stock_balance
WHERE tenant_id = 1 AND location_id = ${locationId} AND product_id = ${productId};
`)[0])
expectEqual(
  balanceAfterPartial,
  openingBalance + 2,
  'Idempotent partial receipt changed inventory more than once',
)

await request(`/api/v3/purchase-orders/${created.id}/receipts`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m4-purchase-over-receipt') },
  body: {
    shop_id: 1,
    version: partial.purchaseOrderVersion,
    lines: [{
      ...partialBody.lines[0],
      received_quantity: 4,
      vendor_batch_no: `V-${suffix}-OVER`,
    }],
  },
  expected: [409],
})
const balanceAfterRejectedOverReceipt = number(sql(`
SELECT quantity_on_hand
FROM stock_balance
WHERE tenant_id = 1 AND location_id = ${locationId} AND product_id = ${productId};
`)[0])
expectEqual(
  balanceAfterRejectedOverReceipt,
  balanceAfterPartial,
  'Rejected over-receipt changed inventory',
)

const finalReceipt = await request(
  `/api/v3/purchase-orders/${created.id}/receipts`,
  {
    method: 'POST',
    token: adminToken,
    headers: { 'Idempotency-Key': key('m4-purchase-final-receipt') },
    body: {
      shop_id: 1,
      version: partial.purchaseOrderVersion,
      remark: 'M4全部到货',
      lines: [{
        purchase_order_item_id: itemId,
        location_id: locationId,
        received_quantity: 3,
        unit_cost: 12.5,
        vendor_batch_no: `V-${suffix}-B`,
        produced_date: '2026-07-02',
        expiry_date: '2027-12-01',
      }],
    },
  },
).then((result) => result.data)
expectEqual(
  finalReceipt.purchaseOrderStatus,
  'RECEIVED',
  'Final receipt did not complete purchase order',
)

const batches = await request(
  `/api/v3/inventory/batches?shop_id=1&product_id=${productId}&location_id=${locationId}`,
  { token: adminToken },
).then((result) => result.data)
const receiptBatches = batches.filter((batch) => batch.sourceType === 'PURCHASE_RECEIPT')
expectEqual(receiptBatches.length, 2, 'Purchase receipts did not create two stock batches')
if (
  dateKey(receiptBatches[0].expiryDate) !== '2027-06-01'
  || dateKey(receiptBatches[1].expiryDate) !== '2027-12-01'
) {
  throw new Error(
    'Stock batches are not returned in deterministic FEFO order: '
      + JSON.stringify(receiptBatches.map((batch) => batch.expiryDate)),
  )
}
const undatedIndex = batches.findIndex((batch) => batch.expiryDate == null)
if (undatedIndex !== -1 && undatedIndex < receiptBatches.length) {
  throw new Error('Undated opening batch was returned before dated FEFO batches.')
}

const rollbackCreate = await request('/api/v3/purchase-orders', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m4-purchase-rollback-create') },
  body: {
    ...createBody,
    supplier_name: 'M4回滚测试供应商',
    lines: [{ ...createBody.lines[0], quantity: 1 }],
  },
}).then((result) => result.data)
const rollbackSubmitted = await request(
  `/api/v3/purchase-orders/${rollbackCreate.id}/submit`,
  {
    method: 'POST',
    token: adminToken,
    headers: { 'Idempotency-Key': key('m4-purchase-rollback-submit') },
    body: { shop_id: 1, version: rollbackCreate.version },
  },
).then((result) => result.data)
const rollbackApproved = await request(
  `/api/v3/purchase-orders/${rollbackCreate.id}/decision`,
  {
    method: 'POST',
    token: managerToken,
    headers: { 'Idempotency-Key': key('m4-purchase-rollback-approve') },
    body: {
      shop_id: 1,
      version: rollbackSubmitted.version,
      action: 'APPROVE',
    },
  },
).then((result) => result.data)
const beforeFailureBalance = number(sql(`
SELECT quantity_on_hand
FROM stock_balance
WHERE tenant_id = 1 AND location_id = ${locationId} AND product_id = ${productId};
`)[0])
sql(`
DROP TRIGGER IF EXISTS m4_fail_batch_movement;
CREATE TRIGGER m4_fail_batch_movement
BEFORE INSERT ON stock_batch_movement
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'M4_PURCHASE_FAILURE_INJECTION';
`)
try {
  await request(`/api/v3/purchase-orders/${rollbackCreate.id}/receipts`, {
    method: 'POST',
    token: adminToken,
    headers: { 'Idempotency-Key': key('m4-purchase-rollback-receipt') },
    body: {
      shop_id: 1,
      version: rollbackApproved.version,
      lines: [{
        purchase_order_item_id: rollbackApproved.items[0].id,
        location_id: locationId,
        received_quantity: 1,
        unit_cost: 12.5,
        vendor_batch_no: `V-${suffix}-ROLLBACK`,
      }],
    },
    expected: [500],
  })
} finally {
  sql('DROP TRIGGER IF EXISTS m4_fail_batch_movement;')
}
const rollbackEvidence = sql(`
SELECT CONCAT(
  COUNT(pr.id), '|',
  COALESCE(MAX(poi.received_quantity), 0), '|',
  COALESCE(MAX(sb.quantity_on_hand), 0)
)
FROM purchase_order po
JOIN purchase_order_item poi ON poi.purchase_order_id = po.id
LEFT JOIN purchase_receipt pr ON pr.purchase_order_id = po.id
JOIN stock_balance sb
  ON sb.tenant_id = po.tenant_id
 AND sb.location_id = ${locationId}
 AND sb.product_id = poi.product_id
WHERE po.id = ${rollbackCreate.id};
`)[0].split('|')
expectEqual(Number(rollbackEvidence[0]), 0, 'Failure injection left a receipt')
expectEqual(number(rollbackEvidence[1]), 0, 'Failure injection changed received quantity')
expectEqual(
  number(rollbackEvidence[2]),
  beforeFailureBalance,
  'Failure injection changed stock balance',
)

const evidence = sql(`
SELECT CONCAT('M4_PURCHASE_ORDERS=', COUNT(*))
FROM purchase_order
WHERE id IN (${created.id}, ${rollbackCreate.id});
SELECT CONCAT('M4_PURCHASE_RECEIPTS=', COUNT(*))
FROM purchase_receipt
WHERE purchase_order_id = ${created.id};
SELECT CONCAT('M4_PURCHASE_BATCHES=', COUNT(*))
FROM stock_batch
WHERE source_type = 'PURCHASE_RECEIPT'
  AND source_id IN (${partial.id}, ${finalReceipt.id});
SELECT CONCAT('M4_PURCHASE_BATCH_MOVEMENTS=', COUNT(*))
FROM stock_batch_movement
WHERE reference_type = 'PURCHASE_RECEIPT'
  AND reference_id IN (${partial.id}, ${finalReceipt.id});
SELECT CONCAT('M4_PURCHASE_INVENTORY_MOVEMENTS=', COUNT(*))
FROM inventory_movement
WHERE movement_type = 'PURCHASE_IN'
  AND reference_no IN ('${partial.receiptNo}', '${finalReceipt.receiptNo}');
SELECT CONCAT('M4_PURCHASE_AUDIT=', COUNT(*))
FROM audit_log
WHERE entity_type IN ('PURCHASE_ORDER', 'PURCHASE_RECEIPT')
  AND action LIKE 'PURCHASE_%';
SELECT CONCAT('M4_PURCHASE_OUTBOX=', COUNT(*))
FROM outbox_event
WHERE aggregate_type = 'PURCHASE_ORDER'
  AND aggregate_id IN ('${created.id}', '${rollbackCreate.id}');
`)
for (const expected of [
  'M4_PURCHASE_ORDERS=2',
  'M4_PURCHASE_RECEIPTS=2',
  'M4_PURCHASE_BATCHES=2',
  'M4_PURCHASE_BATCH_MOVEMENTS=2',
  'M4_PURCHASE_INVENTORY_MOVEMENTS=2',
]) {
  if (!evidence.includes(expected)) {
    throw new Error(`Missing M4 purchase evidence: ${expected}`)
  }
}
for (const line of evidence) console.log(line)
console.log('M4_PURCHASE_CREATE_IDEMPOTENCY=PASS')
console.log('M4_PURCHASE_SEPARATION_OF_DUTIES=PASS')
console.log('M4_PURCHASE_PARTIAL_AND_FINAL_RECEIPT=PASS')
console.log('M4_PURCHASE_OVER_RECEIPT_ROLLBACK=PASS')
console.log('M4_PURCHASE_FEFO_BATCH_ORDER=PASS')
console.log('M4_PURCHASE_FAILURE_INJECTION_ROLLBACK=PASS')
