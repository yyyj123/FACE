import { spawnSync } from 'node:child_process'
import fs from 'node:fs'

const options = Object.fromEntries(
  process.argv.slice(2).map((entry) => {
    const [key, ...parts] = entry.replace(/^--/, '').split('=')
    return [key, parts.join('=')]
  }),
)
const backend = options.backend
const dbPort = Number(options['db-port'])
const dbPassword = options['db-password']
const stateFile = options['state-file']
const mysql = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
const suffix = Date.now().toString().slice(-9)

if (!backend || !dbPassword || !stateFile || !fs.existsSync(stateFile)) {
  throw new Error('M4 refund verifier requires backend, isolated DB and state file.')
}
const state = JSON.parse(fs.readFileSync(stateFile, 'utf8'))

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
    throw new Error(`M4 refund DB assertion failed: ${result.stderr?.trim()}`)
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
      `M4 refund ${method} ${path} returned ${response.status}: ${payload?.message ?? payload?.msg ?? 'no message'}`,
    )
  }
  return { status: response.status, data: payload?.data }
}

function key(prefix) {
  return `${prefix}-${suffix}`.slice(0, 80)
}

async function login(username) {
  const result = await request('/api/v3/auth/login', {
    method: 'POST',
    body: { username, password: 'Face@123' },
  })
  return result.data.access_token
}

async function createPaidServiceOrder(paymentMethod, prefix) {
  const order = await request('/api/v3/orders', {
    method: 'POST',
    token: state.adminV3AccessToken,
    headers: { 'Idempotency-Key': key(`${prefix}-order`) },
    body: {
      shop_id: 1,
      member_id: state.memberId,
      items: [{
        itemType: 'SERVICE',
        referenceId: 1,
        quantity: 1,
        discountAmount: 0,
      }],
      notes: 'M4 refund isolated acceptance',
    },
  })
  if (paymentMethod === 'WECHAT') {
    await request(`/api/v3/orders/${order.data.id}/payments`, {
      method: 'POST',
      token: state.adminV3AccessToken,
      headers: { 'Idempotency-Key': key(`${prefix}-unconfigured-payment`) },
      body: {
        shop_id: 1,
        payment_method: paymentMethod,
        amount: order.data.payableAmount,
        version: order.data.version,
      },
      expected: [503],
    })
    const [paymentId] = sql(`
      INSERT INTO payment_transaction (
        tenant_id, shop_id, order_id, payment_no, payment_method, channel_code,
        amount, refunded_amount, currency_code, external_transaction_no,
        channel_status, channel_request_no, idempotency_key, status,
        paid_at, confirmed_at, created_by
      )
      SELECT
        so.tenant_id, so.shop_id, so.id, 'M4-LEGACY-PAY-${suffix}', 'WECHAT', 'WECHAT',
        so.payable_amount, 0.00, so.currency_code, 'M4-LEGACY-EXT-${suffix}',
        'SUCCESS', 'M4-LEGACY-REQUEST-${suffix}', 'M4-LEGACY-IDEMPOTENCY-${suffix}',
        'SUCCESS', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), a.id
      FROM sales_order so
      JOIN account a ON a.tenant_id = so.tenant_id AND a.username = 'admin'
      WHERE so.id = ${Number(order.data.id)};
      SET @legacy_payment_id = LAST_INSERT_ID();
      UPDATE sales_order
      SET paid_amount = payable_amount,
          status = 'PAID',
          version = version + 1,
          updated_by = created_by
      WHERE id = ${Number(order.data.id)};
      SELECT @legacy_payment_id;
    `)
    const payment = await request(`/api/v3/payments/${Number(paymentId)}?shop_id=1`, {
      token: state.adminV3AccessToken,
    })
    return { order: order.data, payment: payment.data }
  }
  const payment = await request(`/api/v3/orders/${order.data.id}/payments`, {
    method: 'POST',
    token: state.adminV3AccessToken,
    headers: { 'Idempotency-Key': key(`${prefix}-payment`) },
    body: {
      shop_id: 1,
      payment_method: paymentMethod,
      amount: order.data.payableAmount,
      version: order.data.version,
    },
  })
  return { order: order.data, payment: payment.data }
}

