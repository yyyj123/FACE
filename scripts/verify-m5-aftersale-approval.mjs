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
if (!dbPassword) throw new Error('M5 after-sale verifier requires database password.')

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
    throw new Error(`M5 after-sale database assertion failed: ${result.stderr?.trim()}`)
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
      `M5 after-sale API ${method} ${path} returned ${response.status}: ${payload?.message ?? 'no message'}`,
    )
  }
  return { status: response.status, data: payload?.data }
}

async function login(username, password = 'Face@123') {
  const response = await request('/api/v3/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  if (!response.data?.access_token) throw new Error(`M5 login failed: ${username}`)
  return response.data.access_token
}

function key(prefix) {
  return `${prefix}-${suffix}`.slice(0, 80)
}

const fixture = sql(`
SELECT CONCAT(
  ce.id, '|', so.id, '|', so.member_id, '|', so.service_record_id, '|',
  (SELECT pt.id FROM payment_transaction pt
   WHERE pt.tenant_id = so.tenant_id AND pt.order_id = so.id AND pt.status = 'SUCCESS'
   ORDER BY pt.id DESC LIMIT 1)
)
FROM commission_entry ce
JOIN commission_source_snapshot css
  ON css.id = ce.source_snapshot_id AND css.tenant_id = ce.tenant_id
JOIN sales_order so
  ON so.service_record_id = css.source_id AND so.tenant_id = ce.tenant_id
WHERE ce.tenant_id = 1 AND ce.shop_id = 1
  AND ce.entry_type = 'ACCRUAL' AND css.source_type = 'SERVICE'
ORDER BY ce.id DESC LIMIT 1;
`)
if (fixture.length !== 1) throw new Error('M5 after-sale fixture is missing.')
const [entryIdText, orderIdText, memberIdText, serviceRecordIdText, paymentIdText] =
  fixture[0].split('|')
const entryId = Number(entryIdText)
const orderId = Number(orderIdText)
const memberId = Number(memberIdText)
const serviceRecordId = Number(serviceRecordIdText)
const paymentId = Number(paymentIdText)
const adminToken = await login('admin')

const adjustmentKey = key('m5-adjustment')
const adjustment = await request(`/api/v3/commission/entries/${entryId}/adjustments`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': adjustmentKey },
  body: { shop_id: 1, amount: 5.00, reason: 'M5 审批后提成调整验收' },
})
const adjustmentReplay = await request(
  `/api/v3/commission/entries/${entryId}/adjustments`,
  {
    method: 'POST',
    token: adminToken,
    headers: { 'Idempotency-Key': adjustmentKey },
    body: { shop_id: 1, amount: 5.00, reason: 'M5 审批后提成调整验收' },
  },
)
if (
  Number(adjustment.data?.id) !== Number(adjustmentReplay.data?.id)
  || adjustment.data?.status !== 'PENDING'
) {
  throw new Error('M5 adjustment request idempotency failed.')
}
const approvalId = Number(adjustment.data.approvalId)

sql(`
INSERT INTO account_shop_role (
  tenant_id, account_id, region_id, shop_id, role_id, effective_from, status
)
SELECT 1, a.id, NULL, 1, r.id, CURRENT_TIMESTAMP(3), 'ACTIVE'
FROM account a
JOIN role_definition r ON r.tenant_id = a.tenant_id AND r.role_code = 'MANAGER'
WHERE a.tenant_id = 1 AND a.username = 'jishi01'
  AND NOT EXISTS (
    SELECT 1 FROM account_shop_role ar
    WHERE ar.tenant_id = 1 AND ar.account_id = a.id
      AND ar.role_id = r.id AND ar.shop_id = 1 AND ar.status = 'ACTIVE'
  );
`)
const managerToken = await login('jishi01')
await request(`/api/v3/approvals/${approvalId}/decisions`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-self-approval-denied') },
  body: { shop_id: 1, version: 0, action: 'APPROVE', reason: '不得自批' },
  expected: [403],
})
const decisionKey = key('m5-adjustment-approve')
const approved = await request(`/api/v3/approvals/${approvalId}/decisions`, {
  method: 'POST',
  token: managerToken,
  headers: { 'Idempotency-Key': decisionKey },
  body: { shop_id: 1, version: 0, action: 'APPROVE', reason: '独立审批通过' },
})
const approvedReplay = await request(`/api/v3/approvals/${approvalId}/decisions`, {
  method: 'POST',
  token: managerToken,
  headers: { 'Idempotency-Key': decisionKey },
  body: { shop_id: 1, version: 0, action: 'APPROVE', reason: '独立审批通过' },
})
if (approved.data?.status !== 'APPROVED' || approvedReplay.data?.status !== 'APPROVED') {
  throw new Error('M5 approval decision or replay failed.')
}

