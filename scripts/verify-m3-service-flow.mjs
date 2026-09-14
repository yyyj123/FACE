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

if (!dbPassword) throw new Error('M3 verifier requires the isolated database password.')

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
    throw new Error(`M3 database assertion failed: ${result.stderr?.trim() || 'unknown error'}`)
  }
  return result.stdout.trim().split(/\r?\n/).filter(Boolean)
}

function prepareInventoryFixture() {
  sql(`
    INSERT INTO product_category (
      tenant_id, shop_id, name, sort_order, status
    ) VALUES (
      1, 1, 'M3 Acceptance Consumables', 9999, 'ACTIVE'
    )
    ON DUPLICATE KEY UPDATE status = 'ACTIVE';

    SET @m3_category_id = (
      SELECT id
      FROM product_category
      WHERE tenant_id = 1
        AND shop_id = 1
        AND name = 'M3 Acceptance Consumables'
      LIMIT 1
    );

    INSERT INTO product (
      tenant_id, shop_id, category_id, sku, name, brand_name,
      unit_name, cost_price, sale_price, stock_quantity,
      warning_quantity, is_consumable, status
    ) VALUES (
      1, 1, @m3_category_id, 'M3-ACCEPT-CONSUMABLE',
      'M3 Acceptance Consumable', 'FACE', 'unit',
      1, 1, 0, 0, 1, 'ACTIVE'
    )
    ON DUPLICATE KEY UPDATE
      category_id = @m3_category_id,
      is_consumable = 1,
      status = 'ACTIVE';
  `)
  const candidates = sql(`
    SELECT p.id, sl.id
    FROM product p
    JOIN stock_location sl
      ON sl.tenant_id = p.tenant_id
     AND sl.shop_id = p.shop_id
    WHERE p.tenant_id = 1
      AND p.shop_id = 1
      AND p.status = 'ACTIVE'
      AND p.is_consumable = 1
      AND sl.status = 'ACTIVE'
    ORDER BY p.id, sl.id
    LIMIT 1;
  `)
  if (candidates.length !== 1) {
    throw new Error('M3 isolated inventory fixture could not find an active consumable and location.')
  }
  const [productId, locationId] = candidates[0].split('\t').map(Number)
  if (!Number.isSafeInteger(productId) || !Number.isSafeInteger(locationId)) {
    throw new Error('M3 isolated inventory fixture returned invalid product or location ids.')
  }
  sql(`
    INSERT INTO stock_balance (
      tenant_id, location_id, product_id,
      quantity_on_hand, quantity_reserved, version
    ) VALUES (
      1, ${locationId}, ${productId}, 10, 0, 0
    )
    ON DUPLICATE KEY UPDATE
      quantity_on_hand = GREATEST(quantity_on_hand, 10),
      quantity_reserved = 0,
      version = version + 1;
  `)
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
      `M3 API ${method} ${path} returned ${response.status}: ${payload?.msg ?? payload?.message ?? 'no message'}`,
    )
  }
  return { status: response.status, payload, data: payload?.data }
}

function idempotency(prefix) {
  return `${prefix}-${suffix}`.slice(0, 80)
}

function futureAt(days, hour, minute = 0) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  date.setHours(hour, minute, 0, 0)
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 19)
}

