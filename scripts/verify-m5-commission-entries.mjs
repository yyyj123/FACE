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

if (!dbPassword) throw new Error('M5 entry verifier requires the isolated database password.')

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
      `M5 entry database assertion failed: ${result.stderr?.trim() || 'unknown error'}`,
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
      `M5 entry API ${method} ${path} returned ${response.status}: ${payload?.message ?? 'no message'}`,
    )
  }
  return { status: response.status, payload, data: payload?.data }
}

async function login(username, password) {
  const response = await request('/api/v3/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  if (!response.data?.access_token) {
    throw new Error('M5 entry verifier login did not return an access token.')
  }
  return response.data.access_token
}

async function waitFor(label, query, predicate, attempts = 40) {
  for (let attempt = 0; attempt < attempts; attempt++) {
    const rows = sql(query)
    if (predicate(rows)) return rows
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  const diagnostics = sql(`
SELECT CONCAT(
  oe.id, '|', oe.event_type, '|',
  COALESCE(cep.status, 'UNCLAIMED'), '|',
  COALESCE(cep.attempt_count, 0), '|',
  COALESCE(cep.result_code, ''), '|',
  COALESCE(cep.last_error_code, '')
)
FROM outbox_event oe
LEFT JOIN commission_event_projection cep
  ON cep.tenant_id = oe.tenant_id
 AND cep.outbox_event_id = oe.id
WHERE oe.event_type IN (
  'PaymentSucceeded',
  'ServiceRecordCompleted',
  'RefundCompleted'
)
ORDER BY oe.id DESC
LIMIT 12;
`)
  throw new Error(
    `Timed out waiting for ${label}; projection diagnostics: ${diagnostics.join(', ')}`,
  )
}

function key(prefix) {
  return `${prefix}-${suffix}`.slice(0, 80)
}

const adminToken = await login('admin', 'Face@123')
const technicianToken = await login('jishi01', 'Face@123')
const fixture = sql(`
SELECT CONCAT(so.id, '|', sr.staff_id, '|', so.payable_amount)
FROM sales_order so
JOIN service_record sr
  ON sr.id = so.service_record_id
 AND sr.tenant_id = so.tenant_id
JOIN account a
  ON a.staff_id = sr.staff_id
 AND a.tenant_id = sr.tenant_id
 AND a.username = 'jishi01'
WHERE so.tenant_id = 1
  AND so.shop_id = 1
  AND so.status = 'PAID'
  AND sr.status = 'COMPLETED'
  AND sr.actual_end_at >= '2026-07-01 08:00:00'
  AND sr.actual_end_at < '2026-09-01 08:00:00'
ORDER BY so.id DESC
LIMIT 1;
`)
if (fixture.length !== 1) {
  throw new Error('M5 entry verifier could not find a completed and paid technician service order.')
}
const [orderIdText, staffIdText, payableText] = fixture[0].split('|')
const orderId = Number(orderIdText)
const staffId = Number(staffIdText)
const payable = Number(payableText)

sql(`
INSERT INTO outbox_event (
  event_id, tenant_id, shop_id, aggregate_type,
  aggregate_id, event_type, payload
) VALUES (
  UUID(), 1, 1, 'PAYMENT_TRANSACTION', '${orderId}',
  'PaymentSucceeded',
  JSON_OBJECT('paymentId', (
    SELECT id FROM payment_transaction
    WHERE tenant_id = 1 AND order_id = ${orderId} AND status = 'SUCCESS'
    ORDER BY id DESC LIMIT 1
  ), 'orderId', ${orderId})
);
`)

const entryRows = await waitFor(
  'commission accrual projection',
  `
SELECT CONCAT(ce.id, '|', ce.amount, '|', ce.version)
FROM commission_entry ce
JOIN commission_source_snapshot css ON css.id = ce.source_snapshot_id
WHERE ce.tenant_id = 1
  AND ce.entry_type = 'ACCRUAL'
  AND css.source_type = 'SERVICE'
  AND css.source_id = (
    SELECT service_record_id FROM sales_order WHERE id = ${orderId}
  )
ORDER BY ce.id DESC LIMIT 1;
`,
  (rows) => rows.length === 1,
)
const [entryIdText, originalAmountText, entryVersionText] = entryRows[0].split('|')
const entryId = Number(entryIdText)
const originalAmount = Number(originalAmountText)
const entryVersion = Number(entryVersionText)
if (!(originalAmount > 0) || entryVersion !== 0) {
  throw new Error('M5 accrual entry has an invalid amount or initial version.')
}

const adminEntries = await request(
  `/api/v3/commission/entries?shop_id=1&staff_id=${staffId}`,
  { token: adminToken },
)
if (!adminEntries.data?.records?.some((entry) => Number(entry.id) === entryId)) {
  throw new Error('M5 admin entry list did not return the projected accrual.')
}
const technicianEntries = await request(
  '/api/v3/commission/entries?shop_id=1',
  { token: technicianToken },
)
if (
  !technicianEntries.data?.records?.some((entry) => Number(entry.id) === entryId)
  || technicianEntries.data.records.some((entry) => Number(entry.staffId) !== staffId)
) {
  throw new Error('M5 technician SELF entry scope is incorrect.')
}

const freezeKey = key('m5-entry-freeze')
const frozen = await request(`/api/v3/commission/entries/${entryId}/freeze`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': freezeKey },
  body: {
    shop_id: 1,
    version: entryVersion,
    reason: 'M5 合成冻结验收',
  },
})
if (frozen.data?.status !== 'FROZEN' || Number(frozen.data?.version) !== 1) {
  throw new Error('M5 commission entry freeze did not update state and version.')
}
const freezeReplay = await request(`/api/v3/commission/entries/${entryId}/freeze`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': freezeKey },
  body: {
    shop_id: 1,
    version: entryVersion,
    reason: 'M5 合成冻结验收',
  },
})
if (freezeReplay.data?.status !== 'FROZEN' || Number(freezeReplay.data?.version) !== 1) {
  throw new Error('M5 freeze idempotency replay changed the entry again.')
}

