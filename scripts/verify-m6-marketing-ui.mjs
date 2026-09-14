import fs from 'node:fs'
import path from 'node:path'
import { spawn, spawnSync } from 'node:child_process'
import WebSocket from '../front/node_modules/ws/index.js'

const args = Object.fromEntries(process.argv.slice(2).map((entry) => {
  const [key, ...value] = entry.replace(/^--/, '').split('=')
  return [key, value.join('=')]
}))
const backend = args.backend ?? 'http://127.0.0.1:8193/face-next'
const stateFile = args['state-file']
const frontPort = Number(args['front-port'] ?? 8085)
const adminPort = Number(args['admin-port'] ?? 8084)
const debugPort = Number(args['debug-port'] ?? 9357)
const repoRoot = 'E:/face'
const previewRoot = 'E:/FACE/.artifacts/预览'
const profileRoot = path.join(repoRoot, 'tmp', `m6-marketing-ui-${Date.now()}`)
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const screenshots = {
  admin: path.join(previewRoot, 'M6-营销治理-管理端.png'),
  member: path.join(previewRoot, 'M6-营销偏好-会员端.png'),
}
if (!stateFile || !fs.existsSync(stateFile)) {
  throw new Error('M6 marketing UI verifier requires a valid temporary state file.')
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
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(`Authentication ${pathname} failed: ${response.status}`)
  return payload.data
}

async function sessions() {
  if (!state.memberCredentials) throw new Error('M6 UI state is missing member credentials.')
  const [adminLegacy, adminV3, memberLegacy, memberV3] = await Promise.all([
    postJson('/api/v2/auth/login', { username: 'admin', password: 'Face@123' }),
    postJson('/api/v3/auth/login', { username: 'admin', password: 'Face@123' }),
    postJson('/api/v2/client/auth/login', state.memberCredentials),
    postJson('/api/v3/auth/login', state.memberCredentials),
  ])
  return {
    admin: {
      legacyToken: adminLegacy.token,
      accessToken: adminV3.access_token,
      refreshToken: adminV3.refresh_token,
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
    try { if ((await fetch(url)).ok) return } catch { /* still starting */ }
    await sleep(250)
  }
  throw new Error(`Timed out waiting for ${url}`)
}

function startVite(root, port, environment) {
  const child = spawn(process.env.ComSpec || 'cmd.exe', [
    '/d', '/s', '/c', `npm.cmd run dev -- --host 127.0.0.1 --port ${port} --strictPort`,
  ], {
    cwd: root, windowsHide: true, stdio: 'ignore', env: { ...process.env, ...environment },
  })
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
    expression, awaitPromise: true, returnByValue: true,
  })
  if (message.result.exceptionDetails) throw new Error(message.result.exceptionDetails.text)
  return message.result.result.value
}

async function waitForPage(predicate, label, attempts = 100) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    if (await evaluate(predicate)) return
    await sleep(250)
  }
  const diagnostic = await evaluate(`({ href: location.href, text: document.body?.innerText?.slice(0, 1600) })`)
  throw new Error(`Timed out waiting for ${label}: ${JSON.stringify(diagnostic)}`)
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

async function setViewport(width, height, mobile = false) {
  await send('Emulation.setDeviceMetricsOverride', {
    width, height, deviceScaleFactor: 1, mobile, screenWidth: width, screenHeight: height,
  })
}

async function capture(filePath) {
  const message = await send('Page.captureScreenshot', {
    format: 'png', captureBeyondViewport: false, fromSurface: true,
  })
  fs.writeFileSync(filePath, Buffer.from(message.result.data, 'base64'))
}