async function v2Login(username, password) {
  const response = await request('/api/v2/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  if (!response.data?.token) throw new Error('M3 V2 login did not return a token.')
  return response.data.token
}

async function v3Login(username, password) {
  const response = await request('/api/v3/auth/login', {
    method: 'POST',
    body: { username, password },
  })
  if (!response.data?.access_token) throw new Error('M3 V3 login did not return an access token.')
  return response.data.access_token
}

async function createAndStartAppointment(adminToken, technicianToken, memberId, startAt) {
  const created = await request('/api/v2/appointments', {
    method: 'POST',
    token: adminToken,
    body: {
      shopId: 1,
      memberId,
      staffId: 2,
      serviceIds: [1],
      resourceIds: [],
      startAt,
      source: 'FRONT_DESK',
      internalNote: 'M3 automated acceptance',
    },
  })
  let appointment = created.data
  for (const status of ['CONFIRMED', 'CHECKED_IN']) {
    const changed = await request(`/api/v2/appointments/${appointment.id}/status`, {
      method: 'POST',
      token: adminToken,
      body: {
        shopId: 1,
        status,
        version: appointment.version,
        reason: `M3 ${status}`,
      },
    })
    appointment = changed.data
  }
  const started = await request('/api/v2/client/service-records/start', {
    method: 'POST',
    token: technicianToken,
    body: {
      shopId: 1,
      appointmentId: appointment.id,
      appointmentVersion: appointment.version,
    },
  })
  return { appointment, serviceRecord: started.data }
}

function completionBody(record, consumable, key, summary) {
  return {
    shopId: 1,
    version: record.version,
    serviceSummary: summary,
    nextVisitRecommendation: '建议四周后复访',
    skinType: '混合性',
    concerns: ['补水'],
    observations: '自动验收护理观察',
    homeCareAdvice: '晚间加强保湿',
    nextRecommendedAt: new Date(Date.now() + 28 * 86_400_000).toISOString().slice(0, 10),
    consumptions: [{
      locationId: consumable.locationId,
      productId: consumable.productId,
      quantity: 1,
      balanceVersion: consumable.balanceVersion,
    }],
    idempotencyKey: key,
  }
}

const username = `m3_member_${suffix}`
const memberPassword = 'M3-Test@123'
const syntheticPhone = `188${suffix.slice(-8)}`

const adminV2Token = await v2Login('admin', 'Face@123')
const technicianSession = await request('/api/v2/client/auth/login', {
  method: 'POST',
  body: { username: 'jishi01', password: 'Face@123' },
}).then((result) => result.data)
const technicianToken = technicianSession?.token
if (!technicianToken) throw new Error('M3 technician login did not return a token.')

const registration = await request('/api/v2/client/auth/register', {
  method: 'POST',
  body: {
    username,
    password: memberPassword,
    name: 'M3测试会员',
    phone: syntheticPhone,
    shopId: 1,
  },
})
const memberId = registration.data?.memberId
if (!memberId) throw new Error('M3 member registration failed.')
const memberSession = await request('/api/v2/client/auth/login', {
  method: 'POST',
  body: { username, password: memberPassword },
}).then((result) => result.data)
const memberToken = memberSession?.token
if (!memberToken) throw new Error('M3 member login did not return a token.')

prepareInventoryFixture()

const successFlow = await createAndStartAppointment(
  adminV2Token,
  technicianToken,
  memberId,
  futureAt(3, 16, 0),
)
const resources = await request('/api/v2/client/service-records/resources?shopId=1', {
  token: technicianToken,
})
const consumable = resources.data?.consumables?.find(
  (item) => Number(item.quantityAvailable) >= 2,
)
if (!consumable) throw new Error('M3 requires a consumable with at least two available units.')

const successKey = idempotency('m3-complete-success')
const successBody = completionBody(
  successFlow.serviceRecord,
  consumable,
  successKey,
  'M3成功链路护理完成',
)
const completed = await request(
  `/api/v2/client/service-records/${successFlow.serviceRecord.id}/complete`,
  {
    method: 'POST',
    token: technicianToken,
    body: successBody,
  },
)
if (completed.data?.status !== 'COMPLETED') throw new Error('M3 care completion did not complete.')

const completionReplay = await request(
  `/api/v2/client/service-records/${successFlow.serviceRecord.id}/complete`,
  {
    method: 'POST',
    token: technicianToken,
    body: successBody,
  },
)
if (completionReplay.data?.id !== completed.data?.id) {
  throw new Error('M3 care completion replay returned a different record.')
}
await request(
  `/api/v2/client/service-records/${successFlow.serviceRecord.id}/complete`,
  {
    method: 'POST',
    token: technicianToken,
    body: { ...successBody, serviceSummary: '不同请求摘要' },
    expected: [409],
  },
)

const adminV3Session = await request('/api/v3/auth/login', {
  method: 'POST',
  body: { username: 'admin', password: 'Face@123' },
}).then((result) => result.data)
const adminV3Token = adminV3Session?.access_token
if (!adminV3Token) throw new Error('M3 V3 login did not return an access token.')
const orderKey = idempotency('m3-order')
const orderBody = {
  shop_id: 1,
  member_id: memberId,
  appointment_id: successFlow.appointment.id,
  items: [],
  notes: 'M3 automated acceptance',
}
const order = await request('/api/v3/orders', {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': orderKey },
  body: orderBody,
})
const orderReplay = await request('/api/v3/orders', {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': orderKey },
  body: orderBody,
})
if (order.data?.id !== orderReplay.data?.id) {
  throw new Error('M3 order replay returned a different order.')
}
await request('/api/v3/orders', {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': orderKey },
  body: { ...orderBody, notes: 'different request' },
  expected: [409],
})

const paymentKey = idempotency('m3-payment')
const paymentBody = {
  shop_id: 1,
  payment_method: 'CASH',
  amount: order.data.payableAmount,
  version: order.data.version,
}
const payment = await request(`/api/v3/orders/${order.data.id}/payments`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': paymentKey },
  body: paymentBody,
})
const paymentReplay = await request(`/api/v3/orders/${order.data.id}/payments`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': paymentKey },
  body: paymentBody,
})
if (payment.data?.id !== paymentReplay.data?.id) {
  throw new Error('M3 payment replay returned a different payment.')
}
await request(`/api/v3/orders/${order.data.id}/payments`, {
  method: 'POST',
  token: adminV3Token,
  headers: { 'Idempotency-Key': paymentKey },
  body: { ...paymentBody, amount: Number(paymentBody.amount) - 0.01 },
  expected: [409],
})

