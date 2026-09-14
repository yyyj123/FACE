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
const frontPort = Number(args['front-port'] ?? 8085)
const adminPort = Number(args['admin-port'] ?? 8084)
const debugPort = Number(args['debug-port'] ?? 9356)
const repoRoot = 'E:/face'
const previewRoot = 'E:/FACE/.artifacts/预览'
const profileRoot = path.join(repoRoot, 'tmp', `m5-ui-profile-${Date.now()}`)
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const screenshots = {
  admin: path.join(previewRoot, 'M5-经营协同与通知-管理端.png'),
  technician: path.join(previewRoot, 'M5-技师提成协同工作台.png'),
  member: path.join(previewRoot, 'M5-会员售后服务-移动端.png'),
  notifications: path.join(previewRoot, 'M5-会员消息中心-移动端.png'),
}

if (!stateFile || !fs.existsSync(stateFile)) {
  throw new Error('M5 UI verifier requires a valid temporary state file.')
}

const state = JSON.parse(fs.readFileSync(stateFile, 'utf8'))
const processes = []
const pending = new Map()
const browserErrors = []
const networkFailures = []
let ws
let commandId = 0

const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))

async function postJson(pathname, body) {
  const response = await fetch(`${backend}${pathname}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(
      `Authentication request ${pathname} returned ${response.status}: ${JSON.stringify(payload)}`,
    )
  }
  return payload.data
}

async function createCurrentSessions() {
  const adminCredentials = { username: 'admin', password: 'Face@123' }
  const technicianCredentials = state.technicianCredentials
  const memberCredentials = state.memberCredentials
  if (!technicianCredentials || !memberCredentials) {
    throw new Error('M5 UI state is missing technician or member test credentials.')
  }

  const [
    adminLegacy,
    adminV3,
    technicianLegacy,
    technicianV3,
    memberLegacy,
    memberV3,
  ] = await Promise.all([
    postJson('/api/v2/auth/login', adminCredentials),
    postJson('/api/v3/auth/login', adminCredentials),
    postJson('/api/v2/client/auth/login', technicianCredentials),
    postJson('/api/v3/auth/login', technicianCredentials),
    postJson('/api/v2/client/auth/login', memberCredentials),
    postJson('/api/v3/auth/login', memberCredentials),
  ])

  return {
    admin: {
      legacyToken: adminLegacy.token,
      accessToken: adminV3.access_token,
      refreshToken: adminV3.refresh_token,
    },
    technician: {
      ...technicianLegacy,
      v3AccessToken: technicianV3.access_token,
      v3RefreshToken: technicianV3.refresh_token,
    },
    member: {
      ...memberLegacy,
      v3AccessToken: memberV3.access_token,
      v3RefreshToken: memberV3.refresh_token,
    },
  }
}

async function waitForUrl(url, attempts = 120) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const response = await fetch(url)
      if (response.ok) return
    } catch {
      // The local service is still starting.
    }
    await sleep(250)
  }
  throw new Error(`Timed out waiting for ${url}`)
}

function startVite(root, port, environment) {
  const command = `npm.cmd run dev -- --host 127.0.0.1 --port ${Number(port)} --strictPort`
  const child = spawn(
    process.env.ComSpec || 'cmd.exe',
    ['/d', '/s', '/c', command],
    {
      cwd: root,
      windowsHide: true,
      stdio: 'ignore',
      env: { ...process.env, ...environment },
    },
  )
  processes.push(child)
  return child
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

async function waitForPage(predicate, label, attempts = 100) {
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
  await send('Page.navigate', { url })
  await sleep(350)
  await evaluate(`(() => {
    const entries = ${JSON.stringify(entries)};
    localStorage.clear();
    for (const [key, value] of Object.entries(entries)) {
      localStorage.setItem(key, typeof value === 'string' ? value : JSON.stringify(value));
    }
    return true;
  })()`)
}

try {
  fs.mkdirSync(previewRoot, { recursive: true })
  fs.mkdirSync(profileRoot, { recursive: true })
  const sessions = await createCurrentSessions()

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
  await setStorage(`http://127.0.0.1:${adminPort}/login`, {
    'face-chain-token': sessions.admin.legacyToken,
    'face-chain-v3-access-token': sessions.admin.accessToken,
    'face-chain-v3-refresh-token': sessions.admin.refreshToken,
  })
  await send('Page.navigate', { url: `http://127.0.0.1:${adminPort}/operations` })
  await waitForPage(
    `document.body.innerText.includes('提成、售后与审批')
      && document.querySelectorAll('[role="tab"]').length >= 6
      && !document.body.innerText.includes('正在加载经营协同数据')`,
    'admin operations workspace',
  )
  await evaluate(`(() => {
    [...document.querySelectorAll('[role="tab"]')]
      .find((item) => item.textContent.includes('我的通知'))?.click();
    return true;
  })()`)
  await waitForPage(
    `document.querySelectorAll('.notification-row').length > 0
      && document.body.innerText.includes('外部通道不可用')`,
    'admin notification tab',
  )
  const admin = await evaluate(`(() => ({
    heading: document.body.innerText.includes('提成、售后与审批'),
    tabs: document.querySelectorAll('[role="tab"]').length,
    notifications: document.querySelectorAll('.notification-row').length,
    safeSummary: document.body.innerText.includes('站内已送达'),
    externalTruth: document.body.innerText.includes('外部通道不可用'),
    lightTheme: getComputedStyle(document.documentElement).colorScheme === 'light',
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    errorVisible: Boolean(document.querySelector('.el-alert--error')),
  }))()`)
  await capture(screenshots.admin)

  await setStorage(`http://127.0.0.1:${frontPort}/login`, {
    'face-client-session-v2': sessions.technician,
  })
  await send('Page.navigate', { url: `http://127.0.0.1:${frontPort}/workbench` })
  await waitForPage(
    `document.body.innerText.includes('我的提成与协同事项')
      && Boolean(document.querySelector('.summary-strip'))
      && !document.body.innerText.includes('正在加载技师工作台')`,
    'technician commission workbench',
  )
  const technician = await evaluate(`(() => ({
    heading: document.body.innerText.includes('我的提成与协同事项'),
    summaryCells: document.querySelectorAll('.summary-strip > div').length,
    ownScopeCopy: document.body.innerText.includes('这里只展示本人提成流水'),
    immutableCopy: document.body.innerText.includes('不会修改原始入账'),
    technicianNav: document.body.innerText.includes('技师工作台'),
    memberAfterSaleNavHidden: ![...document.querySelectorAll('.desktop-nav a')]
      .some((item) => item.textContent.trim() === '售后服务'),
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    alertVisible: Boolean(document.querySelector('[role="alert"]')),
  }))()`)
  await capture(screenshots.technician)

  await setViewport(390, 844, true)
  await setStorage(`http://127.0.0.1:${frontPort}/login`, {
    'face-client-session-v2': sessions.member,
  })
  await send('Page.navigate', { url: `http://127.0.0.1:${frontPort}/after-sales` })
  await waitForPage(
    `document.body.innerText.includes('问题反馈与处理进度')
      && Boolean(document.querySelector('.submission-panel form'))
      && !document.body.innerText.includes('正在加载售后记录')`,
    'member after-sale workspace',
  )
  const member = await evaluate(`(() => ({
    heading: document.body.innerText.includes('问题反馈与处理进度'),
    form: Boolean(document.querySelector('.submission-panel form')),
    privacyCopy: document.body.innerText.includes('请不要填写身份证、健康病史或支付凭据'),
    historyCopy: document.body.innerText.includes('不会覆盖历史处理轨迹'),
    mobileAfterSaleNav: [...document.querySelectorAll('.mobile-nav a')]
      .some((item) => item.textContent.includes('售后')),
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    alertVisible: Boolean(document.querySelector('[role="alert"]')),
  }))()`)
  await capture(screenshots.member)

  await send('Page.navigate', { url: `http://127.0.0.1:${frontPort}/notifications` })
  await waitForPage(
    `document.body.innerText.includes('消息中心')
      && Boolean(document.querySelector('.filter-bar'))
      && !document.body.innerText.includes('正在加载通知')`,
    'member notification center',
  )
  const notifications = await evaluate(`(() => ({
    heading: document.body.innerText.includes('消息中心'),
    filters: document.querySelectorAll('.filter-bar button').length,
    safeCopy: document.body.innerText.includes('通知内容只显示安全摘要'),
    renderedState: Boolean(document.querySelector('.message-list, .empty-state')),
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    alertVisible: Boolean(document.querySelector('[role="alert"]')),
  }))()`)
  await capture(screenshots.notifications)

  const results = {
    admin,
    technician,
    member,
    notifications,
    screenshots,
    browserErrors,
    networkFailures,
  }
  const checks = [
    admin.heading,
    admin.tabs >= 6,
    admin.notifications > 0,
    admin.safeSummary,
    admin.externalTruth,
    admin.lightTheme,
    admin.noOverflow,
    !admin.errorVisible,
    technician.heading,
    technician.summaryCells === 4,
    technician.ownScopeCopy,
    technician.immutableCopy,
    technician.technicianNav,
    technician.memberAfterSaleNavHidden,
    technician.noOverflow,
    !technician.alertVisible,
    member.heading,
    member.form,
    member.privacyCopy,
    member.historyCopy,
    member.mobileAfterSaleNav,
    member.noOverflow,
    !member.alertVisible,
    notifications.heading,
    notifications.filters === 3,
    notifications.safeCopy,
    notifications.renderedState,
    notifications.noOverflow,
    !notifications.alertVisible,
    browserErrors.length === 0,
    networkFailures.length === 0,
  ]
  if (checks.some((check) => !check)) {
    throw new Error(`M5 UI verification failed: ${JSON.stringify(results)}`)
  }
  console.log(JSON.stringify(results, null, 2))
  console.log('M5_NOTIFICATION_THREE_CLIENT_UI=PASS')
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
    fs.rmSync(profileRoot, { recursive: true, force: true })
  }
}