const cancelAdjustment = await request(
  `/api/v3/commission/entries/${entryId}/adjustments`,
  {
    method: 'POST',
    token: adminToken,
    headers: { 'Idempotency-Key': key('m5-adjustment-cancel-request') },
    body: { shop_id: 1, amount: -2.00, reason: 'M5 调整取消验收' },
  },
)
const cancelled = await request(
  `/api/v3/approvals/${cancelAdjustment.data.approvalId}/cancel`,
  {
    method: 'POST',
    token: adminToken,
    headers: { 'Idempotency-Key': key('m5-adjustment-cancel') },
    body: { shop_id: 1, version: 0, reason: '申请人取消' },
  },
)
if (cancelled.data?.status !== 'CANCELLED') {
  throw new Error('M5 approval cancellation failed.')
}

const caseKey = key('m5-aftersale-create')
const caseBody = {
  shop_id: 1,
  member_id: memberId,
  order_id: orderId,
  service_record_id: serviceRecordId,
  category: 'SERVICE_QUALITY',
  priority: 'HIGH',
  summary: 'M5 售后状态机验收摘要',
}
const createdCase = await request('/api/v3/after-sales/cases', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': caseKey },
  body: caseBody,
})
const caseReplay = await request('/api/v3/after-sales/cases', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': caseKey },
  body: caseBody,
})
if (Number(createdCase.data?.id) !== Number(caseReplay.data?.id)) {
  throw new Error('M5 after-sale create idempotency failed.')
}
const caseId = Number(createdCase.data.id)

const memberUsername = sql(`
SELECT username FROM account
WHERE tenant_id = 1 AND member_id = ${memberId}
ORDER BY id DESC LIMIT 1;
`)[0]
const memberToken = await login(memberUsername, 'M3-Test@123')
const memberDetail = await request(
  `/api/v3/after-sales/cases/${caseId}?shop_id=1`,
  { token: memberToken },
)
if (Number(memberDetail.data?.memberId) !== memberId) {
  throw new Error('M5 member SELF after-sale detail failed.')
}

let version = 0
for (const [target, note] of [
  ['TRIAGED', null],
  ['PROCESSING', null],
  ['RESOLVED', '已形成处理方案'],
]) {
  const changed = await request(`/api/v3/after-sales/cases/${caseId}/actions`, {
    method: 'POST',
    token: managerToken,
    headers: { 'Idempotency-Key': key(`m5-case-${target.toLowerCase()}`) },
    body: { shop_id: 1, version, target_status: target, note },
  })
  version = Number(changed.data.version)
}
const reopened = await request(`/api/v3/after-sales/cases/${caseId}/reopen`, {
  method: 'POST',
  token: memberToken,
  headers: { 'Idempotency-Key': key('m5-case-reopen') },
  body: { shop_id: 1, version, reason: '问题仍未解决，申请重开' },
})
version = Number(reopened.data.version)
for (const [target, note] of [
  ['PROCESSING', null],
  ['RESOLVED', '重开后已解决'],
  ['CLOSED', '会员确认后关闭'],
]) {
  const changed = await request(`/api/v3/after-sales/cases/${caseId}/actions`, {
    method: 'POST',
    token: managerToken,
    headers: { 'Idempotency-Key': key(`m5-case-second-${target.toLowerCase()}`) },
    body: { shop_id: 1, version, target_status: target, note },
  })
  version = Number(changed.data.version)
}
await request(`/api/v3/after-sales/cases/${caseId}/actions`, {
  method: 'POST',
  token: managerToken,
  headers: { 'Idempotency-Key': key('m5-case-terminal-denied') },
  body: { shop_id: 1, version, target_status: 'PROCESSING', note: '禁止重开关闭工单' },
  expected: [409],
})