const memberConfirmations = await request('/api/v2/client/confirmations', {
  token: memberToken,
})
const confirmation = memberConfirmations.data?.find(
  (item) => item.serviceRecordId === successFlow.serviceRecord.id,
)
if (!confirmation || confirmation.status !== 'PENDING') {
  throw new Error('M3 customer confirmation was not created as PENDING.')
}
const confirmationKey = idempotency('m3-confirmation')
const confirmationBody = {
  action: 'CONFIRMED',
  version: confirmation.version,
  idempotencyKey: confirmationKey,
}
await request(`/api/v2/client/confirmations/${confirmation.id}/action`, {
  method: 'POST',
  token: memberToken,
  body: confirmationBody,
})
await request(`/api/v2/client/confirmations/${confirmation.id}/action`, {
  method: 'POST',
  token: memberToken,
  body: confirmationBody,
})
await request(`/api/v2/client/confirmations/${confirmation.id}/action`, {
  method: 'POST',
  token: memberToken,
  body: {
    action: 'REJECTED',
    reason: 'different request',
    version: confirmation.version,
    idempotencyKey: confirmationKey,
  },
  expected: [409],
})

const correctionKey = idempotency('m3-correction')
const correctionBody = {
  shopId: 1,
  serviceRecordVersion: completed.data.version,
  reason: '补充复访说明',
  correctedFields: { nextVisitRecommendation: '建议三周后复访' },
  idempotencyKey: correctionKey,
}
await request(`/api/v2/service-records/${successFlow.serviceRecord.id}/corrections`, {
  method: 'POST',
  token: adminV2Token,
  body: correctionBody,
})
await request(`/api/v2/service-records/${successFlow.serviceRecord.id}/corrections`, {
  method: 'POST',
  token: adminV2Token,
  body: correctionBody,
})
await request(`/api/v2/service-records/${successFlow.serviceRecord.id}/corrections`, {
  method: 'POST',
  token: adminV2Token,
  body: {
    ...correctionBody,
    correctedFields: { nextVisitRecommendation: '不同请求' },
  },
  expected: [409],
})