try {
  fs.mkdirSync(previewRoot, { recursive: true })
  fs.mkdirSync(profileRoot, { recursive: true })
  const auth = await sessions()
  startVite(path.join(repoRoot, 'admin-next'), adminPort, { VITE_CHAIN_API: backend })
  startVite(path.join(repoRoot, 'front-next'), frontPort, {
    VITE_CLIENT_API_BASE: `${backend}/api/v2/client`,
    VITE_V3_API_BASE: `${backend}/api/v3`,
  })
  await Promise.all([
    waitForUrl(`http://127.0.0.1:${adminPort}`),
    waitForUrl(`http://127.0.0.1:${frontPort}`),
  ])
  const edge = spawn(edgePath, [
    `--user-data-dir=${profileRoot}`, '--headless=new',
    `--remote-debugging-port=${debugPort}`, '--no-first-run',
    '--disable-features=Translate', 'about:blank',
  ], { windowsHide: true, stdio: 'ignore' })
  processes.push(edge)
  await waitForUrl(`http://127.0.0.1:${debugPort}/json/version`)
  const pages = await fetch(`http://127.0.0.1:${debugPort}/json`).then((response) => response.json())
  const page = pages.find((candidate) => candidate.type === 'page'
    && !candidate.url.startsWith('chrome-extension://'))
  if (!page) throw new Error('No controllable M6 UI page was found.')
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
    if (message.method === 'Log.entryAdded' && message.params?.entry?.level === 'error') {
      browserErrors.push(message.params.entry.text)
    }
    if (message.method === 'Network.responseReceived' && message.params?.response?.status >= 400) {
      networkFailures.push({ status: message.params.response.status, url: message.params.response.url })
    }
  })
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Log.enable')
  await send('Network.enable')

  await setViewport(1440, 960)
  await setStorage(`http://127.0.0.1:${adminPort}/login`, {
    'face-chain-token': auth.admin.legacyToken,
    'face-chain-v3-access-token': auth.admin.accessToken,
    'face-chain-v3-refresh-token': auth.admin.refreshToken,
  })
  await send('Page.navigate', { url: `http://127.0.0.1:${adminPort}/marketing` })
  await waitForPage(
    `document.body.innerText.includes('营销治理')
      && document.body.innerText.includes('可执行通道：站内通知')
      && document.querySelectorAll('.el-table__body tr').length >= 3`,
    'M6 admin marketing governance',
  )
  const admin = await evaluate(`(() => ({
    heading: document.body.innerText.includes('营销治理'),
    dutySeparation: document.body.innerText.includes('异岗审批'),
    frozenAudience: document.body.innerText.includes('执行时冻结受众'),
    externalTruth: document.body.innerText.includes('短信/邮件/微信未配置'),
    campaignRows: document.querySelectorAll('.el-table__body tr').length,
    lightTheme: getComputedStyle(document.documentElement).colorScheme === 'light',
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
  }))()`)
  await capture(screenshots.admin)

  await setViewport(390, 844, true)
  await setStorage(`http://127.0.0.1:${frontPort}/login`, {
    'face-client-session-v2': auth.member,
  })
  await send('Page.navigate', { url: `http://127.0.0.1:${frontPort}/profile` })
  await waitForPage(
    `document.body.innerText.includes('营销信息偏好')
      && document.querySelectorAll('.consent-list article').length === 4`,
    'M6 member marketing preferences',
  )
  const member = await evaluate(`(() => ({
    heading: document.body.innerText.includes('营销信息偏好'),
    defaultCopy: document.body.innerText.includes('默认关闭'),
    revokeCopy: document.body.innerText.includes('撤回后不会进入之后新执行的活动'),
    consentRows: document.querySelectorAll('.consent-list article').length,
    unavailableRows: [...document.querySelectorAll('.consent-list article')]
      .filter((item) => item.textContent.includes('服务商尚未配置')).length,
    switchAccessible: Boolean(document.querySelector('[role="switch"][aria-checked]')),
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
  }))()`)
  await capture(screenshots.member)

  const results = { admin, member, screenshots, browserErrors, networkFailures }
  const checks = [
    admin.heading, admin.dutySeparation, admin.frozenAudience, admin.externalTruth,
    admin.campaignRows >= 3, admin.lightTheme, admin.noOverflow,
    member.heading, member.defaultCopy, member.revokeCopy,
    member.consentRows === 4, member.unavailableRows === 3,
    member.switchAccessible, member.noOverflow,
    browserErrors.length === 0, networkFailures.length === 0,
  ]
  if (checks.some((check) => !check)) {
    throw new Error(`M6 marketing UI verification failed: ${JSON.stringify(results)}`)
  }
  console.log(JSON.stringify(results, null, 2))
  console.log('M6_MARKETING_UI=PASS')
} finally {
  try { ws?.close() } catch { /* best effort */ }
  for (const child of processes.reverse()) {
    try {
      if (child?.pid) spawnSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], {
        windowsHide: true, stdio: 'ignore',
      })
    } catch { /* best effort */ }
  }
  await sleep(500)
  const resolved = path.resolve(profileRoot)
  const allowed = path.resolve(repoRoot, 'tmp') + path.sep
  if (resolved.startsWith(allowed)) fs.rmSync(profileRoot, { recursive: true, force: true })
}
