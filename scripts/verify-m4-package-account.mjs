import { spawnSync } from 'node:child_process'
import fs from 'node:fs'

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
const stateFile = options['state-file']

if (!dbPassword) throw new Error('M4 verifier requires the isolated database password.')

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
    throw new Error(`M4 database assertion failed: ${result.stderr?.trim() || 'unknown error'}`)
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
      `M4 API ${method} ${path} returned ${response.status}: ${payload?.msg ?? payload?.message ?? 'no message'}`,
    )
  }
  return { status: response.status, payload, data: payload?.data }
}

function key(prefix) {
  return `${prefix}-${suffix}`.slice(0, 80)
}

function futureAt(days, hour, minute = 0) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  date.setHours(hour, minute, 0, 0)
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 19)
}

async function loginV2(username, password) {
  const response = await request('/api/v2/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  if (!response.data?.token) throw new Error('M4 V2 login did not return a token.')
  return response.data.token
}

async function loginTechnicianV2() {
  const response = await request('/api/v2/client/auth/login', {
    method: 'POST',
    body: { username: 'jishi01', password: 'Face@123' },
  })
  if (!response.data?.token) throw new Error('M4 technician V2 login did not return a token.')
  return response.data
}

async function loginV3(username, password) {
  const response = await request('/api/v3/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  if (!response.data?.access_token) throw new Error('M4 V3 login did not return a token.')
  return response.data.access_token
}

async function createPaidPackageOrder(adminToken, memberId, packageProductId, prefix) {
  const orderBody = {
    shop_id: 1,
    member_id: memberId,
    items: [{
      itemType: 'PACKAGE',
      referenceId: packageProductId,
      quantity: 1,
      discountAmount: 0,
    }],
    notes: 'M4 package acceptance order',
  }
  const order = await request('/api/v3/orders', {
    method: 'POST',
    token: adminToken,
    headers: { 'Idempotency-Key': key(`${prefix}-order`) },
    body: orderBody,
  })
  return request(`/api/v3/orders/${order.data.id}/payments`, {
    method: 'POST',
    token: adminToken,
    headers: { 'Idempotency-Key': key(`${prefix}-payment`) },
    body: {
      shop_id: 1,
      payment_method: 'CASH',
      amount: order.data.payableAmount,
      version: order.data.version,
    },
  }).then(() => order.data)
}

async function createServiceRecord(adminV2Token, technicianV2Token, memberId, serviceId) {
  const appointment = await request('/api/v2/appointments', {
    method: 'POST',
    token: adminV2Token,
    body: {
      shopId: 1,
      memberId,
      staffId: 2,
      serviceIds: [serviceId],
      resourceIds: [],
      startAt: futureAt(10, 15, 0),
      source: 'FRONT_DESK',
      internalNote: 'M4 package write-off acceptance',
    },
  }).then((result) => result.data)
  let current = appointment
  for (const status of ['CONFIRMED', 'CHECKED_IN']) {
    current = await request(`/api/v2/appointments/${current.id}/status`, {
      method: 'POST',
      token: adminV2Token,
      body: {
        shopId: 1,
        status,
        version: current.version,
        reason: `M4 ${status}`,
      },
    }).then((result) => result.data)
  }
  return request('/api/v2/client/service-records/start', {
    method: 'POST',
    token: technicianV2Token,
    body: {
      shopId: 1,
      appointmentId: current.id,
      appointmentVersion: current.version,
    },
  }).then((result) => result.data)
}

const adminV3Token = await loginV3('admin', 'Face@123')
const technicianV3Token = await loginV3('jishi01', 'Face@123')
const adminV2Token = await loginV2('admin', 'Face@123')
const technicianV2Session = await loginTechnicianV2()
const technicianV2Token = technicianV2Session.token

const memberUsername = `m4_member_${suffix}`
const memberPassword = 'M4-Test@123'
const memberRegistration = await request('/api/v2/client/auth/register', {
  method: 'POST',
  body: {
    username: memberUsername,
    password: memberPassword,
    name: 'M4测试会员',
    phone: `177${suffix.slice(-8)}`,
    shopId: 1,
  },
})
const memberId = Number(memberRegistration.data?.memberId)
if (!Number.isSafeInteger(memberId)) throw new Error('M4 member registration failed.')
const memberV2Session = await request('/api/v2/client/auth/login', {
  method: 'POST',
  body: { username: memberUsername, password: memberPassword },
}).then((result) => result.data)
const memberV3Token = await loginV3(memberUsername, memberPassword)

const fixture = sql(`
SELECT si.id
FROM service_item si
WHERE si.tenant_id = 1
  AND si.shop_id = 1
  AND si.status = 'ACTIVE'
ORDER BY si.id
LIMIT 1;
`)
if (fixture.length !== 1) throw new Error('M4 fixture requires an active service.')
const serviceId = Number(fixture[0])

const packageBody = {
  shop_id: 1,
  package_code: `M4-${suffix}`,
  name: 'M4 Acceptance Package',
  description: 'Synthetic M4 package-account verification',
  sale_price: 280,
  validity_days: 365,
  status: 'ACTIVE',
  items: [{ service_id: serviceId, quantity: 3 }],
}
const packageProduct = await request('/api/v3/package-products', {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': key('m4-package-product') },
  body: packageBody,
}).then((result) => result.data)

const order = await createPaidPackageOrder(
  adminV3Token,
  memberId,
  packageProduct.id,
  'm4-success',
)
const issueKey = key('m4-package-issue')
const issueBody = {
  shop_id: 1,
  package_product_id: packageProduct.id,
  source_order_id: order.id,
}
const issued = await request(`/api/v3/members/${memberId}/packages`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': issueKey },
  body: issueBody,
})
const issueReplay = await request(`/api/v3/members/${memberId}/packages`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': issueKey },
  body: issueBody,
})
if (issued.data?.id !== issueReplay.data?.id) {
  throw new Error('M4 package issue replay returned a different package instance.')
}
await request(`/api/v3/members/${memberId}/packages`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': issueKey },
  body: { ...issueBody, source_order_id: order.id + 99999 },
  expected: [409],
})

