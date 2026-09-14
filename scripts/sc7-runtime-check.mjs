import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const options = Object.fromEntries(process.argv.slice(2).map((entry) => {
  const [key, ...rest] = entry.replace(/^--/, '').split('=')
  return [key, rest.join('=')]
}))
const { admin, project, root } = options
const envFile = options['env-file']
if (!admin || !project || !root || !envFile) {
  throw new Error('SC7 runtime checker requires admin, project, root and env-file.')
}

const suffix = Date.now().toString().slice(-9)
const phone = `138${suffix.slice(-8)}`
const fixture = path.join(os.tmpdir(), `face-sc7-${suffix}.xlsx`)
const conflictFixture = path.join(os.tmpdir(), `face-sc7-conflict-${suffix}.xlsx`)
const expect = (condition, message) => { if (!condition) throw new Error(message) }
const value = (object, camel, snake) => object?.[camel] ?? object?.[snake]

function compose(args, input) {
  const result = spawnSync('docker', [
    'compose', '--project-name', project, '--project-directory', root,
    '--env-file', envFile, '-f', `${root}/compose.yaml`, ...args,
  ], { input, encoding: 'utf8' })
  if (result.status !== 0) throw new Error(`SC7 compose command failed: ${result.stderr}`)
  return result.stdout
}

function sql(statement) {
  return compose([
    'exec', '-T', 'mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot face_salon',
  ], statement).trim().split(/\r?\n/).filter(Boolean)
}