const unfrozen = await request(`/api/v3/commission/entries/${entryId}/unfreeze`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-entry-unfreeze') },
  body: {
    shop_id: 1,
    version: 1,
    reason: 'M5 合成解冻验收',
  },
})
if (unfrozen.data?.status !== 'PENDING' || Number(unfrozen.data?.version) !== 2) {
  throw new Error('M5 commission entry unfreeze did not restore pending state.')
}

await request(`/api/v3/commission/entries/${entryId}/freeze`, {
  method: 'POST',
  token: technicianToken,
  headers: { 'Idempotency-Key': key('m5-tech-freeze-denied') },
  body: {
    shop_id: 1,
    version: 2,
    reason: '技师不得冻结提成',
  },
  expected: [403],
})

const refundRows = sql(`
SET @order_id = ${orderId};
SET @admin_id = (SELECT id FROM account WHERE tenant_id = 1 AND username = 'admin');
SET @payment_id = (
  SELECT id FROM payment_transaction
  WHERE tenant_id = 1 AND order_id = @order_id AND status = 'SUCCESS'
  ORDER BY id DESC LIMIT 1
);
SET @refund_amount = ROUND((
  SELECT paid_amount FROM sales_order WHERE id = @order_id
) / 4, 2);
INSERT INTO refund_transaction (
  tenant_id, shop_id, order_id, payment_id, refund_no, amount, reason,
  idempotency_key, request_hash, execution_idempotency_key,
  execution_request_hash, execution_mode, channel_status,
  status, approved_by, executed_by, refunded_at, created_by
) VALUES (
  1, 1, @order_id, @payment_id, 'M5-RF-${suffix}', @refund_amount,
  'M5 合成退款冲正验收', 'm5-rf-${suffix}', REPEAT('a', 64),
  'm5-rf-exec-${suffix}', REPEAT('b', 64), 'LOCAL_LEDGER',
  'LOCAL_CONFIRMED', 'SUCCESS', @admin_id, @admin_id,
  CURRENT_TIMESTAMP(3), @admin_id
);
SET @refund_id = LAST_INSERT_ID();
UPDATE payment_transaction
SET refunded_amount = refunded_amount + @refund_amount
WHERE id = @payment_id;
UPDATE sales_order
SET refunded_amount = refunded_amount + @refund_amount,
    status = 'PARTIALLY_REFUNDED',
    version = version + 1
WHERE id = @order_id;
INSERT INTO outbox_event (
  event_id, tenant_id, shop_id, aggregate_type,
  aggregate_id, event_type, payload
) VALUES (
  UUID(), 1, 1, 'REFUND_TRANSACTION', @refund_id,
  'RefundCompleted', JSON_OBJECT('refundId', @refund_id)
);
SELECT CONCAT(@refund_id, '|', @refund_amount);
`)
const [refundIdText, refundAmountText] = refundRows.at(-1).split('|')
const refundId = Number(refundIdText)
const refundAmount = Number(refundAmountText)

const reversalRows = await waitFor(
  'commission refund reversal projection',
  `
SELECT CONCAT(id, '|', amount)
FROM commission_entry
WHERE tenant_id = 1
  AND original_entry_id = ${entryId}
  AND refund_id = ${refundId}
  AND entry_type = 'REVERSAL';
`,
  (rows) => rows.length === 1,
)
const [reversalIdText, reversalAmountText] = reversalRows[0].split('|')
const reversalId = Number(reversalIdText)
const reversalAmount = Number(reversalAmountText)
if (!(reversalAmount < 0)) {
  throw new Error('M5 refund reversal did not create a negative entry.')
}