async function createRefund(orderId, paymentId, amount, prefix) {
  const body = {
    shop_id: 1,
    order_id: orderId,
    amount,
    reason: '隔离验收退款',
  }
  const idempotencyKey = key(`${prefix}-request`)
  const created = await request(`/api/v3/payments/${paymentId}/refunds`, {
    method: 'POST',
    token: state.adminV3AccessToken,
    headers: { 'Idempotency-Key': idempotencyKey },
    body,
  })
  const replay = await request(`/api/v3/payments/${paymentId}/refunds`, {
    method: 'POST',
    token: state.adminV3AccessToken,
    headers: { 'Idempotency-Key': idempotencyKey },
    body,
  })
  if (created.data.id !== replay.data.id) {
    throw new Error('Refund request idempotency replay created a second refund.')
  }
  await request(`/api/v3/payments/${paymentId}/refunds`, {
    method: 'POST',
    token: state.adminV3AccessToken,
    headers: { 'Idempotency-Key': idempotencyKey },
    body: { ...body, reason: '不同载荷' },
    expected: [409],
  })
  return created.data
}

const managerUsername = `m4manager${suffix}`
sql(`
INSERT INTO staff (
  tenant_id, home_shop_id, shop_id, staff_no, name, job_role, status
) VALUES (
  1, 1, 1, 'M4RF${suffix}', 'M4退款审批员', '店长', 'ACTIVE'
);
SET @manager_staff_id = LAST_INSERT_ID();
INSERT INTO account (
  tenant_id, home_shop_id, shop_id, username, display_name,
  password_hash, role_code, staff_id, status
)
SELECT tenant_id, 1, 1, '${managerUsername}', 'M4退款审批员',
       password_hash, 'MANAGER', @manager_staff_id, 'ACTIVE'
FROM account
WHERE username = 'admin'
LIMIT 1;
SET @manager_id = LAST_INSERT_ID();
INSERT INTO account_shop_role (
  tenant_id, account_id, shop_id, role_id, status
)
SELECT 1, @manager_id, 1, id, 'ACTIVE'
FROM role_definition
WHERE tenant_id = 1 AND role_code = 'MANAGER'
LIMIT 1;
`)
const managerToken = await login(managerUsername)

const [packageOrderRow] = sql(`
SELECT CONCAT(pi.source_order_id, '|', pt.id, '|', pt.amount)
FROM package_instance pi
JOIN payment_transaction pt
  ON pt.order_id = pi.source_order_id AND pt.status = 'SUCCESS'
WHERE pi.id = ${Number(state.packageInstanceId)}
LIMIT 1;
`)
const [packageOrderId, packagePaymentId, packageAmount] = packageOrderRow.split('|')
const packageRefund = await createRefund(
  Number(packageOrderId),
  Number(packagePaymentId),
  packageAmount,
  'm4-package-refund',
)

await request(`/api/v3/refunds/${packageRefund.id}/decision`, {
  method: 'POST',
  token: state.adminV3AccessToken,
  headers: { 'Idempotency-Key': key('m4-self-approval') },
  body: {
    shop_id: 1,
    version: packageRefund.version,
    action: 'APPROVE',
    decision_note: '应被职责分离拒绝',
  },
  expected: [403],
})
const approved = await request(`/api/v3/refunds/${packageRefund.id}/decision`, {
  method: 'POST',
  token: managerToken,
  headers: { 'Idempotency-Key': key('m4-manager-approval') },
  body: {
    shop_id: 1,
    version: packageRefund.version,
    action: 'APPROVE',
    decision_note: '隔离验收审批通过',
  },
})
const executeBody = { shop_id: 1, version: approved.data.version }
const executeKey = key('m4-package-execute')
const executed = await request(`/api/v3/refunds/${packageRefund.id}/execute`, {
  method: 'POST',
  token: managerToken,
  headers: { 'Idempotency-Key': executeKey },
  body: executeBody,
})
const executeReplay = await request(`/api/v3/refunds/${packageRefund.id}/execute`, {
  method: 'POST',
  token: managerToken,
  headers: { 'Idempotency-Key': executeKey },
  body: executeBody,
})
if (executed.data.id !== executeReplay.data.id || executed.data.status !== 'SUCCESS') {
  throw new Error('Refund execution idempotency replay failed.')
}

