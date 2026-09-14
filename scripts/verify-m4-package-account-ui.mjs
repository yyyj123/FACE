import fs from 'node:fs'
import path from 'node:path'
import { spawn, spawnSync } from 'node:child_process'
import WebSocket from '../front/node_modules/ws/index.js'

const args = Object.fromEntries(
  process.argv.slice(2).map((entry) => {
    const [key, ...value] = entry.replace(/^--/, '').split('=')
    return [key, value.join('=')]
  }),
)

const backend = args.backend ?? 'http://127.0.0.1:8193/face-next'
const stateFile = args['state-file']
const frontPort = Number(args['front-port'] ?? 8082)
const adminPort = Number(args['admin-port'] ?? 8081)
const debugPort = Number(args['debug-port'] ?? 9344)
const refundMode = args['refund-mode'] === 'true'
const repoRoot = 'E:/face'
const previewRoot = 'E:/FACE/.artifacts/预览'
const profileRoot = path.join(repoRoot, 'tmp', `m4-ui-profile-${Date.now()}`)
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const screenshots = {
  technician: path.join(previewRoot, 'M4-技师端-套餐核销.png'),
  member: path.join(previewRoot, 'M4-会员端-我的资产.png'),
  memberRefund: path.join(previewRoot, 'M4-会员端-退款进度.png'),
  admin: path.join(previewRoot, 'M4-管理端-套餐账户.png'),
  adminRefund: path.join(previewRoot, 'M4-管理端-退款闭环.png'),
}

if (!stateFile || !fs.existsSync(stateFile)) {
  throw new Error('M4 UI verifier requires a valid temporary state file.')
}

const state = JSON.parse(fs.readFileSync(stateFile, 'utf8'))
const processes = []
const pending = new Map()
const browserErrors = []
const networkFailures = []
let ws
let commandId = 0

const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))

async function waitForUrl(url, attempts = 120) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const response = await fetch(url)
      if (response.ok) return
    } catch {
      // Local service is still starting.
    }
    await sleep(250)
  }
  throw new Error(`Timed out waiting for ${url}`)
}

function startVite(root, port, environment) {
  const processEnvironment = { ...process.env, ...environment }
  const build = spawnSync(
    process.env.ComSpec || 'cmd.exe',
    ['/d', '/s', '/c', 'npm.cmd run build'],
    {
      cwd: root,
      windowsHide: true,
      stdio: 'pipe',
      encoding: 'utf8',
      env: processEnvironment,
    },
  )
  if (build.status !== 0) {
    throw new Error(
      `Frontend build failed in ${root}: ${build.stderr || build.stdout}`,
    )
  }
  const command = `npm.cmd run preview -- --host 127.0.0.1 --port ${Number(port)} --strictPort`
  const child = spawn(
    process.env.ComSpec || 'cmd.exe',
    ['/d', '/s', '/c', command],
    {
      cwd: root,
      windowsHide: true,
      stdio: 'ignore',
      env: processEnvironment,
    },
  )
  processes.push(child)
}

function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++commandId
    pending.set(id, { resolve, reject })
    ws.send(JSON.stringify({ id, method, params }))
  })
}

async function evaluate(expression) {
  const message = await send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  })
  if (message.result.exceptionDetails) {
    throw new Error(message.result.exceptionDetails.text)
  }
  return message.result.result.value
}

async function waitForPage(predicate, label, attempts = 120) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    if (await evaluate(predicate)) return
    await sleep(250)
  }
  const diagnostic = await evaluate(`({
    href: location.href,
    text: document.body?.innerText?.slice(0, 1800),
  })`)
  throw new Error(
    `Timed out waiting for ${label}: ${JSON.stringify(diagnostic)}; network=${JSON.stringify(networkFailures)}`,
  )
}

async function capture(filePath) {
  const message = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: false,
    fromSurface: true,
  })
  fs.writeFileSync(filePath, Buffer.from(message.result.data, 'base64'))
}

async function setViewport(width, height, mobile = false) {
  await send('Emulation.setDeviceMetricsOverride', {
    width,
    height,
    deviceScaleFactor: 1,
    mobile,
    screenWidth: width,
    screenHeight: height,
  })
}

