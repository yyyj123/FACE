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
if (!dbPassword) throw new Error('M5 settlement verifier requires the isolated database password.')

function sql(statement) {
  const result = spawnSync(mysql, [
    '--protocol=TCP', '--host=127.0.0.1', `--port=${dbPort}`, '--user=root',
    '--batch', '--skip-column-names', '--default-character-set=utf8mb4', 'face_salon',
  ], {
    input: statement,
    encoding: 'utf8',
    env: { ...process.env, MYSQL_PWD: dbPassword },
  })
  if (result.status !== 0) {
    throw new Error(`M5 settlement database assertion failed: ${result.stderr?.trim()}`)
  }
  return result.stdout.trim().split(/\r?\n/).filter(Boolean)
}

async function request(path, {
  method = 'GET', token, headers = {}, body, expected = [200],
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
  try { payload = await response.json() } catch { payload = undefined }
  if (!expected.includes(response.status)) {
    throw new Error(
      `M5 settlement API ${method} ${path} returned ${response.status}: ${payload?.message ?? 'no message'}`,
    )
  }
  return { status: response.status, data: payload?.data }
}

async function login(username) {
  const response = await request('/api/v3/auth/login', {
    method: 'POST',
    body: { username, password: 'Face@123' },
  })
  if (!response.data?.access_token) throw new Error(`M5 settlement login failed: ${username}`)
  return response.data.access_token
}

function key(prefix) {
  return `${prefix}-${suffix}`.slice(0, 80)
}

const adminToken = await login('admin')
const entries = sql(`
SELECT CONCAT(id, '|', version)
FROM commission_entry
WHERE tenant_id = 1 AND shop_id = 1 AND status = 'PENDING'
ORDER BY id;
`)
if (entries.length < 2) throw new Error('M5 settlement requires M5-03 accrual and reversal entries.')
for (const row of entries) {
  const [entryId, version] = row.split('|')
  await request(`/api/v3/commission/entries/${entryId}/freeze`, {
    method: 'POST',
    token: adminToken,
    headers: { 'Idempotency-Key': key(`m5-settle-freeze-${entryId}`) },
    body: { shop_id: 1, version: Number(version), reason: 'M5 结算验收冻结' },
  })
}

sql(`
INSERT INTO account_shop_role (
  tenant_id, account_id, region_id, shop_id, role_id,
  effective_from, status
)
SELECT 1, a.id, NULL, NULL, r.id, CURRENT_TIMESTAMP(3), 'ACTIVE'
FROM account a
JOIN role_definition r ON r.tenant_id = a.tenant_id AND r.role_code = 'FINANCE'
WHERE a.tenant_id = 1 AND a.username = 'jishi01'
  AND NOT EXISTS (
    SELECT 1 FROM account_shop_role ar
    WHERE ar.tenant_id = 1 AND ar.account_id = a.id
      AND ar.role_id = r.id AND ar.status = 'ACTIVE'
  );
`)
const financeToken = await login('jishi01')
const frozenPeriod = sql(`
SELECT CONCAT(DATE_FORMAT(MIN(created_at), '%Y-%m-%d'), '|', DATE_FORMAT(MAX(created_at), '%Y-%m-%d'))
FROM commission_entry
WHERE tenant_id = 1 AND shop_id = 1 AND status = 'FROZEN';
`)
if (frozenPeriod.length !== 1 || !frozenPeriod[0].includes('|')) {
  throw new Error('M5 settlement could not resolve the frozen-entry period from the database.')
}
const [periodStart, periodEnd] = frozenPeriod[0].split('|')
const createKey = key('m5-settlement-create')
const createBody = {
  shop_id: 1,
  period_start: periodStart,
  period_end: periodEnd,
  currency_code: 'CNY',
}
const created = await request('/api/v3/commission/settlements', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': createKey },
  body: createBody,
})
const replay = await request('/api/v3/commission/settlements', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': createKey },
  body: createBody,
})
if (Number(created.data?.id) !== Number(replay.data?.id) || created.data?.status !== 'DRAFT') {
  throw new Error('M5 settlement create idempotency replay is inconsistent.')
}
const batchId = Number(created.data.id)
const calculated = await request(`/api/v3/commission/settlements/${batchId}/calculate`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-settlement-calculate') },
  body: { shop_id: 1, version: 0 },
})
if (calculated.data?.status !== 'CALCULATED' || Number(calculated.data?.itemCount) < 2) {
  throw new Error('M5 settlement calculation did not capture frozen entries.')
}
await request(`/api/v3/commission/settlements/${batchId}/confirm`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-settlement-self-confirm') },
  body: { shop_id: 1, version: 1 },
  expected: [403],
})
const confirmed = await request(`/api/v3/commission/settlements/${batchId}/confirm`, {
  method: 'POST',
  token: financeToken,
  headers: { 'Idempotency-Key': key('m5-settlement-confirm') },
  body: { shop_id: 1, version: 1 },
})
if (confirmed.data?.status !== 'CONFIRMED' || Number(confirmed.data?.version) !== 2) {
  throw new Error('M5 settlement confirmation failed.')
}
const paid = await request(`/api/v3/commission/settlements/${batchId}/mark-paid`, {
  method: 'POST',
  token: financeToken,
  headers: { 'Idempotency-Key': key('m5-settlement-paid') },
  body: { shop_id: 1, version: 2, payment_reference: `M5-PAY-${suffix}` },
})
if (paid.data?.status !== 'PAID') throw new Error('M5 settlement paid fact was not recorded.')
const closed = await request(`/api/v3/commission/settlements/${batchId}/close`, {
  method: 'POST',
  token: financeToken,
  headers: { 'Idempotency-Key': key('m5-settlement-close') },
  body: { shop_id: 1, version: 3 },
})
if (closed.data?.status !== 'CLOSED' || Number(closed.data?.version) !== 4) {
  throw new Error('M5 settlement close failed.')
}
await request(`/api/v3/commission/settlements/${batchId}/void`, {
  method: 'POST',
  token: financeToken,
  headers: { 'Idempotency-Key': key('m5-settlement-closed-void') },
  body: { shop_id: 1, version: 4, reason: '关闭批次不可作废' },
  expected: [409],
})