const memberAccounts = await request(`/api/v3/members/${state.memberId}/accounts?shop_id=1`, {
  token: state.adminV3AccessToken,
})
const balanceAccount = memberAccounts.data.find((item) => item.accountType === 'BALANCE')
if (!balanceAccount) throw new Error('Balance refund verifier requires a BALANCE account.')
await request(`/api/v3/member-accounts/${balanceAccount.id}/credits`, {
  method: 'POST',
  token: state.adminV3AccessToken,
  headers: { 'Idempotency-Key': key('m4-balance-funding') },
  body: {
    shop_id: 1,
    amount: 1000,
    entry_type: 'MANUAL_CREDIT',
    remark: 'M4退款隔离验收测试入账',
    version: balanceAccount.version,
  },
})
const [balanceBefore] = sql(`
SELECT CAST(balance AS CHAR)
FROM member_account
WHERE tenant_id = 1
  AND member_id = ${Number(state.memberId)}
  AND account_type = 'BALANCE'
LIMIT 1;
`)
const balanceOrder = await createPaidServiceOrder('BALANCE', 'm4-balance')
const balanceRefund = await createRefund(
  balanceOrder.order.id,
  balanceOrder.payment.id,
  balanceOrder.payment.amount,
  'm4-balance-refund',
)
const balanceApproved = await request(`/api/v3/refunds/${balanceRefund.id}/decision`, {
  method: 'POST',
  token: managerToken,
  headers: { 'Idempotency-Key': key('m4-balance-approval') },
  body: {
    shop_id: 1,
    version: balanceRefund.version,
    action: 'APPROVE',
    decision_note: '余额退款隔离验收',
  },
})
const balanceExecuted = await request(`/api/v3/refunds/${balanceRefund.id}/execute`, {
  method: 'POST',
  token: managerToken,
  headers: { 'Idempotency-Key': key('m4-balance-execute') },
  body: { shop_id: 1, version: balanceApproved.data.version },
})
if (balanceExecuted.data.status !== 'SUCCESS') {
  throw new Error('Balance refund did not complete.')
}
const [balanceAfter] = sql(`
SELECT CAST(balance AS CHAR)
FROM member_account
WHERE tenant_id = 1
  AND member_id = ${Number(state.memberId)}
  AND account_type = 'BALANCE'
LIMIT 1;
`)
if (Number(balanceBefore).toFixed(2) !== Number(balanceAfter).toFixed(2)) {
  throw new Error(`Balance refund did not restore balance: ${balanceBefore} -> ${balanceAfter}`)
}
const memberRefunds = await request(`/api/v3/members/${state.memberId}/refunds?shop_id=1`, {
  token: state.memberSession.v3AccessToken,
})
const visibleRefundIds = new Set(memberRefunds.data.map((item) => Number(item.id)))
if (
  !visibleRefundIds.has(Number(packageRefund.id))
  || !visibleRefundIds.has(Number(balanceRefund.id))
) {
  throw new Error('Member refund progress endpoint did not return the member-owned refunds.')
}
await request(`/api/v3/members/${Number(state.memberId) + 999}/refunds?shop_id=1`, {
  token: state.memberSession.v3AccessToken,
  expected: [404],
})

const external = await createPaidServiceOrder('WECHAT', 'm4-external')
const externalRefund = await createRefund(
  external.order.id,
  external.payment.id,
  external.payment.amount,
  'm4-external-refund',
)
const externalApproved = await request(`/api/v3/refunds/${externalRefund.id}/decision`, {
  method: 'POST',
  token: managerToken,
  headers: { 'Idempotency-Key': key('m4-external-approval') },
  body: {
    shop_id: 1,
    version: externalRefund.version,
    action: 'APPROVE',
    decision_note: '外部通道保持待执行',
  },
})
await request(`/api/v3/refunds/${externalRefund.id}/execute`, {
  method: 'POST',
  token: managerToken,
  headers: { 'Idempotency-Key': key('m4-external-execute') },
  body: { shop_id: 1, version: externalApproved.data.version },
  expected: [503],
})
const externalAfter = await request(`/api/v3/refunds/${externalRefund.id}?shop_id=1`, {
  token: managerToken,
})
if (externalAfter.data.status !== 'APPROVED') {
  throw new Error('External refund was falsely marked successful.')
}

