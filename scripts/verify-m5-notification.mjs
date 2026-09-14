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
if (!dbPassword) throw new Error('M5 notification verifier requires database password.')

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
    throw new Error(`M5 notification database assertion failed: ${result.stderr?.trim()}`)
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
      `M5 notification API ${method} ${path} returned ${response.status}: ${payload?.message ?? 'no message'}`,
    )
  }
  return { status: response.status, data: payload?.data }
}

async function login(username, password = 'Face@123') {
  const response = await request('/api/v3/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  if (!response.data?.access_token) throw new Error(`M5 notification login failed: ${username}`)
  return response.data.access_token
}

function key(prefix) {
  return `${prefix}-${suffix}`.slice(0, 80)
}

async function waitFor(predicate, label, attempts = 15) {
  for (let index = 0; index < attempts; index += 1) {
    const value = await predicate()
    if (value) return value
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
  throw new Error(`Timed out waiting for ${label}.`)
}

const memberFixture = sql(`
SELECT CONCAT(a.username, '|', a.member_id)
FROM account a
JOIN (
  SELECT member_id
  FROM after_sale_case
  WHERE tenant_id = 1 AND member_id IS NOT NULL
  ORDER BY id DESC
  LIMIT 1
) recent_case ON recent_case.member_id = a.member_id
WHERE a.tenant_id = 1 AND a.role_code = 'MEMBER'
  AND a.status = 'ACTIVE' AND a.member_id IS NOT NULL
ORDER BY a.id DESC LIMIT 1;
`)
if (memberFixture.length !== 1) throw new Error('M5 notification member fixture is missing.')
const [memberUsername, memberIdText] = memberFixture[0].split('|')
const memberId = Number(memberIdText)

const adminToken = await login('admin')
const technicianToken = await login('jishi01')
const memberToken = await login(memberUsername, 'M3-Test@123')

const adminInbox = await waitFor(async () => {
  const result = await request('/api/v3/notifications?status=ALL&page=1&page_size=100', {
    token: adminToken,
  })
  return result.data?.records?.length ? result.data : null
}, 'admin notifications')
const technicianInbox = await waitFor(async () => {
  const result = await request('/api/v3/notifications?status=ALL&page=1&page_size=100', {
    token: technicianToken,
  })
  return result.data?.records?.length ? result.data : null
}, 'technician notifications')
const memberInbox = await waitFor(async () => {
  const result = await request('/api/v3/notifications?status=ALL&page=1&page_size=100', {
    token: memberToken,
  })
  return result.data?.records?.length ? result.data : null
}, 'member notifications')

for (const inbox of [adminInbox, technicianInbox, memberInbox]) {
  if (inbox.records.some((item) => item.externalStatus !== 'UNAVAILABLE')) {
    throw new Error('M5 notification falsely claimed an external delivery.')
  }
}

const technicianUnread = technicianInbox.records.find((item) => item.status === 'UNREAD')
  ?? technicianInbox.records[0]
await request(`/api/v3/notifications/${technicianUnread.id}/read`, {
  method: 'POST',
  token: adminToken,
  headers: { 'Idempotency-Key': key('m5-notification-cross-account') },
  body: { version: technicianUnread.version },
  expected: [404],
})

const memberUnread = memberInbox.records.find((item) => item.status === 'UNREAD')
if (!memberUnread) throw new Error('M5 notification fixture has no unread member message.')
await request(`/api/v3/notifications/${memberUnread.id}/read`, {
  method: 'POST',
  token: memberToken,
  headers: { 'Idempotency-Key': key('m5-notification-version-conflict') },
  body: { version: memberUnread.version + 1 },
  expected: [409],
})
const readKey = key('m5-notification-read')
await request(`/api/v3/notifications/${memberUnread.id}/read`, {
  method: 'POST',
  token: memberToken,
  headers: { 'Idempotency-Key': readKey },
  body: { version: memberUnread.version },
})
await request(`/api/v3/notifications/${memberUnread.id}/read`, {
  method: 'POST',
  token: memberToken,
  headers: { 'Idempotency-Key': readKey },
  body: { version: memberUnread.version },
})

await request('/api/v3/notifications/read-all', {
  method: 'POST',
  token: memberToken,
  headers: { 'Idempotency-Key': key('m5-notification-read-all') },
  body: {},
})
const newCase = await request('/api/v3/after-sales/cases', {
  method: 'POST',
  token: memberToken,
  headers: { 'Idempotency-Key': key('m5-notification-new-case') },
  body: {
    shop_id: 1,
    member_id: memberId,
    category: 'OTHER',
    priority: 'NORMAL',
    summary: 'M5 通知水位线验收摘要',
  },
})
const afterWatermark = await waitFor(async () => {
  const result = await request('/api/v3/notifications?status=UNREAD&page=1&page_size=100', {
    token: memberToken,
  })
  return result.data?.records?.some(
    (item) => item.businessType === 'AFTER_SALE_CASE'
      && Number(item.businessId) === Number(newCase.data?.id),
  ) ? result.data : null
}, 'post-watermark notification')

const beforeCount = Number(sql('SELECT COUNT(*) FROM notification_message;')[0])
await new Promise((resolve) => setTimeout(resolve, 4000))
const afterCount = Number(sql('SELECT COUNT(*) FROM notification_message;')[0])
if (beforeCount !== afterCount) {
  throw new Error('M5 notification replay created duplicate messages.')
}

const evidence = sql(`
SELECT CONCAT('M5_NOTIFICATION_MESSAGES=', COUNT(*)) FROM notification_message;
SELECT CONCAT('M5_NOTIFICATION_PROJECTED=', COUNT(*))
FROM notification_projection_checkpoint WHERE status = 'PROJECTED';
SELECT CONCAT('M5_NOTIFICATION_FAILED=', COUNT(*))
FROM notification_projection_checkpoint WHERE status = 'FAILED';
SELECT CONCAT('M5_NOTIFICATION_EXTERNAL_UNAVAILABLE=', COUNT(*))
FROM notification_message WHERE external_status = 'UNAVAILABLE';
SELECT CONCAT('M5_NOTIFICATION_DUPLICATES=', COUNT(*))
FROM (
  SELECT tenant_id, event_id, recipient_account_id, channel
  FROM notification_message
  GROUP BY tenant_id, event_id, recipient_account_id, channel
  HAVING COUNT(*) > 1
) duplicated;
`)
for (const expected of ['M5_NOTIFICATION_FAILED=0', 'M5_NOTIFICATION_DUPLICATES=0']) {
  if (!evidence.includes(expected)) throw new Error(`Missing notification evidence: ${expected}`)
}

console.log(`M5_NOTIFICATION_ADMIN_VISIBLE=${adminInbox.records.length}`)
console.log(`M5_NOTIFICATION_TECHNICIAN_VISIBLE=${technicianInbox.records.length}`)
console.log(`M5_NOTIFICATION_MEMBER_VISIBLE=${memberInbox.records.length}`)
console.log(`M5_NOTIFICATION_POST_WATERMARK_UNREAD=${afterWatermark.records.length}`)
console.log('M5_NOTIFICATION_SELF_SCOPE=PASS')
console.log('M5_NOTIFICATION_OPTIMISTIC_READ=PASS')
console.log('M5_NOTIFICATION_READ_ALL_WATERMARK=PASS')
console.log('M5_NOTIFICATION_EXTERNAL_CHANNEL_TRUTH=PASS')
console.log('M5_NOTIFICATION_PROJECTION_IDEMPOTENCY=PASS')
for (const line of evidence) console.log(line)
console.log('M5_NOTIFICATION=PASS')
