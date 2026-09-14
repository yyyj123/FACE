import { spawnSync } from 'node:child_process'

const options = Object.fromEntries(process.argv.slice(2).map((entry) => {
  const [key, ...parts] = entry.replace(/^--/, '').split('=')
  return [key, parts.join('=')]
}))
const backend = options.backend ?? 'http://127.0.0.1:8193/face-next'
const dbPort = Number(options['db-port'] ?? 3321)
const dbPassword = options['db-password']
const mysql = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
const suffix = Date.now().toString().slice(-10)
if (!dbPassword) throw new Error('M6 training/open verifier requires a temporary database password.')

function sql(statement) {
  const result = spawnSync(mysql, [
    '--protocol=TCP', '--host=127.0.0.1', `--port=${dbPort}`, '--user=root',
    '--batch', '--skip-column-names', '--default-character-set=utf8mb4', 'face_salon',
  ], { input: statement, encoding: 'utf8', env: { ...process.env, MYSQL_PWD: dbPassword } })
  if (result.status !== 0) throw new Error(`M6 training/open database assertion failed: ${result.stderr?.trim()}`)
  return result.stdout.trim().split(/\r?\n/).filter(Boolean)
}

async function request(path, { method = 'GET', token, headers = {}, body, expected = [200] } = {}) {
  const response = await fetch(`${backend}${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const payload = await response.json().catch(() => undefined)
  if (!expected.includes(response.status)) {
    throw new Error(`M6 training/open ${method} ${path} returned ${response.status}: ${payload?.message ?? 'no message'}`)
  }
  return { status: response.status, data: payload?.data }
}

async function login(username, password = 'Face@123') {
  const response = await request('/api/v3/auth/login', { method: 'POST', body: { username, password } })
  return response.data.access_token
}

function key(prefix) { return `${prefix}-${suffix}`.slice(0, 80) }
function openHeaders(clientCode, secret, nonce) {
  return {
    'X-Integration-Client': clientCode,
    'X-Integration-Timestamp': new Date().toISOString(),
    'X-Integration-Nonce': nonce,
    Authorization: `Bearer ${secret}`,
  }
}

const adminToken = await login('admin')
const technicianToken = await login('jishi01')
const [staffIdText] = sql("SELECT staff_id FROM account WHERE tenant_id=1 AND username='jishi01' LIMIT 1;")
const staffId = Number(staffIdText)
if (!staffId) throw new Error('M6 training technician binding is missing.')

const course = await request('/api/v3/training/courses', {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-training-course') },
  body: {
    shop_id: 1, course_code: `CARE_${suffix}`, revision: 1,
    title: `基础护理规范 ${suffix}`, safe_summary: '虚构培训课程，不包含客户或健康信息。',
    pass_score: 80, validity_days: 365,
  },
})
const courseId = Number(course.data.id)
const published = await request(`/api/v3/training/courses/${courseId}/publish`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-training-publish') },
  body: { shop_id: 1, version: 0 },
})
if (published.data.status !== 'ACTIVE' || Number(published.data.version) !== 1) {
  throw new Error('M6 training course publish failed.')
}

const assigned = await request('/api/v3/training/records', {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-training-assign') },
  body: { shop_id: 1, course_id: courseId, staff_id: staffId },
})
const recordId = Number(assigned.data.id)
const mine = await request('/api/v3/training/me', { token: technicianToken })
if (!mine.data.some((item) => Number(item.id) === recordId && item.status === 'ASSIGNED')) {
  throw new Error('M6 training SELF assignment visibility failed.')
}
await request(`/api/v3/training/me/${recordId}/start`, {
  method: 'POST', token: technicianToken,
  headers: { 'Idempotency-Key': key('m6-training-start') },
  body: { version: 0 },
})
const submitted = await request(`/api/v3/training/me/${recordId}/submit`, {
  method: 'POST', token: technicianToken,
  headers: { 'Idempotency-Key': key('m6-training-submit') },
  body: { version: 1, evidence_summary: '已完成虚构课程演练并阅读操作规范。' },
})
if (submitted.data.status !== 'SUBMITTED') throw new Error('M6 training submission failed.')

await request(`/api/v3/training/records/${recordId}/verify`, {
  method: 'POST', token: technicianToken,
  headers: { 'Idempotency-Key': key('m6-training-self-verify') },
  body: { shop_id: 1, version: 2, score: 92, safe_reason: '本人不得验证本人培训。' },
  expected: [409],
})
const verified = await request(`/api/v3/training/records/${recordId}/verify`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-training-admin-verify') },
  body: { shop_id: 1, version: 2, score: 92, safe_reason: '虚构验证账号确认培训达标。' },
})
if (verified.data.status !== 'PASSED' || !verified.data.certificateNo) {
  throw new Error('M6 independent training verification failed.')
}

const client = await request('/api/v3/integrations/clients', {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-integration-create') },
  body: {
    shop_id: 1, client_code: `CATALOG_${suffix}`, client_name: `合成目录客户端 ${suffix}`,
    safe_description: '仅用于隔离验收的虚构客户端。', scopes: ['catalog:read'],
    rate_limit_per_minute: 5,
  },
})
const clientId = Number(client.data.id)
const clientCode = client.data.clientCode
const secret = client.data.clientSecret
if (!secret || client.data.secretShownOnce !== true) throw new Error('M6 integration one-time secret issuance failed.')

const firstNonce = key('open-nonce-01')
const catalog = await request('/api/v3/open/v1/catalog/services', {
  headers: openHeaders(clientCode, secret, firstNonce),
})
if (!Array.isArray(catalog.data) || catalog.data.some((item) => 'phone' in item || 'memberId' in item || 'health' in item)) {
  throw new Error('M6 open catalog returned an invalid or sensitive contract.')
}
await request('/api/v3/open/v1/catalog/services', {
  headers: openHeaders(clientCode, secret, firstNonce), expected: [409],
})
for (let index = 2; index <= 5; index += 1) {
  await request('/api/v3/open/v1/catalog/services', {
    headers: openHeaders(clientCode, secret, key(`open-nonce-0${index}`)),
  })
}
await request('/api/v3/open/v1/catalog/services', {
  headers: openHeaders(clientCode, secret, key('open-nonce-06')), expected: [429],
})
sql(`UPDATE integration_request_log SET created_at = DATE_SUB(created_at, INTERVAL 2 MINUTE) WHERE integration_client_id = ${clientId};`)

const rotated = await request(`/api/v3/integrations/clients/${clientId}/rotate-secret`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-integration-rotate') },
  body: { shop_id: 1, version: 0, reason: '隔离验收轮换测试' },
})
const rotatedSecret = rotated.data.clientSecret
if (!rotatedSecret || rotatedSecret === secret) throw new Error('M6 integration secret rotation failed.')
await request('/api/v3/open/v1/catalog/services', {
  headers: openHeaders(clientCode, secret, key('open-old-secret')), expected: [401],
})
await request('/api/v3/open/v1/catalog/services', {
  headers: openHeaders(clientCode, rotatedSecret, key('open-new-secret')),
})
await request(`/api/v3/integrations/clients/${clientId}/revoke`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-integration-revoke') },
  body: { shop_id: 1, version: 1, reason: '隔离验收完成后撤销' },
})
await request('/api/v3/open/v1/catalog/services', {
  headers: openHeaders(clientCode, rotatedSecret, key('open-revoked-secret')), expected: [401],
})

const evidence = sql(`
SELECT CONCAT('TRAINING_RECORD_STATUS=', status, ';VERSION=', version) FROM training_record WHERE id=${recordId};
SELECT CONCAT('TRAINING_HISTORY_ROWS=', COUNT(*)) FROM training_record_history WHERE training_record_id=${recordId};
SELECT CONCAT('TRAINING_SELF_VERIFY_ROWS=', COUNT(*)) FROM training_record_history WHERE training_record_id=${recordId} AND actor_account_id=(SELECT id FROM account WHERE username='jishi01' LIMIT 1) AND to_status IN ('PASSED','FAILED');
SELECT CONCAT('INTEGRATION_CLIENT_STATUS=', status, ';SECRET_VERSION=', secret_version) FROM integration_client WHERE id=${clientId};
SELECT CONCAT('INTEGRATION_EVENT_ROWS=', COUNT(*)) FROM integration_client_event WHERE integration_client_id=${clientId};
SELECT CONCAT('INTEGRATION_REQUEST_ROWS=', COUNT(*)) FROM integration_request_log WHERE integration_client_id=${clientId};
SELECT CONCAT('PLAINTEXT_SECRET_ROWS=', COUNT(*)) FROM integration_client WHERE id=${clientId} AND (secret_hash='${secret}' OR secret_hash='${rotatedSecret}' OR secret_prefix='${secret}' OR secret_prefix='${rotatedSecret}');
`)
for (const expected of [
  'TRAINING_RECORD_STATUS=PASSED;VERSION=3',
  'TRAINING_HISTORY_ROWS=4',
  'TRAINING_SELF_VERIFY_ROWS=0',
  'INTEGRATION_CLIENT_STATUS=REVOKED;SECRET_VERSION=2',
  'INTEGRATION_EVENT_ROWS=3',
  'PLAINTEXT_SECRET_ROWS=0',
]) {
  if (!evidence.includes(expected)) throw new Error(`Missing M6 training/open evidence: ${expected}`)
}

console.log('M6_TRAINING_OPEN_RUNTIME=PASS')
for (const line of evidence) console.log(line)
console.log(`OPEN_CATALOG_ROWS=${catalog.data.length}`)
console.log('OPEN_SCOPE=catalog:read')
console.log('OPEN_SENSITIVE_FIELDS=0')
