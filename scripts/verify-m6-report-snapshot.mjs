import { createHash } from 'node:crypto'
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
if (!dbPassword) throw new Error('M6 report verifier requires a temporary database password.')

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
    throw new Error(`M6 report database assertion failed: ${result.stderr?.trim()}`)
  }
  return result.stdout.trim().split(/\r?\n/).filter(Boolean)
}

async function jsonRequest(path, {
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
      `M6 report API ${method} ${path} returned ${response.status}: ${payload?.msg ?? payload?.message ?? 'no message'}`,
    )
  }
  return { status: response.status, data: payload?.data }
}

async function login(username) {
  const response = await jsonRequest('/api/v3/auth/login', {
    method: 'POST',
    body: { username, password: 'Face@123' },
  })
  if (!response.data?.access_token) throw new Error(`M6 report login failed: ${username}`)
  return response.data.access_token
}

function idempotencyKey(prefix) {
  return `${prefix}-${suffix}`.slice(0, 80)
}

sql(`
INSERT INTO account_shop_role (tenant_id, account_id, shop_id, role_id, status)
SELECT a.tenant_id, a.id, NULL, r.id, 'ACTIVE'
FROM account a
JOIN role_definition r
  ON r.tenant_id = a.tenant_id AND r.role_code = 'OWNER'
WHERE a.username = 'jishi01'
  AND NOT EXISTS (
    SELECT 1 FROM account_shop_role ar
    WHERE ar.account_id = a.id AND ar.role_id = r.id AND ar.status = 'ACTIVE'
  );
`)

const adminToken = await login('admin')
const secondToken = await login('jishi01')
const reportKey = idempotencyKey('m6-report-admin')
const payload = {
  shopId: 1,
  fromDate: '2026-07-01',
  toDate: '2026-07-28',
  itemType: 'ALL',
  format: 'CSV',
}

const created = await jsonRequest('/api/v3/analytics/reports', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': reportKey },
  body: payload,
})
if (
  !created.data?.id
  || created.data.status !== 'READY'
  || created.data.metricVersion !== 'M6-04-v1'
  || 'content' in created.data
) {
  throw new Error('M6 report create response is incomplete or leaked snapshot content.')
}
const reportId = Number(created.data.id)

const replay = await jsonRequest('/api/v3/analytics/reports', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': reportKey },
  body: payload,
})
if (Number(replay.data?.id) !== reportId) {
  throw new Error('M6 report idempotent replay created a different snapshot.')
}

await jsonRequest('/api/v3/analytics/reports', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': reportKey },
  body: { ...payload, itemType: 'PRODUCT' },
  expected: [409],
})

const adminList = await jsonRequest('/api/v3/analytics/reports?page=1&pageSize=30', {
  token: adminToken,
})
if (!adminList.data?.records?.some((report) => Number(report.id) === reportId)) {
  throw new Error('M6 report creator cannot see the created snapshot.')
}

const secondListBefore = await jsonRequest('/api/v3/analytics/reports?page=1&pageSize=30', {
  token: secondToken,
})
if (secondListBefore.data?.records?.some((report) => Number(report.id) === reportId)) {
  throw new Error('M6 report self scope leaked another account snapshot.')
}
await jsonRequest(`/api/v3/analytics/reports/${reportId}`, {
  token: secondToken,
  expected: [404],
})

const secondCreated = await jsonRequest('/api/v3/analytics/reports', {
  method: 'POST',
  token: secondToken,
  headers: { 'Idempotency-Key': idempotencyKey('m6-report-second') },
  body: payload,
})
const adminListAfter = await jsonRequest('/api/v3/analytics/reports?page=1&pageSize=30', {
  token: adminToken,
})
if (adminListAfter.data?.records?.some(
  (report) => Number(report.id) === Number(secondCreated.data?.id),
)) {
  throw new Error('M6 report list leaked the second account snapshot to the first account.')
}

const downloadResponse = await fetch(
  `${backend}/api/v3/analytics/reports/${reportId}/download`,
  { headers: { Authorization: `Bearer ${adminToken}` } },
)
if (downloadResponse.status !== 200) {
  const failureBody = await downloadResponse.text()
  throw new Error(
    `M6 report download returned ${downloadResponse.status}: ${failureBody.slice(0, 800)}`,
  )
}
const bytes = Buffer.from(await downloadResponse.arrayBuffer())
const hash = createHash('sha256').update(bytes).digest('hex')
if (bytes[0] !== 0xef || bytes[1] !== 0xbb || bytes[2] !== 0xbf) {
  throw new Error('M6 report download is missing the UTF-8 BOM.')
}
if (downloadResponse.headers.get('x-content-sha256') !== hash) {
  throw new Error('M6 report response hash does not match the downloaded bytes.')
}
const text = bytes.toString('utf8')
if (text.includes('13800138000') || text.includes('password_hash')) {
  throw new Error('M6 report download contains forbidden sensitive data.')
}

const evidence = sql(`
SELECT CONCAT('M6_REPORT_ROWS=', COUNT(*)) FROM report_snapshot;
SELECT CONCAT('M6_REPORT_DUPLICATE_KEYS=', COUNT(*))
FROM (
  SELECT tenant_id, requested_by_account_id, idempotency_key
  FROM report_snapshot
  GROUP BY tenant_id, requested_by_account_id, idempotency_key
  HAVING COUNT(*) > 1
) duplicated;
SELECT CONCAT('M6_REPORT_CONTENT_MATCH=', COUNT(*))
FROM report_snapshot
WHERE id = ${reportId}
  AND content_sha256 = '${hash}'
  AND content_bytes = OCTET_LENGTH(content)
  AND status = 'READY';
SELECT CONCAT('M6_REPORT_CREATE_AUDIT=', COUNT(*))
FROM audit_log
WHERE entity_type = 'REPORT_SNAPSHOT'
  AND entity_id = ${reportId}
  AND action = 'ANALYTICS_REPORT_CREATED';
SELECT CONCAT('M6_REPORT_DOWNLOAD_AUDIT=', COUNT(*))
FROM audit_log
WHERE entity_type = 'REPORT_SNAPSHOT'
  AND entity_id = ${reportId}
  AND action = 'ANALYTICS_REPORT_DOWNLOADED';
`)
for (const expected of [
  'M6_REPORT_DUPLICATE_KEYS=0',
  'M6_REPORT_CONTENT_MATCH=1',
  'M6_REPORT_CREATE_AUDIT=1',
  'M6_REPORT_DOWNLOAD_AUDIT=1',
]) {
  if (!evidence.includes(expected)) throw new Error(`Missing M6 report evidence: ${expected}`)
}

console.log(`M6_REPORT_ADMIN_ID=${reportId}`)
console.log(`M6_REPORT_SECOND_ID=${Number(secondCreated.data.id)}`)
console.log(`M6_REPORT_DOWNLOAD_BYTES=${bytes.length}`)
console.log(`M6_REPORT_DOWNLOAD_SHA256=${hash}`)
console.log('M6_REPORT_IDEMPOTENCY=PASS')
console.log('M6_REPORT_REQUEST_HASH_CONFLICT=PASS')
console.log('M6_REPORT_SELF_SCOPE=PASS')
console.log('M6_REPORT_DOWNLOAD_INTEGRITY=PASS')
console.log('M6_REPORT_SENSITIVE_DATA_GUARD=PASS')
for (const line of evidence) console.log(line)
console.log('M6_REPORT_SNAPSHOT=PASS')
