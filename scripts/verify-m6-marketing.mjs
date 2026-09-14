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
if (!dbPassword) throw new Error('M6 marketing verifier requires a temporary database password.')

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
    throw new Error(`M6 marketing database assertion failed: ${result.stderr?.trim()}`)
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
      `M6 marketing API ${method} ${path} returned ${response.status}: ${payload?.message ?? payload?.msg ?? 'no message'}`,
    )
  }
  return { status: response.status, data: payload?.data }
}

async function login(username, password = 'Face@123') {
  const response = await request('/api/v3/auth/login', {
    method: 'POST', body: { username, password },
  })
  if (!response.data?.access_token) throw new Error(`M6 marketing login failed: ${username}`)
  return response.data.access_token
}

function key(prefix) {
  const asciiPrefix = String(prefix).replace(/[^A-Za-z0-9._:-]+/g, '-')
  return `${asciiPrefix}-${suffix}`.slice(0, 80)
}

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

const [memberUsername] = sql(`
SELECT a.username
FROM account a
JOIN (
  SELECT member_id FROM after_sale_case
  WHERE tenant_id = 1 AND member_id IS NOT NULL
  ORDER BY id DESC LIMIT 1
) recent_case ON recent_case.member_id = a.member_id
WHERE a.tenant_id = 1 AND a.role_code = 'MEMBER'
  AND a.status = 'ACTIVE' AND a.member_id IS NOT NULL
ORDER BY a.id DESC LIMIT 1;
`)
if (!memberUsername) throw new Error('M6 marketing member test account is missing.')

const adminToken = await login('admin')
const managerToken = await login('jishi01')
const memberToken = await login(memberUsername, 'M3-Test@123')

const initialConsent = await request('/api/v3/me/marketing-consents', { token: memberToken })
const inAppInitial = initialConsent.data?.records?.find((item) => item.channel === 'IN_APP')
if (inAppInitial?.status !== 'REVOKED' || Number(inAppInitial?.version) !== 0) {
  throw new Error('M6 marketing legacy member must default to no consent.')
}
const unavailable = initialConsent.data?.records?.filter((item) => item.channel !== 'IN_APP') ?? []
if (unavailable.some((item) => item.available !== false)) {
  throw new Error('M6 marketing external channels must remain unavailable.')
}

let campaignSequence = 0
async function createCampaign(channel, title) {
  campaignSequence += 1
  return request('/api/v3/marketing/campaigns', {
    method: 'POST', token: adminToken,
    headers: { 'Idempotency-Key': key(`m6-create-${channel}-${campaignSequence}`) },
    body: {
      shop_id: 1, title, safe_summary: '本店会员专属活动，详情请在站内查看。',
      channel, action_path: '/notifications',
    },
  })
}

const campaign = await createCampaign('IN_APP', `合规活动${suffix}`)
if (campaign.data?.status !== 'DRAFT' || Number(campaign.data?.version) !== 0) {
  throw new Error('M6 marketing campaign draft creation failed.')
}
const campaignId = Number(campaign.data.id)
const submitKey = key('m6-marketing-submit')
const submitted = await request(`/api/v3/marketing/campaigns/${campaignId}/submit`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': submitKey },
  body: { shop_id: 1, version: 0 },
})
if (submitted.data?.status !== 'PENDING_APPROVAL') throw new Error('M6 campaign submit failed.')

await request(`/api/v3/marketing/campaigns/${campaignId}/decisions`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-marketing-self-approve') },
  body: { shop_id: 1, version: 1, action: 'APPROVE' }, expected: [403],
})
const approved = await request(`/api/v3/marketing/campaigns/${campaignId}/decisions`, {
  method: 'POST', token: managerToken,
  headers: { 'Idempotency-Key': key('m6-marketing-approve') },
  body: { shop_id: 1, version: 1, action: 'APPROVE' },
})
if (approved.data?.status !== 'APPROVED' || Number(approved.data?.version) !== 2) {
  throw new Error('M6 campaign independent approval failed.')
}

await request(`/api/v3/marketing/campaigns/${campaignId}/execute`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-marketing-no-consent') },
  body: { shop_id: 1, version: 2 }, expected: [409],
})

const grantKey = key('m6-marketing-consent-grant')
const granted = await request('/api/v3/me/marketing-consents/IN_APP', {
  method: 'PUT', token: memberToken,
  headers: { 'Idempotency-Key': grantKey },
  body: { status: 'GRANTED', version: 0 },
})
if (granted.data?.status !== 'GRANTED' || Number(granted.data?.version) !== 1) {
  throw new Error('M6 marketing member consent grant failed.')
}
const executeKey = key('m6-marketing-execute')
const executed = await request(`/api/v3/marketing/campaigns/${campaignId}/execute`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': executeKey },
  body: { shop_id: 1, version: 2 },
})
if (executed.data?.status !== 'COMPLETED' || Number(executed.data?.deliveredCount) < 1) {
  throw new Error('M6 marketing compliant in-app execution failed.')
}
const replay = await request(`/api/v3/marketing/campaigns/${campaignId}/execute`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': executeKey },
  body: { shop_id: 1, version: 2 },
})
if (replay.data?.status !== 'COMPLETED') throw new Error('M6 marketing execution replay failed.')

