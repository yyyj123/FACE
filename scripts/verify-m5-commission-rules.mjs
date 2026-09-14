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

if (!dbPassword) throw new Error('M5 verifier requires the isolated database password.')

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
    throw new Error(`M5 database assertion failed: ${result.stderr?.trim() || 'unknown error'}`)
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
      `M5 API ${method} ${path} returned ${response.status}: ${payload?.message ?? 'no message'}`,
    )
  }
  return { status: response.status, payload, data: payload?.data }
}

async function login(username, password) {
  const response = await request('/api/v3/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  if (!response.data?.access_token) throw new Error('M5 V3 login did not return a token.')
  return response.data.access_token
}

function key(prefix) {
  return `${prefix}-${suffix}`.slice(0, 80)
}

const adminToken = await login('admin', 'Face@123')
const technicianToken = await login('jishi01', 'Face@123')
const ruleCode = `M5_SERVICE_${suffix}`
const createKey = key('m5-rule-create')
const effectiveFrom = '2026-07-01T00:00:00Z'
const effectiveTo = '2026-09-01T00:00:00Z'
const draft = {
  shop_id: 1,
  rule_code: ruleCode,
  rule_name: 'M5 合成服务提成规则',
  source_type: 'SERVICE',
  calculation_type: 'PERCENTAGE_PLUS_FIXED',
  rate_value: 0.1,
  fixed_amount: 2,
  floor_amount: 5,
  cap_amount: 100,
  priority: 10,
  effective_from: effectiveFrom,
  effective_to: effectiveTo,
  scopes: [{ scope_type: 'ROLE', scope_key: 'BEAUTICIAN' }],
}

const created = await request('/api/v3/commission/rules', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': createKey },
  body: draft,
})
if (created.data?.status !== 'DRAFT' || created.data?.version !== 0) {
  throw new Error('M5 rule creation did not return a versioned draft.')
}
const ruleId = Number(created.data.id)

const replay = await request('/api/v3/commission/rules', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': createKey },
  body: draft,
})
if (Number(replay.data?.id) !== ruleId) {
  throw new Error('M5 same-key replay did not return the original rule.')
}

await request('/api/v3/commission/rules', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': createKey },
  body: { ...draft, rule_name: '不同载荷' },
  expected: [409],
})

const updated = await request(`/api/v3/commission/rules/${ruleId}`, {
  method: 'PATCH',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-rule-update') },
  body: { ...draft, fixed_amount: 3, version: 0 },
})
if (updated.data?.version !== 1 || Number(updated.data?.fixedAmount) !== 3) {
  throw new Error('M5 draft update did not enforce optimistic versioning.')
}

const published = await request(`/api/v3/commission/rules/${ruleId}/publish`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-rule-publish') },
  body: { shop_id: 1, version: 1 },
})
if (published.data?.status !== 'PUBLISHED' || published.data?.version !== 2) {
  throw new Error('M5 rule publishing did not create an immutable published state.')
}

await request(`/api/v3/commission/rules/${ruleId}`, {
  method: 'PATCH',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-rule-update-published') },
  body: { ...draft, fixed_amount: 4, version: 2 },
  expected: [409],
})

const conflictingDraft = await request('/api/v3/commission/rules', {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-rule-overlap-create') },
  body: { ...draft, rule_name: 'M5 合成冲突版本' },
})
await request(`/api/v3/commission/rules/${conflictingDraft.data.id}/publish`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-rule-overlap-publish') },
  body: { shop_id: 1, version: 0 },
  expected: [409],
})

const simulation = await request('/api/v3/commission/simulations', {
  method: 'POST',
  token: adminToken,
  body: { shop_id: 1, rule_id: ruleId, base_amount: 168 },
})
if (Number(simulation.data?.commissionAmount) !== 19.8 || simulation.data?.simulationOnly !== true) {
  throw new Error('M5 deterministic commission simulation failed.')
}

const technicianPermissions = sql(`
SELECT p.permission_code
FROM account a
JOIN account_shop_role ar ON ar.account_id = a.id AND ar.tenant_id = a.tenant_id
JOIN role_permission rp ON rp.role_id = ar.role_id
JOIN permission_definition p ON p.id = rp.permission_id
WHERE a.username = 'jishi01'
ORDER BY p.permission_code;
`)
console.log(`M5_TECHNICIAN_PERMISSIONS=${technicianPermissions.join(',')}`)
if (technicianPermissions.includes('commission:rule:view')) {
  throw new Error('M5 technician unexpectedly received commission rule management visibility.')
}

await request('/api/v3/commission/rules?shop_id=1', {
  token: technicianToken,
  expected: [403],
})

const evidence = sql(`
SELECT CONCAT('M5_RULE_ROWS=', COUNT(*))
FROM commission_rule_version
WHERE tenant_id = 1 AND rule_code = '${ruleCode}';
SELECT CONCAT('M5_RULE_SCOPES=', COUNT(*))
FROM commission_rule_scope scope
JOIN commission_rule_version rule ON rule.id = scope.rule_version_id
WHERE rule.tenant_id = 1 AND rule.rule_code = '${ruleCode}';
SELECT CONCAT('M5_RULE_AUDIT=', COUNT(*))
FROM audit_log
WHERE tenant_id = 1
  AND entity_type = 'COMMISSION_RULE'
  AND entity_id = ${ruleId};
SELECT CONCAT('M5_RULE_OUTBOX=', COUNT(*))
FROM outbox_event
WHERE tenant_id = 1
  AND aggregate_type = 'COMMISSION_RULE'
  AND aggregate_id = '${ruleId}';
`)

for (const expected of [
  'M5_RULE_ROWS=2',
  'M5_RULE_SCOPES=4',
  'M5_RULE_AUDIT=3',
  'M5_RULE_OUTBOX=1',
]) {
  if (!evidence.includes(expected)) {
    throw new Error(`Missing M5 commission evidence: ${expected}; actual=${evidence.join(',')}`)
  }
}

console.log(evidence.join('\n'))
console.log('M5_RULE_CREATE_IDEMPOTENCY=PASS')
console.log('M5_RULE_PUBLISHED_IMMUTABILITY=PASS')
console.log('M5_RULE_SCOPE_CONFLICT=PASS')
console.log('M5_RULE_DECIMAL_SIMULATION=PASS')
console.log('M5_RULE_PERMISSION_SCOPE=PASS')
console.log('M5_COMMISSION_RULE_VERSIONING=PASS')