async function setStorage(url, entries) {
  const origin = new URL(url).origin
  await send('Page.navigate', { url })
  await waitForPage(
    `location.origin === ${JSON.stringify(origin)} && document.readyState !== 'loading'`,
    `storage origin ${origin}`,
  )
  const stored = await evaluate(`(() => {
    const entries = ${JSON.stringify(entries)};
    for (const [key, value] of Object.entries(entries)) {
      localStorage.setItem(key, typeof value === 'string' ? value : JSON.stringify(value));
    }
    return Object.keys(entries).every((key) => localStorage.getItem(key) !== null);
  })()`)
  if (!stored) throw new Error(`Could not persist browser session for ${origin}.`)
}

async function loginThroughUi(url, credentials, clearKeys, label) {
  const origin = new URL(url).origin
  await send('Page.navigate', { url })
  await waitForPage(
    `location.origin === ${JSON.stringify(origin)} && document.readyState !== 'loading'`,
    `${label} origin`,
  )
  await evaluate(`(() => {
    for (const key of ${JSON.stringify(clearKeys)}) localStorage.removeItem(key);
    return true;
  })()`)
  await send('Page.navigate', { url })
  await waitForPage(
    `document.querySelectorAll('input').length >= 2
      && Boolean(document.querySelector('form button[type="submit"]'))`,
    `${label} login form`,
  )
  await evaluate(`(() => {
    const inputs = [...document.querySelectorAll('input')];
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(inputs[0], ${JSON.stringify(credentials.username)});
    inputs[0].dispatchEvent(new Event('input', { bubbles: true }));
    setter.call(inputs[1], ${JSON.stringify(credentials.password)});
    inputs[1].dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  })()`)
  await sleep(50)
  await evaluate(`(() => {
    document.querySelector('form').requestSubmit();
    return true;
  })()`)
  await waitForPage(
    `location.pathname !== '/login'`,
    `${label} authenticated route`,
  )
}