const serviceRecord = await createServiceRecord(
  adminV2Token,
  technicianV2Token,
  memberId,
  serviceId,
)
const writeOffKey = key('m4-write-off')
const writeOffBody = {
  shop_id: 1,
  service_record_id: serviceRecord.id,
  service_id: serviceId,
  quantity: 1,
  version: issued.data.version,
  reason: 'M4 acceptance write-off',
}
const writtenOff = await request(
  `/api/v3/package-instances/${issued.data.id}/write-offs`,
  {
    method: 'POST',
    token: technicianV3Token,
    headers: { 'Idempotency-Key': writeOffKey },
    body: writeOffBody,
  },
)
const writeOffReplay = await request(
  `/api/v3/package-instances/${issued.data.id}/write-offs`,
  {
    method: 'POST',
    token: technicianV3Token,
    headers: { 'Idempotency-Key': writeOffKey },
    body: writeOffBody,
  },
)
if (writtenOff.data?.id !== writeOffReplay.data?.id) {
  throw new Error('M4 package write-off replay returned a different ledger.')
}
await request(`/api/v3/package-instances/${issued.data.id}/write-offs`, {
  method: 'POST',
  token: technicianV3Token,
  headers: { 'Idempotency-Key': writeOffKey },
  body: { ...writeOffBody, quantity: 2 },
  expected: [409],
})

const packagesAfterWriteOff = await request(
  `/api/v3/members/${memberId}/packages?shop_id=1`,
  { token: adminV3Token },
)
const currentPackage = packagesAfterWriteOff.data.find(
  (item) => Number(item.id) === Number(issued.data.id),
)
if (!currentPackage || Number(currentPackage.remainingQuantity) !== 2) {
  throw new Error('M4 package write-off did not decrement the package balance.')
}