const evidence = sql(`
SELECT CONCAT('M4_REFUND_STATUS=', status)
FROM refund_transaction WHERE id = ${Number(packageRefund.id)};
SELECT CONCAT('M4_REFUND_REQUEST_HASH=', CHAR_LENGTH(request_hash))
FROM refund_transaction WHERE id = ${Number(packageRefund.id)};
SELECT CONCAT('M4_REFUND_EXECUTION_HASH=', CHAR_LENGTH(execution_request_hash))
FROM refund_transaction WHERE id = ${Number(packageRefund.id)};
SELECT CONCAT('M4_REFUND_PACKAGE_STATUS=', status)
FROM package_instance WHERE id = ${Number(state.packageInstanceId)};
SELECT CONCAT('M4_REFUND_PACKAGE_REVERSALS=', COUNT(*))
FROM package_ledger
WHERE package_instance_id = ${Number(state.packageInstanceId)}
  AND business_key = 'REFUND:${Number(packageRefund.id)}:${Number(state.packageInstanceId)}';
SELECT CONCAT('M4_REFUND_OUTBOX=', COUNT(*))
FROM outbox_event
WHERE aggregate_type = 'REFUND_TRANSACTION'
  AND aggregate_id = '${Number(packageRefund.id)}'
  AND event_type = 'RefundCompleted';
SELECT CONCAT('M4_EXTERNAL_REFUND_STATUS=', status)
FROM refund_transaction WHERE id = ${Number(externalRefund.id)};
SELECT CONCAT('M4_BALANCE_REFUND_STATUS=', status)
FROM refund_transaction WHERE id = ${Number(balanceRefund.id)};
SELECT CONCAT('M4_BALANCE_REFUND_LEDGER=', COUNT(*))
FROM member_account_ledger
WHERE tenant_id = 1
  AND entry_type = 'ORDER_REFUND'
  AND reference_type = 'REFUND_TRANSACTION'
  AND reference_id = ${Number(balanceRefund.id)};
SELECT CONCAT('M4_REFUND_COLUMNS=', COUNT(*))
FROM information_schema.columns
WHERE table_schema = 'face_salon'
  AND table_name = 'refund_transaction'
  AND column_name IN (
    'request_hash', 'execution_idempotency_key', 'execution_request_hash',
    'execution_mode', 'external_refund_no', 'channel_status',
    'failure_code', 'failed_at', 'executed_by'
  );
SELECT CONCAT('M4_REFUND_PERMISSIONS=', COUNT(*))
FROM permission_definition
WHERE permission_code IN ('refund:request', 'refund:execute');
SELECT CONCAT('M4_REFUND_PROCESSING_CONSTRAINT=', COUNT(*))
FROM information_schema.check_constraints
WHERE constraint_schema = 'face_salon'
  AND constraint_name = 'ck_refund_status'
  AND check_clause LIKE '%PROCESSING%';
`)
for (const expected of [
  'M4_REFUND_STATUS=SUCCESS',
  'M4_REFUND_REQUEST_HASH=64',
  'M4_REFUND_EXECUTION_HASH=64',
  'M4_REFUND_PACKAGE_STATUS=CANCELLED',
  'M4_REFUND_PACKAGE_REVERSALS=1',
  'M4_REFUND_OUTBOX=1',
  'M4_EXTERNAL_REFUND_STATUS=APPROVED',
  'M4_BALANCE_REFUND_STATUS=SUCCESS',
  'M4_BALANCE_REFUND_LEDGER=1',
  'M4_REFUND_COLUMNS=9',
  'M4_REFUND_PERMISSIONS=2',
  'M4_REFUND_PROCESSING_CONSTRAINT=1',
]) {
  if (!evidence.includes(expected)) {
    throw new Error(`Missing M4 refund evidence: ${expected}`)
  }
}
for (const line of evidence) console.log(line)
console.log('M4_REFUND_REQUEST_IDEMPOTENCY=PASS')
console.log('M4_REFUND_SEPARATION_OF_DUTIES=PASS')
console.log('M4_REFUND_PACKAGE_REVERSAL=PASS')
console.log('M4_BALANCE_REFUND_RESTORED=PASS')
console.log('M4_MEMBER_REFUND_SCOPE=PASS')
console.log('M4_EXTERNAL_REFUND_NOT_FAKED=PASS')