try {
  fs.mkdirSync(previewRoot, { recursive: true })
  fs.mkdirSync(profileRoot, { recursive: true })

  startVite(path.join(repoRoot, 'front-next'), frontPort, {
    VITE_CLIENT_API_BASE: `${backend}/api/v2/client`,
    VITE_V3_API_BASE: `${backend}/api/v3`,
  })
  startVite(path.join(repoRoot, 'admin-next'), adminPort, {
    VITE_CHAIN_API: backend,
  })
  await Promise.all([
    waitForUrl(`http://127.0.0.1:${frontPort}`),
    waitForUrl(`http://127.0.0.1:${adminPort}`),
  ])

  const edge = spawn(
    edgePath,
    [
      `--user-data-dir=${profileRoot}`,
      '--headless=new',
      `--remote-debugging-port=${debugPort}`,
      '--no-first-run',
      '--disable-features=Translate',
      'about:blank',
    ],
    { windowsHide: true, stdio: 'ignore' },
  )
  processes.push(edge)
  await waitForUrl(`http://127.0.0.1:${debugPort}/json/version`)
  const pages = await fetch(`http://127.0.0.1:${debugPort}/json`).then((response) => response.json())
  const page = pages.find(
    (candidate) => candidate.type === 'page' && !candidate.url.startsWith('chrome-extension://'),
  )
  if (!page) throw new Error('No controllable application page was found.')
  ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((resolve) => ws.once('open', resolve))
  ws.on('message', (payload) => {
    const message = JSON.parse(payload)
    if (message.id && pending.has(message.id)) {
      pending.get(message.id).resolve(message)
      pending.delete(message.id)
      return
    }
    if (message.method === 'Runtime.exceptionThrown') {
      browserErrors.push(message.params?.exceptionDetails?.text ?? 'Runtime exception')
    }
    if (
      message.method === 'Log.entryAdded'
      && ['error', 'warning'].includes(message.params?.entry?.level)
    ) {
      browserErrors.push(message.params.entry.text)
    }
    if (
      message.method === 'Network.responseReceived'
      && message.params?.response?.status >= 400
    ) {
      networkFailures.push({
        status: message.params.response.status,
        url: message.params.response.url,
      })
    }
  })
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Log.enable')
  await send('Network.enable')

  await setViewport(1440, 960)
  await loginThroughUi(
    `http://127.0.0.1:${frontPort}/login`,
    state.technicianCredentials,
    ['face-client-session-v2'],
    'technician',
  )
  await send('Page.navigate', { url: `http://127.0.0.1:${frontPort}/appointments` })
  await waitForPage(
    `document.body.innerText.includes('技师工作台')
      && document.querySelectorAll('.appointment-card').length > 0`,
    'technician workbench',
  )
  await evaluate(`(() => {
    [...document.querySelectorAll('.status-tabs button')]
      .find((button) => button.textContent.trim() === '护理中')?.click();
    return true;
  })()`)
  await sleep(300)
  await evaluate(`(() => {
    [...document.querySelectorAll('button')]
      .find((button) => button.textContent.trim() === '填写护理记录')?.click();
    return true;
  })()`)
  await waitForPage(
    `document.body.innerText.includes('套餐核销')
      && (
        ${refundMode}
          ? document.body.innerText.includes('当前会员没有可用于本次护理的套餐项目')
          : Boolean(document.querySelector('.package-writeoff-form'))
      )`,
    'technician package write-off workspace',
  )
  const technician = await evaluate(`(() => ({
    heading: document.body.innerText.includes('技师工作台'),
    packageWriteOff: document.body.innerText.includes('套餐核销'),
    orderGuardCopy: document.body.innerText.includes('不可覆盖流水'),
    lifecycleGuard: ${refundMode}
      ? document.body.innerText.includes('当前会员没有可用于本次护理的套餐项目')
      : Boolean(document.querySelector('.package-writeoff-form')),
    lightTheme: getComputedStyle(document.body).backgroundColor !== 'rgb(10, 8, 12)',
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    alertVisible: Boolean(document.querySelector('[role="alert"]')),
  }))()`)
  await capture(screenshots.technician)

  await setViewport(390, 844, true)
  await loginThroughUi(
    `http://127.0.0.1:${frontPort}/login`,
    state.memberCredentials,
    ['face-client-session-v2'],
    'member',
  )
  await send('Page.navigate', { url: `http://127.0.0.1:${frontPort}/benefits` })
  await waitForPage(
    `document.body.innerText.includes('我的套餐与账户')
      && document.querySelectorAll('.asset-row').length > 0
      && document.querySelectorAll('.account-row').length >= 3`,
    'member benefits',
  )
  const member = await evaluate(`(() => ({
    heading: document.body.innerText.includes('我的套餐与账户'),
    packages: document.querySelectorAll('.asset-row').length,
    accounts: document.querySelectorAll('.account-row').length,
    validity: document.body.innerText.includes('有效至'),
    mobileAssetNav: [...document.querySelectorAll('.mobile-nav a')]
      .some((link) => link.textContent.includes('资产')),
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    alertVisible: Boolean(document.querySelector('[role="alert"]')),
  }))()`)
  await capture(screenshots.member)

  await send('Page.navigate', { url: `http://127.0.0.1:${frontPort}/appointments` })
  await waitForPage(
    `document.body.innerText.includes('近期退款进度')
      && document.querySelectorAll('.refund-progress-row').length >= 2`,
    'member refund progress',
  )
  const memberRefund = await evaluate(`(() => ({
    heading: document.body.innerText.includes('近期退款进度'),
    rows: document.querySelectorAll('.refund-progress-row').length,
    threeSteps: document.body.innerText.includes('已申请')
      && document.body.innerText.includes('已审核')
      && document.body.innerText.includes('已退款'),
    completionCopy: document.body.innerText.includes('款项已退回会员余额')
      || document.body.innerText.includes('门店账务已确认退款完成'),
    noManagementAction: !document.body.innerText.includes('执行退款'),
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    alertVisible: Boolean(document.querySelector('[role="alert"]')),
  }))()`)
  await capture(screenshots.memberRefund)

  await setViewport(1440, 960)
  await loginThroughUi(
    `http://127.0.0.1:${adminPort}/login`,
    { username: 'admin', password: 'Face@123' },
    ['face-chain-token', 'face-chain-v3-access-token', 'face-chain-v3-refresh-token'],
    'admin',
  )
  await send('Page.navigate', { url: `http://127.0.0.1:${adminPort}/assets` })
  await waitForPage(
    `document.body.innerText.includes('套餐账户')
      && document.body.innerText.includes('套餐产品')
      && document.querySelectorAll('.el-table__body tbody tr').length > 0`,
    'admin package account',
  )
  const admin = await evaluate(`(() => ({
    heading: document.body.innerText.includes('套餐账户'),
    products: document.querySelectorAll('.el-table__body tbody tr').length,
    immutableCopy: document.body.innerText.includes('不可变流水'),
    issueGuardCopy: document.body.innerText.includes('已支付订单'),
    navVisible: [...document.querySelectorAll('.sidebar-nav a')]
      .some((link) => link.textContent.includes('套餐与账户')),
    lightTheme: getComputedStyle(document.documentElement).colorScheme === 'light',
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    errorVisible: Boolean(document.querySelector('.el-alert--error')),
  }))()`)
  await capture(screenshots.admin)

  await send('Page.navigate', { url: `http://127.0.0.1:${adminPort}/transactions` })
  await waitForPage(
    `document.body.innerText.includes('交易与结算')
      && document.querySelectorAll('.transaction-table tbody tr').length > 0
      && document.body.innerText.includes('外部通道待配置')`,
    'admin refund lifecycle',
  )
  const adminRefund = await evaluate(`(() => ({
    heading: document.body.innerText.includes('交易与结算'),
    rows: document.querySelectorAll('.transaction-table tbody tr').length,
    completed: document.body.innerText.includes('已退款')
      && Boolean(document.querySelector('.refund-amount')),
    externalGuard: document.body.innerText.includes('外部通道待配置'),
    separationCopy: document.body.innerText.includes('退款待执行'),
    navVisible: [...document.querySelectorAll('.sidebar-nav a')]
      .some((link) => link.textContent.includes('交易')),
    lightTheme: getComputedStyle(document.documentElement).colorScheme === 'light',
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    errorVisible: Boolean(document.querySelector('.el-alert--error')),
  }))()`)
  await capture(screenshots.adminRefund)

  const results = {
    technician,
    member,
    memberRefund,
    admin,
    adminRefund,
    screenshots,
    browserErrors,
  }
  const checks = [
    technician.heading,
    technician.packageWriteOff,
    technician.orderGuardCopy,
    technician.lifecycleGuard,
    technician.lightTheme,
    technician.noOverflow,
    !technician.alertVisible,
    member.heading,
    member.packages > 0,
    member.accounts >= 3,
    member.validity,
    member.mobileAssetNav,
    member.noOverflow,
    !member.alertVisible,
    memberRefund.heading,
    memberRefund.rows >= 2,
    memberRefund.threeSteps,
    memberRefund.completionCopy,
    memberRefund.noManagementAction,
    memberRefund.noOverflow,
    !memberRefund.alertVisible,
    admin.heading,
    admin.products > 0,
    admin.immutableCopy,
    admin.navVisible,
    admin.lightTheme,
    admin.noOverflow,
    !admin.errorVisible,
    adminRefund.heading,
    adminRefund.rows > 0,
    adminRefund.completed,
    adminRefund.externalGuard,
    adminRefund.separationCopy,
    adminRefund.navVisible,
    adminRefund.lightTheme,
    adminRefund.noOverflow,
    !adminRefund.errorVisible,
    browserErrors.length === 0,
  ]
  if (checks.some((check) => !check)) {
    throw new Error(`M4 UI verification failed: ${JSON.stringify(results)}`)
  }
  console.log(JSON.stringify(results, null, 2))
  console.log('M4_THREE_CLIENT_UI=PASS')
} finally {
  try {
    ws?.close()
  } catch {
    // Best-effort browser cleanup.
  }
  for (const child of processes.reverse()) {
    try {
      if (child?.pid) {
        spawnSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], {
          windowsHide: true,
          stdio: 'ignore',
        })
      }
    } catch {
      // Best-effort process cleanup.
    }
  }
  await sleep(500)
  const resolvedProfile = path.resolve(profileRoot)
  const allowedProfileRoot = path.resolve(repoRoot, 'tmp') + path.sep
  if (resolvedProfile.startsWith(allowedProfileRoot)) {
    fs.rmSync(resolvedProfile, { recursive: true, force: true })
  }
}