const reversalKey = key('m4-write-off-reversal')
const reversalBody = {
  shop_id: 1,
  version: currentPackage.version,
  reason: 'M4 acceptance reversal',
}
const reversed = await request(`/api/v3/package-ledger/${writtenOff.data.id}/reversals`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': reversalKey },
  body: reversalBody,
})
const reversalReplay = await request(
  `/api/v3/package-ledger/${writtenOff.data.id}/reversals`,
  {
    method: 'POST',
    token: adminV3Token,
    headers: { 'Idempotency-Key': reversalKey },
    body: reversalBody,
  },
)
if (reversed.data?.id !== reversalReplay.data?.id) {
  throw new Error('M4 package reversal replay returned a different ledger.')
}

const accounts = await request(`/api/v3/members/${memberId}/accounts?shop_id=1`, {
  token: adminV3Token,
})
let account = accounts.data.find((item) => item.accountType === 'GIFT_BALANCE')
if (!account) throw new Error('M4 member gift-balance account is unavailable.')

const creditKey = key('m4-account-credit')
const creditBody = {
  shop_id: 1,
  amount: 50,
  entry_type: 'MANUAL_CREDIT',
  remark: 'M4 synthetic credit',
  version: account.version,
}
const credit = await request(`/api/v3/member-accounts/${account.id}/credits`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': creditKey },
  body: creditBody,
})
const creditReplay = await request(`/api/v3/member-accounts/${account.id}/credits`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': creditKey },
  body: creditBody,
})
if (credit.data?.id !== creditReplay.data?.id) {
  throw new Error('M4 member-account credit replay returned a different ledger.')
}

account = await request(`/api/v3/members/${memberId}/accounts?shop_id=1`, {
  token: adminV3Token,
}).then((result) => result.data.find((item) => item.id === account.id))
const debitKey = key('m4-account-debit')
const debitBody = {
  shop_id: 1,
  amount: 10,
  entry_type: 'MANUAL_DEBIT',
  remark: 'M4 synthetic debit',
  version: account.version,
}
const debit = await request(`/api/v3/member-accounts/${account.id}/debits`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': debitKey },
  body: debitBody,
})

account = await request(`/api/v3/members/${memberId}/accounts?shop_id=1`, {
  token: adminV3Token,
}).then((result) => result.data.find((item) => item.id === account.id))
await request(`/api/v3/member-accounts/${account.id}/freeze`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': key('m4-account-freeze') },
  body: { shop_id: 1, version: account.version },
})
account = await request(`/api/v3/members/${memberId}/accounts?shop_id=1`, {
  token: adminV3Token,
}).then((result) => result.data.find((item) => item.id === account.id))
await request(`/api/v3/member-accounts/${account.id}/debits`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': key('m4-frozen-debit') },
  body: { ...debitBody, amount: 1, version: account.version },
  expected: [409],
})
await request(`/api/v3/member-accounts/${account.id}/unfreeze`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': key('m4-account-unfreeze') },
  body: { shop_id: 1, version: account.version },
})
account = await request(`/api/v3/members/${memberId}/accounts?shop_id=1`, {
  token: adminV3Token,
}).then((result) => result.data.find((item) => item.id === account.id))
await request(`/api/v3/member-account-ledger/${debit.data.id}/reversals`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': key('m4-account-reversal') },
  body: {
    shop_id: 1,
    version: account.version,
    reason: 'M4 account debit reversal',
  },
})

