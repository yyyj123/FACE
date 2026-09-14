import crypto from 'node:crypto'
import fs from 'node:fs'
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
const stateFile = options['state-file']
const sandboxSecret = options['sandbox-secret'] ?? 'm4-synthetic-payment-secret'
const mysql = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
const suffix = Date.now().toString().slice(-10)

if (!dbPassword) throw new Error('M4 payment verifier requires database password.')

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
      `M4 payment database assertion failed: ${result.stderr?.trim() || 'unknown error'}`,
    )
  }
  return result.stdout.trim().split(/\r?\n/).filter(Boolean)
}

async function request(path, {
  method = 'GET',
  token,
  headers = {},
  body,
  rawBody,
  expected = [200],
} = {}) {
  const actualBody = rawBody ?? (body === undefined ? undefined : JSON.stringify(body))
  const response = await fetch(`${backend}${path}`, {
    method,
    headers: {
      ...(actualBody === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: actualBody,
  })
  let payload
  try {
    payload = await response.json()
  } catch {
    payload = undefined
  }
  if (!expected.includes(response.status)) {
    throw new Error(
      `M4 payment API ${method} ${path} returned ${response.status}: `
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
    throw new Error(`M4 payment login failed for ${username}.`)
  }
  return result.data.access_token
}

function key(prefix) {
  return `${prefix}-${suffix}`.slice(0, 80)
}

function sign(timestamp, rawBody) {
  return crypto
    .createHmac('sha256', sandboxSecret)
    .update(`${timestamp}.${rawBody}`)
    .digest('hex')
}

const token = await login('admin')
const [fixture] = sql(`
SELECT CONCAT(m.id, '|', si.id)
FROM member m
JOIN service_item si
  ON si.tenant_id = m.tenant_id
 AND si.shop_id = 1
 AND si.status = 'ACTIVE'
WHERE m.tenant_id = 1 AND m.status = 'ACTIVE'
ORDER BY m.id, si.id
LIMIT 1;
`)
if (!fixture) throw new Error('M4 payment fixture requires an active member and service.')
const [memberIdText, serviceIdText] = fixture.split('|')
const memberId = Number(memberIdText)
const serviceId = Number(serviceIdText)

const order = await request('/api/v3/orders', {
  method: 'POST',
  token,
  headers: { 'Idempotency-Key': key('m4-payment-order') },
  body: {
    shop_id: 1,
    member_id: memberId,
    items: [{
      itemType: 'SERVICE',
      referenceId: serviceId,
      quantity: 1,
      discountAmount: 0,
    }],
    notes: 'M4支付适配与对账合成验收',
  },
}).then((result) => result.data)

const cardKey = key('m4-card-unconfigured')
await request(`/api/v3/orders/${order.id}/payments`, {
  method: 'POST',
  token,
  headers: { 'Idempotency-Key': cardKey },
  body: {
    shop_id: 1,
    payment_method: 'CARD',
    amount: order.payableAmount,
    version: order.version,
  },
  expected: [503],
})
const [cardRows] = sql(`
SELECT COUNT(*) FROM payment_transaction
WHERE tenant_id = 1 AND idempotency_key = '${cardKey}';
`)
if (cardRows !== '0') throw new Error('Unconfigured CARD payment changed the payment ledger.')

const paymentKey = key('m4-sandbox-payment')
const paymentBody = {
  shop_id: 1,
  payment_method: 'SANDBOX',
  amount: order.payableAmount,
  version: order.version,
}
const pending = await request(`/api/v3/orders/${order.id}/payments`, {
  method: 'POST',
  token,
  headers: { 'Idempotency-Key': paymentKey },
  body: paymentBody,
}).then((result) => result.data)
const replay = await request(`/api/v3/orders/${order.id}/payments`, {
  method: 'POST',
  token,
  headers: { 'Idempotency-Key': paymentKey },
  body: paymentBody,
}).then((result) => result.data)
if (pending.id !== replay.id || pending.status !== 'PENDING') {
  throw new Error('External payment idempotent creation failed.')
}
await request(`/api/v3/orders/${order.id}/payments`, {
  method: 'POST',
  token,
  headers: { 'Idempotency-Key': paymentKey },
  body: { ...paymentBody, amount: Number(order.payableAmount) - 0.01 },
  expected: [409],
})

const eventId = `evt-${suffix}`
const timestamp = Math.floor(Date.now() / 1000)
const callbackBody = JSON.stringify({
  event_id: eventId,
  payment_no: pending.paymentNo,
  status: 'SUCCESS',
  amount: Number(order.payableAmount).toFixed(2),
  external_transaction_no: `SBX-TXN-${suffix}`,
  channel_status: 'SANDBOX_CONFIRMED',
})
const callbackPath = '/api/v3/payment-channels/SANDBOX/callbacks'
await request(callbackPath, {
  method: 'POST',
  headers: {
    'X-Payment-Timestamp': String(timestamp),
    'X-Payment-Event-Id': eventId,
    'X-Payment-Signature': '0'.repeat(64),
  },
  rawBody: callbackBody,
  expected: [401],
})
let [paymentState] = sql(`
SELECT CONCAT(status, '|', COALESCE(channel_event_id, 'NULL'))
FROM payment_transaction WHERE id = ${pending.id};
`)
if (paymentState !== 'PENDING|NULL') {
  throw new Error('Invalid callback signature changed the payment state.')
}

const callbackHeaders = {
  'X-Payment-Timestamp': String(timestamp),
  'X-Payment-Event-Id': eventId,
  'X-Payment-Signature': sign(timestamp, callbackBody),
}
await request(callbackPath, {
  method: 'POST',
  headers: callbackHeaders,
  rawBody: callbackBody,
})
await request(callbackPath, {
  method: 'POST',
  headers: callbackHeaders,
  rawBody: callbackBody,
})
const changedCallbackBody = callbackBody.replace('SANDBOX_CONFIRMED', 'ALTERED')
await request(callbackPath, {
  method: 'POST',
  headers: {
    ...callbackHeaders,
    'X-Payment-Signature': sign(timestamp, changedCallbackBody),
  },
  rawBody: changedCallbackBody,
  expected: [409],
})
paymentState = sql(`
SELECT CONCAT(
  pt.status, '|', so.status, '|', pt.channel_event_id, '|',
  (SELECT COUNT(*) FROM payment_callback_event pce WHERE pce.payment_id = pt.id)
)
FROM payment_transaction pt
JOIN sales_order so ON so.id = pt.order_id
WHERE pt.id = ${pending.id};
`)[0]
if (paymentState !== `SUCCESS|PAID|${eventId}|1`) {
  throw new Error(`Callback confirmation is inconsistent: ${paymentState}`)
}

const accountingDate = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}).format(new Date())
const [sandboxSummary] = sql(`
SELECT CONCAT(COUNT(*), '|', CAST(COALESCE(SUM(amount), 0) AS CHAR))
FROM payment_transaction
WHERE tenant_id = 1 AND shop_id = 1
  AND channel_code = 'SANDBOX'
  AND status = 'SUCCESS' AND DATE(paid_at) = '${accountingDate}';
`)
const [sandboxCountText, sandboxAmountText] = sandboxSummary.split('|')
const matchedKey = key('m4-reconciliation-matched')
const matchedBody = {
  shop_id: 1,
  channel_code: 'SANDBOX',
  accounting_date: accountingDate,
  channel_payment_count: Number(sandboxCountText),
  channel_payment_amount: Number(sandboxAmountText),
  channel_refund_count: 0,
  channel_refund_amount: 0,
}
const matched = await request('/api/v3/reconciliation-batches', {
  method: 'POST',
  token,
  headers: { 'Idempotency-Key': matchedKey },
  body: matchedBody,
}).then((result) => result.data)
const matchedReplay = await request('/api/v3/reconciliation-batches', {
  method: 'POST',
  token,
  headers: { 'Idempotency-Key': matchedKey },
  body: matchedBody,
}).then((result) => result.data)
if (matched.id !== matchedReplay.id || matched.status !== 'MATCHED') {
  throw new Error('Matched reconciliation idempotency failed.')
}

const different = await request('/api/v3/reconciliation-batches', {
  method: 'POST',
  token,
  headers: { 'Idempotency-Key': key('m4-reconciliation-different') },
  body: {
    shop_id: 1,
    channel_code: 'CARD',
    accounting_date: accountingDate,
    channel_payment_count: 1,
    channel_payment_amount: 1,
    channel_refund_count: 0,
    channel_refund_amount: 0,
  },
}).then((result) => result.data)
if (different.status !== 'DIFFERENT' || different.items?.length !== 1) {
  throw new Error('Reconciliation difference was not retained.')
}
await request(`/api/v3/reconciliation-batches/${different.id}/close`, {
  method: 'POST',
  token,
  body: { shop_id: 1, version: different.version },
  expected: [409],
})
const resolved = await request(`/api/v3/reconciliation-batches/${different.id}/resolve`, {
  method: 'POST',
  token,
  body: {
    shop_id: 1,
    version: different.version,
    resolution_note: '合成验收：通道侧多出一笔，已登记复核，不直接修改业务账。',
    evidence_reference: `SYNTHETIC-${suffix}`,
  },
}).then((result) => result.data)
const closed = await request(`/api/v3/reconciliation-batches/${different.id}/close`, {
  method: 'POST',
  token,
  body: { shop_id: 1, version: resolved.version },
}).then((result) => result.data)
if (closed.status !== 'CLOSED') throw new Error('Resolved reconciliation did not close.')

const evidence = sql(`
SELECT CONCAT('M4_PAYMENT_CALLBACK_EVENTS=', COUNT(*)) FROM payment_callback_event;
SELECT CONCAT('M4_RECONCILIATION_BATCHES=', COUNT(*)) FROM reconciliation_batch;
SELECT CONCAT('M4_RECONCILIATION_ITEMS=', COUNT(*)) FROM reconciliation_item;
SELECT CONCAT('M4_RECONCILIATION_RESOLUTIONS=', COUNT(*)) FROM reconciliation_resolution;
SELECT CONCAT('M4_SANDBOX_SUCCESS_PAYMENTS=', COUNT(*))
FROM payment_transaction WHERE channel_code = 'SANDBOX' AND status = 'SUCCESS';
`)
for (const line of evidence) console.log(line)
if (stateFile) {
  const existingState = fs.existsSync(stateFile)
    ? JSON.parse(fs.readFileSync(stateFile, 'utf8'))
    : {}
  fs.writeFileSync(
    stateFile,
    JSON.stringify({
      ...existingState,
      matchedBatchId: matched.id,
      differentBatchId: different.id,
      accountingDate,
      paymentId: pending.id,
    }),
    { encoding: 'utf8', mode: 0o600 },
  )
}
console.log('M4_PAYMENT_UNCONFIGURED_CHANNEL_GUARD=PASS')
console.log('M4_PAYMENT_CALLBACK_SIGNATURE_AND_REPLAY=PASS')
console.log('M4_PAYMENT_CALLBACK_TRANSACTION=PASS')
console.log('M4_RECONCILIATION_MATCH_AND_DIFFERENCE=PASS')
console.log('M4_RECONCILIATION_RESOLVE_AND_CLOSE=PASS')