async function request(base, requestPath, {
  method = 'GET', token, body, headers = {}, expected = [200], rawBody,
} = {}) {
  const content = rawBody ?? (body === undefined ? undefined : JSON.stringify(body))
  const response = await fetch(`${base}${requestPath}`, {
    method,
    headers: {
      ...(rawBody || content === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: content,
  })
  const text = await response.text()
  let payload
  try { payload = text ? JSON.parse(text) : undefined } catch { payload = undefined }
  if (!expected.includes(response.status)) {
    throw new Error(`${method} ${requestPath} returned ${response.status}: ${payload?.message ?? payload?.msg ?? text}`)
  }
  return { status: response.status, data: payload?.data, payload }
}

function writeFixture(output, memberName, shopCode, codes) {
  const mavenArgs = [
    '-q', 'test-compile', 'org.codehaus.mojo:exec-maven-plugin:3.5.0:java',
    '-Dexec.classpathScope=test',
    '-Dexec.mainClass=com.face.platform.legacyimport.LegacyImportFixtureWriter',
    `-Dexec.args=${output} ${suffix} ${shopCode} ${codes.combo} ${codes.stored} ${codes.discount} ${codes.service} ${memberName}`,
  ]
  const command = process.platform === 'win32' ? process.env.ComSpec : 'mvn'
  const args = process.platform === 'win32' ? ['/d', '/s', '/c', 'mvn.cmd', ...mavenArgs] : mavenArgs
  const result = spawnSync(command, args, {
    cwd: path.join(root, 'backend-next'),
    encoding: 'utf8',
    windowsHide: true,
  })
  if (result.status !== 0) {
    throw new Error(`SC7 Excel fixture generation failed: ${result.error?.message || result.stderr || result.stdout || `exit ${result.status}`}`)
  }
}

async function upload(token, filePath, expected = [200], surface = 'DESKTOP') {
  const form = new FormData()
  form.append('file', new Blob([fs.readFileSync(filePath)]), path.basename(filePath))
  return request(admin, '/api/v3/legacy-import/preflight?shopId=1', {
    method: 'POST', token, rawBody: form, expected,
    headers: surface ? { 'X-FACE-Admin-Surface': surface } : {},
  })
}

try {
  const roles = sql("SELECT role_code FROM role_definition WHERE tenant_id = 1 AND role_code IN ('ADMIN','SUPER_ADMIN') ORDER BY role_code;")
  expect(roles.includes('ADMIN') && roles.includes('SUPER_ADMIN'), 'SC7 visible admin roles are missing.')
  sql(`
    SET @admin_id := (SELECT id FROM account WHERE tenant_id = 1 AND username = 'admin' LIMIT 1);
    SET @service_id := (SELECT id FROM service_item WHERE tenant_id = 1 AND shop_id = 1 AND status = 'ACTIVE' ORDER BY id LIMIT 1);
    INSERT INTO package_product (tenant_id, shop_id, package_code, card_type, name, sale_price, validity_days, status, created_by, updated_by)
      VALUES (1, 1, 'SC7-COMBO-${suffix}', 'COMBO_TIMES', 'SC7 Combo', 0, 3650, 'ACTIVE', @admin_id, @admin_id),
             (1, 1, 'SC7-STORED-${suffix}', 'STORED_VALUE', 'SC7 Stored', 0, 3650, 'ACTIVE', @admin_id, @admin_id),
             (1, 1, 'SC7-DISCOUNT-${suffix}', 'DISCOUNT', 'SC7 Discount', 0, 3650, 'ACTIVE', @admin_id, @admin_id);
    SET @combo_id := (SELECT id FROM package_product WHERE tenant_id = 1 AND package_code = 'SC7-COMBO-${suffix}');
    INSERT INTO package_product_item (tenant_id, package_product_id, service_id, service_name_snapshot, quantity_total)
      SELECT 1, @combo_id, @service_id, name, 10 FROM service_item WHERE id = @service_id;
    INSERT INTO account (tenant_id, home_shop_id, shop_id, username, display_name, password_hash, role_code, status)
      SELECT 1, 1, 1, 'sc7-operator-${suffix}', 'SC7 Ordinary Admin', password_hash, 'ADMIN', 'ACTIVE'
      FROM account WHERE username = 'admin' LIMIT 1;
    SET @operator_id := LAST_INSERT_ID();
    INSERT INTO account_shop_role (tenant_id, account_id, shop_id, role_id, status)
      SELECT 1, @operator_id, 1, id, 'ACTIVE' FROM role_definition WHERE tenant_id = 1 AND role_code = 'ADMIN';
  `)
  const shopCode = sql('SELECT shop_code FROM shop WHERE id = 1;').at(-1)
  const serviceCode = sql('SELECT service_code FROM service_item WHERE tenant_id = 1 AND shop_id = 1 AND status = \'ACTIVE\' ORDER BY id LIMIT 1;').at(-1)
  const codes = {
    combo: `SC7-COMBO-${suffix}`, stored: `SC7-STORED-${suffix}`,
    discount: `SC7-DISCOUNT-${suffix}`, service: serviceCode,
  }
  writeFixture(fixture, `SC7Member${suffix}`, shopCode, codes)
  writeFixture(conflictFixture, `Conflict${suffix}`, shopCode, codes)

  const adminToken = value((await request(admin, '/api/v3/auth/admin-login', {
    method: 'POST', body: { username: 'admin', password: 'Face@123' },
  })).data, 'accessToken', 'access_token')
  const operatorToken = value((await request(admin, '/api/v3/auth/admin-login', {
    method: 'POST', body: { username: `sc7-operator-${suffix}`, password: 'Face@123' },
  })).data, 'accessToken', 'access_token')
  expect(adminToken && operatorToken, 'SC7 admin tokens were not issued.')

  await upload(operatorToken, fixture, [403], null)
  await upload(operatorToken, fixture, [403], 'MOBILE')
  await request(admin, '/api/v3/admin-accounts', {
    token: adminToken, expected: [403], headers: { 'X-FACE-Admin-Surface': 'MOBILE' },
  })
  await request(admin, '/api/v3/admin-accounts', { token: adminToken, expected: [403] })
  const desktopAccounts = (await request(admin, '/api/v3/admin-accounts', {
    token: adminToken, headers: { 'X-FACE-Admin-Surface': 'DESKTOP' },
  })).data
  expect(Array.isArray(desktopAccounts), 'Desktop administrator account access was not preserved.')
  const preflight = (await upload(operatorToken, fixture)).data
  expect(preflight.totalRows === 5 && preflight.conflictRows === 0 && preflight.errorRows === 0, 'SC7 preflight did not accept the five-sheet fixture.')
  const duplicatePreflight = (await upload(operatorToken, fixture)).data
  expect(duplicatePreflight.id === preflight.id, 'Repeated file upload did not replay the original batch.')

  const operatorExecute = await request(admin, `/api/v3/legacy-import/batches/${preflight.id}/execute`, {
    method: 'POST', token: operatorToken, expected: [403],
    headers: { 'Idempotency-Key': `operator-${suffix}`, 'X-FACE-Admin-Surface': 'DESKTOP' },
    body: { shopId: 1 },
  })
  expect(operatorExecute.status === 403, 'Ordinary admin unexpectedly executed formal import.')
  await request(admin, `/api/v3/legacy-import/batches/${preflight.id}/execute`, {
    method: 'POST', token: adminToken, expected: [403],
    headers: { 'Idempotency-Key': `mobile-${suffix}` }, body: { shopId: 1 },
  })

  const executeKey = `execute-${suffix}`
  const imported = (await request(admin, `/api/v3/legacy-import/batches/${preflight.id}/execute`, {
    method: 'POST', token: adminToken,
    headers: { 'Idempotency-Key': executeKey, 'X-FACE-Admin-Surface': 'DESKTOP' },
    body: { shopId: 1 },
  })).data
  expect(imported.status === 'COMPLETED' && imported.importedMembers === 1 && imported.importedCards === 3, 'SC7 formal import result is incomplete.')
  const replay = (await request(admin, `/api/v3/legacy-import/batches/${preflight.id}/execute`, {
    method: 'POST', token: adminToken,
    headers: { 'Idempotency-Key': executeKey, 'X-FACE-Admin-Surface': 'DESKTOP' },
    body: { shopId: 1 },
  })).data
  expect(replay.id === preflight.id && replay.importedCards === 3, 'SC7 formal import replay changed the result.')

  expect(sql(`SELECT COUNT(*) FROM legacy_member_source_map WHERE source_system='SC7_RUNTIME' AND original_member_no='MEM-${suffix}';`).at(-1) === '1', 'Repeated import duplicated source member mapping.')
  expect(sql(`SELECT COUNT(*) FROM package_instance WHERE legacy_source_system='SC7_RUNTIME' AND legacy_original_card_no IN ('COMBO-${suffix}','STORED-${suffix}','DISCOUNT-${suffix}');`).at(-1) === '3', 'Repeated import duplicated or lost historical cards.')
  expect(sql(`SELECT COUNT(*) FROM package_ledger pl JOIN package_instance pi ON pi.id=pl.package_instance_id WHERE pi.legacy_source_system='SC7_RUNTIME' AND pl.entry_type='LEGACY_IMPORT';`).at(-1) === '3', 'Historical card LEGACY_IMPORT ledgers are incomplete.')
  expect(sql(`SELECT COUNT(*) FROM stored_value_ledger svl JOIN stored_value_batch svb ON svb.id=svl.batch_id JOIN package_instance pi ON pi.id=svb.package_instance_id WHERE pi.legacy_original_card_no='STORED-${suffix}' AND svl.entry_type='LEGACY_IMPORT';`).at(-1) === '1', 'Stored-value legacy ledger is missing.')

  const conflict = (await upload(operatorToken, conflictFixture)).data
  expect(conflict.conflictRows >= 1, 'Conflicting member profile was not isolated during preflight.')
  await request(admin, `/api/v3/legacy-import/batches/${conflict.id}/execute`, {
    method: 'POST', token: adminToken,
    headers: { 'Idempotency-Key': `conflict-${suffix}`, 'X-FACE-Admin-Surface': 'DESKTOP' },
    body: { shopId: 1 },
  })
  expect(sql(`SELECT name FROM member m JOIN legacy_member_source_map lsm ON lsm.member_id=m.id WHERE lsm.source_system='SC7_RUNTIME' AND lsm.original_member_no='MEM-${suffix}';`).at(-1) === `SC7Member${suffix}`, 'Conflicting import overwrote existing member data.')

  const workbench = (await request(admin, '/api/v3/operations/workbench?shopId=1', {
    token: operatorToken, headers: { 'X-FACE-Admin-Surface': 'MOBILE' },
  })).data
  expect(workbench.summary && Array.isArray(workbench.todayAppointments) && Array.isArray(workbench.afterSales), 'Mobile operations workbench did not return basic statistics and task lists.')
  const logs = compose(['logs', '--no-color', 'backend'])
  expect(!logs.includes(phone), 'Backend logs exposed the imported full phone number.')
  console.log(`SC7_RUNTIME=PASS;BATCH=${preflight.batchNo};MEMBERS=1;CARDS=3;CONFLICTS_ISOLATED=${conflict.conflictRows}`)
} finally {
  for (const file of [fixture, conflictFixture]) {
    try { fs.rmSync(file, { force: true }) } catch { /* best-effort temporary fixture cleanup */ }
  }
}