const rollbackOrder = await createPaidPackageOrder(
  adminV3Token,
  memberId,
  packageProduct.id,
  'm4-rollback',
)
sql(`
DROP TRIGGER IF EXISTS m4_fail_package_ledger;
CREATE TRIGGER m4_fail_package_ledger
BEFORE INSERT ON package_ledger
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'M4_FAILURE_INJECTION';
`)
try {
  await request(`/api/v3/members/${memberId}/packages`, {
    method: 'POST',
    token: adminV3Token,
    headers: { 'Idempotency-Key': key('m4-rollback-issue') },
    body: {
      shop_id: 1,
      package_product_id: packageProduct.id,
      source_order_id: rollbackOrder.id,
    },
    expected: [500],
  })
} finally {
  sql('DROP TRIGGER IF EXISTS m4_fail_package_ledger;')
}

const rollbackRows = sql(`
SELECT COUNT(*)
FROM package_instance
WHERE tenant_id = 1 AND source_order_id = ${Number(rollbackOrder.id)};
`)
if (rollbackRows[0] !== '0') {
  throw new Error('M4 failure injection left a package instance after transaction rollback.')
}

const evidence = sql(`
SELECT CONCAT('M4_TABLES=', COUNT(*))
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'package_product', 'package_product_item', 'package_instance',
    'package_instance_item', 'package_ledger'
  );
SELECT CONCAT('M4_PACKAGE_BALANCE=', remaining_quantity, '/', total_quantity)
FROM package_instance WHERE id = ${Number(issued.data.id)};
SELECT CONCAT('M4_WRITE_OFF_ROWS=', COUNT(*))
FROM package_ledger
WHERE package_instance_id = ${Number(issued.data.id)}
  AND entry_type = 'WRITE_OFF';
SELECT CONCAT('M4_REVERSAL_ROWS=', COUNT(*))
FROM package_ledger
WHERE package_instance_id = ${Number(issued.data.id)}
  AND entry_type = 'REVERSAL';
SELECT CONCAT('M4_ACCOUNT_REVERSALS=', COUNT(*))
FROM member_account_ledger
WHERE account_id = ${Number(account.id)}
  AND reversal_of_ledger_id = ${Number(debit.data.id)};
SELECT CONCAT('M4_OUTBOX_ROWS=', COUNT(*))
FROM outbox_event
WHERE aggregate_type IN ('PACKAGE_INSTANCE', 'MEMBER_ACCOUNT');
SELECT CONCAT('M4_ROLLBACK_INSTANCES=', COUNT(*))
FROM package_instance
WHERE source_order_id = ${Number(rollbackOrder.id)};
`)

for (const expected of [
  'M4_TABLES=5',
  'M4_PACKAGE_BALANCE=3.0000/3.0000',
  'M4_WRITE_OFF_ROWS=1',
  'M4_REVERSAL_ROWS=1',
  'M4_ACCOUNT_REVERSALS=1',
  'M4_ROLLBACK_INSTANCES=0',
]) {
  if (!evidence.includes(expected)) {
    throw new Error(`Missing M4 package-account evidence: ${expected}`)
  }
}
for (const line of evidence) console.log(line)
if (stateFile) {
  fs.writeFileSync(
    stateFile,
    JSON.stringify({
      adminV2Token,
      adminV3AccessToken: adminV3Token,
      technicianSession: {
        ...technicianV2Session,
        v3AccessToken: technicianV3Token,
      },
      technicianCredentials: {
        username: 'jishi01',
        password: 'Face@123',
      },
      memberSession: {
        ...memberV2Session,
        v3AccessToken: memberV3Token,
      },
      memberCredentials: {
        username: memberUsername,
        password: memberPassword,
      },
      memberId,
      packageInstanceId: issued.data.id,
      serviceRecordId: serviceRecord.id,
    }),
    { encoding: 'utf8', mode: 0o600 },
  )
}
console.log('M4_PACKAGE_ISSUE_IDEMPOTENCY=PASS')
console.log('M4_PACKAGE_WRITE_OFF_AND_REVERSAL=PASS')
console.log('M4_MEMBER_ACCOUNT_VERSION_AND_FREEZE=PASS')
console.log('M4_FAILURE_INJECTION_ROLLBACK=PASS')