const notifications = await request('/api/v3/notifications?status=ALL&page=1&page_size=100', {
  token: memberToken,
})
if (!notifications.data?.records?.some((item) =>
  item.category === 'MARKETING' && Number(item.businessId) === campaignId
)) {
  throw new Error('M6 marketing member did not receive the in-app notification.')
}

const revoked = await request('/api/v3/me/marketing-consents/IN_APP', {
  method: 'PUT', token: memberToken,
  headers: { 'Idempotency-Key': key('m6-marketing-consent-revoke') },
  body: { status: 'REVOKED', version: 1 },
})
if (revoked.data?.status !== 'REVOKED' || Number(revoked.data?.version) !== 2) {
  throw new Error('M6 marketing member consent revoke failed.')
}

const second = await createCampaign('IN_APP', `撤回验证${suffix}`)
const secondId = Number(second.data.id)
await request(`/api/v3/marketing/campaigns/${secondId}/submit`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-second-submit') }, body: { shop_id: 1, version: 0 },
})
await request(`/api/v3/marketing/campaigns/${secondId}/decisions`, {
  method: 'POST', token: managerToken,
  headers: { 'Idempotency-Key': key('m6-second-approve') },
  body: { shop_id: 1, version: 1, action: 'APPROVE' },
})
await request(`/api/v3/marketing/campaigns/${secondId}/execute`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-second-execute') },
  body: { shop_id: 1, version: 2 }, expected: [409],
})

const sms = await createCampaign('SMS', `短信不可用${suffix}`)
await request(`/api/v3/marketing/campaigns/${Number(sms.data.id)}/submit`, {
  method: 'POST', token: adminToken,
  headers: { 'Idempotency-Key': key('m6-sms-submit') },
  body: { shop_id: 1, version: 0 }, expected: [409],
})
await request('/api/v3/marketing/campaigns?shop_id=999999', {
  token: adminToken, expected: [403],
})

const evidence = sql(`
SELECT CONCAT('CAMPAIGN_STATUS=', status, ';VERSION=', version)
FROM marketing_campaign WHERE id = ${campaignId};
SELECT CONCAT('AUDIENCE_ROWS=', COUNT(*))
FROM marketing_campaign_audience WHERE campaign_id = ${campaignId};
SELECT CONCAT('DELIVERY_ROWS=', COUNT(*), ';DELIVERED=', SUM(status = 'DELIVERED'))
FROM marketing_delivery_attempt WHERE campaign_id = ${campaignId};
SELECT CONCAT('MARKETING_NOTIFICATIONS=', COUNT(*), ';EXTERNAL_NOT_REQUESTED=', SUM(external_status = 'NOT_REQUESTED'))
FROM notification_message WHERE business_type = 'MARKETING_CAMPAIGN' AND business_id = '${campaignId}';
SELECT CONCAT('CONSENT_HISTORY_ROWS=', COUNT(*))
FROM member_marketing_consent_history
WHERE member_id = (SELECT member_id FROM account WHERE username = '${memberUsername}' LIMIT 1)
  AND channel = 'IN_APP';
SELECT CONCAT('SELF_APPROVAL_ROWS=', COUNT(*))
FROM marketing_campaign
WHERE id = ${campaignId} AND approved_by IN (created_by, submitted_by);
`)
for (const expected of [
  'CAMPAIGN_STATUS=COMPLETED;VERSION=4',
  'DELIVERY_ROWS=1;DELIVERED=1',
  'MARKETING_NOTIFICATIONS=1;EXTERNAL_NOT_REQUESTED=1',
  'CONSENT_HISTORY_ROWS=2',
  'SELF_APPROVAL_ROWS=0',
]) {
  if (!evidence.includes(expected)) throw new Error(`Missing M6 marketing evidence: ${expected}`)
}
if (!evidence.some((line) => /^AUDIENCE_ROWS=[1-9]\d*$/.test(line))) {
  throw new Error('M6 marketing audience was not frozen.')
}

console.log('M6_MARKETING_RUNTIME=PASS')
console.log(`M6_MARKETING_MEMBER=${memberUsername}`)
console.log(`M6_MARKETING_CAMPAIGN=${campaignId}`)
evidence.forEach((line) => console.log(line))