const summary = await request('/api/v3/commission/technician/commission-summary?shop_id=1', {
  token: financeToken,
})
if (Number(summary.data?.staffId) <= 0 || Number(summary.data?.paidAmount) === 0) {
  throw new Error('M5 technician settlement summary is incomplete.')
}

const draft = await request('/api/v3/commission/settlements', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-settlement-void-create') },
  body: createBody,
})
const voided = await request(`/api/v3/commission/settlements/${draft.data.id}/void`, {
  method: 'POST',
  token: financeToken,
  headers: { 'Idempotency-Key': key('m5-settlement-void') },
  body: { shop_id: 1, version: 0, reason: 'M5 草稿整批作废验收' },
})
if (voided.data?.status !== 'VOIDED') throw new Error('M5 settlement draft void failed.')

sql(`
DELETE ar FROM account_shop_role ar
JOIN account a ON a.id = ar.account_id AND a.tenant_id = ar.tenant_id
JOIN role_definition r ON r.id = ar.role_id AND r.tenant_id = ar.tenant_id
WHERE a.username = 'jishi01' AND r.role_code = 'FINANCE';
`)
const evidence = sql(`
SELECT CONCAT('M5_SETTLEMENT_BATCHES=', COUNT(*))
FROM commission_settlement_batch WHERE tenant_id = 1;
SELECT CONCAT('M5_SETTLEMENT_ITEMS=', COUNT(*))
FROM commission_settlement_item WHERE tenant_id = 1 AND batch_id = ${batchId};
SELECT CONCAT('M5_SETTLEMENT_SETTLED_ENTRIES=', COUNT(*))
FROM commission_entry WHERE tenant_id = 1 AND status = 'SETTLED';
SELECT CONCAT('M5_SETTLEMENT_HISTORY=', COUNT(*))
FROM commission_entry_history WHERE tenant_id = 1 AND action = 'SETTLED';
SELECT CONCAT('M5_SETTLEMENT_FINAL_STATUS=', status)
FROM commission_settlement_batch WHERE id = ${batchId};
SELECT CONCAT('M5_SETTLEMENT_TEMP_FINANCE_CLEANUP=', COUNT(*))
FROM account_shop_role ar
JOIN account a ON a.id = ar.account_id
JOIN role_definition r ON r.id = ar.role_id
WHERE a.username = 'jishi01' AND r.role_code = 'FINANCE' AND ar.status = 'ACTIVE';
`)
for (const expected of [
  'M5_SETTLEMENT_BATCHES=2',
  `M5_SETTLEMENT_ITEMS=${Number(calculated.data.itemCount)}`,
  `M5_SETTLEMENT_SETTLED_ENTRIES=${Number(calculated.data.itemCount)}`,
  `M5_SETTLEMENT_HISTORY=${Number(calculated.data.itemCount)}`,
  'M5_SETTLEMENT_FINAL_STATUS=CLOSED',
  'M5_SETTLEMENT_TEMP_FINANCE_CLEANUP=0',
]) {
  if (!evidence.includes(expected)) throw new Error(`Missing M5 settlement evidence: ${expected}`)
}
for (const row of evidence) console.log(row)
console.log('M5_SETTLEMENT_CREATE_IDEMPOTENCY=PASS')
console.log('M5_SETTLEMENT_DUTY_SEPARATION=PASS')
console.log('M5_SETTLEMENT_ACTIVE_ENTRY_UNIQUENESS=PASS')
console.log('M5_SETTLEMENT_PAYMENT_AND_CLOSE=PASS')
console.log('M5_SETTLEMENT_TERMINAL_GUARD=PASS')
console.log('M5_SETTLEMENT_TECHNICIAN_SELF_SUMMARY=PASS')
console.log('M5_COMMISSION_SETTLEMENT=PASS')