const failureFlow = await createAndStartAppointment(
  adminV2Token,
  technicianToken,
  memberId,
  futureAt(5, 16, 30),
)
const failureResources = await request('/api/v2/client/service-records/resources?shopId=1', {
  token: technicianToken,
})
const failureConsumable = failureResources.data.consumables.find(
  (item) => item.productId === consumable.productId
    && item.locationId === consumable.locationId,
)
if (!failureConsumable) throw new Error('M3 failure injection consumable is unavailable.')
const failureKey = idempotency('m3-complete-failure')
const balanceBefore = sql(`
SELECT CONCAT(quantity_on_hand, ':', version)
FROM stock_balance
WHERE tenant_id = 1
  AND location_id = ${Number(failureConsumable.locationId)}
  AND product_id = ${Number(failureConsumable.productId)};
`)[0]

sql(`
DROP TRIGGER IF EXISTS m3_fail_customer_confirmation;
CREATE TRIGGER m3_fail_customer_confirmation
BEFORE INSERT ON customer_confirmation
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'M3_FAILURE_INJECTION';
`)
try {
  await request(
    `/api/v2/client/service-records/${failureFlow.serviceRecord.id}/complete`,
    {
      method: 'POST',
      token: technicianToken,
      body: completionBody(
        failureFlow.serviceRecord,
        failureConsumable,
        failureKey,
        'M3失败注入护理完成',
      ),
      expected: [500],
    },
  )
} finally {
  sql('DROP TRIGGER IF EXISTS m3_fail_customer_confirmation;')
}

const balanceAfter = sql(`
SELECT CONCAT(quantity_on_hand, ':', version)
FROM stock_balance
WHERE tenant_id = 1
  AND location_id = ${Number(failureConsumable.locationId)}
  AND product_id = ${Number(failureConsumable.productId)};
`)[0]
if (balanceBefore !== balanceAfter) {
  throw new Error('M3 failure injection changed stock balance despite transaction rollback.')
}

const uiFlow = await createAndStartAppointment(
  adminV2Token,
  technicianToken,
  memberId,
  futureAt(7, 16, 0),
)
const uiResources = await request('/api/v2/client/service-records/resources?shopId=1', {
  token: technicianToken,
})
const uiConsumable = uiResources.data.consumables.find(
  (item) => item.productId === consumable.productId
    && item.locationId === consumable.locationId,
)
if (!uiConsumable) throw new Error('M3 UI acceptance consumable is unavailable.')
await request(`/api/v2/client/service-records/${uiFlow.serviceRecord.id}/complete`, {
  method: 'POST',
  token: technicianToken,
  body: completionBody(
    uiFlow.serviceRecord,
    uiConsumable,
    idempotency('m3-complete-ui'),
    'M3 UI acceptance care completed',
  ),
})
const uiConfirmations = await request('/api/v2/client/confirmations', {
  token: memberToken,
})
const uiConfirmation = uiConfirmations.data?.find(
  (item) => item.serviceRecordId === uiFlow.serviceRecord.id,
)
if (!uiConfirmation || uiConfirmation.status !== 'PENDING') {
  throw new Error('M3 UI acceptance confirmation was not left pending.')
}
if (options['state-file']) {
  fs.writeFileSync(
    options['state-file'],
    JSON.stringify({
      adminV2Token,
      adminV3Session,
      technicianSession,
      memberSession,
      successServiceRecordId: successFlow.serviceRecord.id,
      inProgressServiceRecordId: failureFlow.serviceRecord.id,
      pendingConfirmationId: uiConfirmation.id,
    }),
    { encoding: 'utf8', mode: 0o600 },
  )
}