sql(`
INSERT INTO outbox_event (
  event_id, tenant_id, shop_id, aggregate_type,
  aggregate_id, event_type, payload
) VALUES (
  UUID(), 1, 1, 'REFUND_TRANSACTION', '${refundId}',
  'RefundCompleted', JSON_OBJECT('refundId', ${refundId})
);
`)
await waitFor(
  'refund event replay checkpoint',
  `
SELECT CAST(COUNT(*) AS CHAR)
FROM commission_event_projection cep
JOIN outbox_event oe ON oe.id = cep.outbox_event_id
WHERE oe.event_type = 'RefundCompleted'
  AND JSON_UNQUOTE(JSON_EXTRACT(oe.payload, '$.refundId')) = '${refundId}'
  AND cep.status = 'COMPLETED';
`,
  (rows) => Number(rows[0]) >= 2,
)
const reversalCount = Number(sql(`
SELECT COUNT(*) FROM commission_entry
WHERE tenant_id = 1
  AND original_entry_id = ${entryId}
  AND refund_id = ${refundId};
`)[0])
if (reversalCount !== 1) {
  throw new Error('M5 refund event replay created a duplicate reversal.')
}

const originalDetail = await request(
  `/api/v3/commission/entries/${entryId}?shop_id=1`,
  { token: adminToken },
)
if (
  originalDetail.data?.status !== 'PENDING'
  || Number(originalDetail.data?.amount) !== originalAmount
  || originalDetail.data?.reproducible !== true
) {
  throw new Error('M5 historical accrual was mutated or is no longer reproducible.')
}
const reversalDetail = await request(
  `/api/v3/commission/entries/${reversalId}?shop_id=1`,
  { token: adminToken },
)
if (
  Number(reversalDetail.data?.originalEntryId) !== entryId
  || Number(reversalDetail.data?.refundId) !== refundId
  || Number(reversalDetail.data?.amount) !== reversalAmount
) {
  throw new Error('M5 reversal detail lost its original-entry or refund linkage.')
}

const evidence = sql(`
SELECT CONCAT('M5_ENTRY_ROWS=', COUNT(*))
FROM commission_entry
WHERE id IN (${entryId}, ${reversalId});
SELECT CONCAT('M5_ENTRY_HISTORY=', COUNT(*))
FROM commission_entry_history
WHERE entry_id IN (${entryId}, ${reversalId});
SELECT CONCAT('M5_ENTRY_PROJECTIONS=', COUNT(*))
FROM commission_event_projection
WHERE status = 'COMPLETED'
  AND result_code IN ('ACCRUED', 'REVERSED_1', 'REPLAY');
SELECT CONCAT('M5_ENTRY_FAILED_PROJECTIONS=', COUNT(*))
FROM commission_event_projection
WHERE status = 'FAILED';
SELECT CONCAT('M5_ENTRY_ORIGINAL_AMOUNT=', amount)
FROM commission_entry WHERE id = ${entryId};
SELECT CONCAT('M5_ENTRY_REVERSAL_AMOUNT=', amount)
FROM commission_entry WHERE id = ${reversalId};
`)

for (const expected of [
  'M5_ENTRY_ROWS=2',
  'M5_ENTRY_HISTORY=4',
  'M5_ENTRY_FAILED_PROJECTIONS=0',
  `M5_ENTRY_ORIGINAL_AMOUNT=${originalAmount.toFixed(2)}`,
  `M5_ENTRY_REVERSAL_AMOUNT=${reversalAmount.toFixed(2)}`,
]) {
  if (!evidence.includes(expected)) {
    throw new Error(`Missing M5 entry evidence: ${expected}`)
  }
}
const projectionEvidence = evidence.find((row) => row.startsWith('M5_ENTRY_PROJECTIONS='))
if (!projectionEvidence || Number(projectionEvidence.split('=')[1]) < 3) {
  throw new Error('M5 entry projection evidence is incomplete.')
}

for (const row of evidence) console.log(row)
console.log(`M5_ENTRY_ORDER_PAYABLE=${payable.toFixed(2)}`)
console.log(`M5_ENTRY_REFUND_AMOUNT=${refundAmount.toFixed(2)}`)
console.log('M5_ENTRY_EVENT_IDEMPOTENCY=PASS')
console.log('M5_ENTRY_FREEZE_UNFREEZE=PASS')
console.log('M5_ENTRY_REFUND_REVERSAL=PASS')
console.log('M5_ENTRY_HISTORICAL_REPRODUCTION=PASS')
console.log('M5_ENTRY_SELF_PERMISSION=PASS')
console.log('M5_COMMISSION_ENTRY_LIFECYCLE=PASS')