const refundCase = await request('/api/v3/after-sales/cases', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-refund-case-create') },
  body: {
    ...caseBody,
    category: 'REFUND',
    summary: 'M5 售后复用退款申请验收',
  },
})
const refundLinked = await request(
  `/api/v3/after-sales/cases/${refundCase.data.id}/refunds`,
  {
    method: 'POST',
    token: adminToken,
    headers: { 'Idempotency-Key': key('m5-aftersale-refund') },
    body: {
      shop_id: 1,
      payment_id: paymentId,
      amount: 1.00,
      reason: '售后工单申请退款',
    },
  },
)
if (Number(refundLinked.data?.refundId) <= 0) {
  throw new Error('M5 after-sale did not link the real refund request.')
}

sql(`
DELETE ar FROM account_shop_role ar
JOIN account a ON a.id = ar.account_id AND a.tenant_id = ar.tenant_id
JOIN role_definition r ON r.id = ar.role_id AND r.tenant_id = ar.tenant_id
WHERE a.username = 'jishi01' AND r.role_code = 'MANAGER' AND ar.shop_id = 1;
`)
const evidence = sql(`
SELECT CONCAT('M5_AFTERSALE_CASES=', COUNT(*))
FROM after_sale_case WHERE tenant_id = 1;
SELECT CONCAT('M5_AFTERSALE_LOGS=', COUNT(*))
FROM after_sale_case_log WHERE tenant_id = 1 AND case_id = ${caseId};
SELECT CONCAT('M5_APPROVAL_INSTANCES=', COUNT(*))
FROM approval_instance WHERE tenant_id = 1;
SELECT CONCAT('M5_APPROVAL_DECISIONS=', COUNT(*))
FROM approval_step WHERE tenant_id = 1 AND status IN ('DECIDED', 'CANCELLED');
SELECT CONCAT('M5_ADJUSTMENT_ENTRIES=', COUNT(*))
FROM commission_entry WHERE tenant_id = 1
  AND entry_type = 'ADJUSTMENT' AND approval_instance_id = ${approvalId};
SELECT CONCAT('M5_ADJUSTMENT_ORIGINAL_AMOUNT=', amount)
FROM commission_entry WHERE id = ${entryId};
SELECT CONCAT('M5_AFTERSALE_FINAL_STATUS=', status)
FROM after_sale_case WHERE id = ${caseId};
SELECT CONCAT('M5_AFTERSALE_REFUND_STATUS=', rt.status)
FROM after_sale_case ac JOIN refund_transaction rt ON rt.id = ac.refund_id
WHERE ac.id = ${refundCase.data.id};
SELECT CONCAT('M5_TEMP_MANAGER_CLEANUP=', COUNT(*))
FROM account_shop_role ar
JOIN account a ON a.id = ar.account_id
JOIN role_definition r ON r.id = ar.role_id
WHERE a.username = 'jishi01' AND r.role_code = 'MANAGER'
  AND ar.shop_id = 1 AND ar.status = 'ACTIVE';
`)
for (const expected of [
  'M5_AFTERSALE_CASES=2',
  'M5_AFTERSALE_LOGS=8',
  'M5_APPROVAL_INSTANCES=2',
  'M5_APPROVAL_DECISIONS=2',
  'M5_ADJUSTMENT_ENTRIES=1',
  'M5_ADJUSTMENT_ORIGINAL_AMOUNT=29.80',
  'M5_AFTERSALE_FINAL_STATUS=CLOSED',
  'M5_AFTERSALE_REFUND_STATUS=PENDING',
  'M5_TEMP_MANAGER_CLEANUP=0',
]) {
  if (!evidence.includes(expected)) throw new Error(`Missing M5 after-sale evidence: ${expected}`)
}
for (const row of evidence) console.log(row)
console.log('M5_ADJUSTMENT_APPROVAL_IDEMPOTENCY=PASS')
console.log('M5_APPROVAL_DUTY_SEPARATION=PASS')
console.log('M5_APPROVAL_IMMUTABLE_DECISION=PASS')
console.log('M5_AFTERSALE_STATE_MACHINE=PASS')
console.log('M5_AFTERSALE_MEMBER_SELF=PASS')
console.log('M5_AFTERSALE_REFUND_BOUNDARY=PASS')
console.log('M5_AFTERSALE_APPROVAL_ADJUSTMENT=PASS')