const evidence = sql(`
SELECT CONCAT('M3_SUCCESS_RECORD=', status)
FROM service_record WHERE id = ${Number(successFlow.serviceRecord.id)};
SELECT CONCAT('M3_SUCCESS_APPOINTMENT=', status)
FROM appointment WHERE id = ${Number(successFlow.appointment.id)};
SELECT CONCAT('M3_SUCCESS_CONFIRMATION=', status)
FROM customer_confirmation
WHERE service_record_id = ${Number(successFlow.serviceRecord.id)};
SELECT CONCAT('M3_SUCCESS_MOVEMENTS=', COUNT(*))
FROM inventory_movement
WHERE service_record_id = ${Number(successFlow.serviceRecord.id)};
SELECT CONCAT('M3_SUCCESS_OUTBOX=', COUNT(*))
FROM outbox_event
WHERE aggregate_type = 'SERVICE_RECORD'
  AND aggregate_id = '${Number(successFlow.serviceRecord.id)}'
  AND event_type = 'ServiceRecordCompleted';
SELECT CONCAT('M3_SUCCESS_CORRECTIONS=', COUNT(*))
FROM service_record_correction
WHERE service_record_id = ${Number(successFlow.serviceRecord.id)};
SELECT CONCAT('M3_SUCCESS_PAYMENTS=', COUNT(*))
FROM payment_transaction
WHERE order_id = ${Number(order.data.id)}
  AND status = 'SUCCESS';
SELECT CONCAT('M3_FAILURE_RECORD=', status)
FROM service_record WHERE id = ${Number(failureFlow.serviceRecord.id)};
SELECT CONCAT('M3_FAILURE_APPOINTMENT=', status)
FROM appointment WHERE id = ${Number(failureFlow.appointment.id)};
SELECT CONCAT('M3_FAILURE_CARE=', COUNT(*))
FROM care_record WHERE service_record_id = ${Number(failureFlow.serviceRecord.id)};
SELECT CONCAT('M3_FAILURE_CONFIRMATION=', COUNT(*))
FROM customer_confirmation WHERE service_record_id = ${Number(failureFlow.serviceRecord.id)};
SELECT CONCAT('M3_FAILURE_MOVEMENTS=', COUNT(*))
FROM inventory_movement
WHERE service_record_id = ${Number(failureFlow.serviceRecord.id)};
SELECT CONCAT('M3_FAILURE_OUTBOX=', COUNT(*))
FROM outbox_event
WHERE aggregate_type = 'SERVICE_RECORD'
  AND aggregate_id = '${Number(failureFlow.serviceRecord.id)}';
`)

const exact = [
  'M3_SUCCESS_RECORD=COMPLETED',
  'M3_SUCCESS_APPOINTMENT=COMPLETED',
  'M3_SUCCESS_CONFIRMATION=CONFIRMED',
  'M3_SUCCESS_MOVEMENTS=1',
  'M3_SUCCESS_OUTBOX=1',
  'M3_SUCCESS_CORRECTIONS=1',
  'M3_SUCCESS_PAYMENTS=1',
  'M3_FAILURE_RECORD=IN_PROGRESS',
  'M3_FAILURE_APPOINTMENT=IN_SERVICE',
  'M3_FAILURE_CARE=0',
  'M3_FAILURE_CONFIRMATION=0',
  'M3_FAILURE_MOVEMENTS=0',
  'M3_FAILURE_OUTBOX=0',
]
for (const expected of exact) {
  if (!evidence.includes(expected)) {
    throw new Error(`Missing M3 service-flow evidence: ${expected}`)
  }
}

for (const line of evidence) console.log(line)
console.log('M3_IDEMPOTENCY_REPLAY=PASS')
console.log('M3_FAILURE_INJECTION_ROLLBACK=PASS')
console.log('M3_THREE_ROLE_SERVICE_FLOW=PASS')
