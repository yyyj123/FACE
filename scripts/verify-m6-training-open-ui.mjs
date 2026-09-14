import fs from 'node:fs'
import path from 'node:path'
import { spawn, spawnSync } from 'node:child_process'
import WebSocket from '../front/node_modules/ws/index.js'

const args = Object.fromEntries(process.argv.slice(2).map((entry) => {
  const [key, ...value] = entry.replace(/^--/, '').split('=')
  return [key, value.join('=')]
}))
const backend = args.backend ?? 'http://127.0.0.1:8193/face-next'
const frontPort = Number(args['front-port'] ?? 8085)
const adminPort = Number(args['admin-port'] ?? 8084)
const debugPort = Number(args['debug-port'] ?? 9357)
const repoRoot = 'E:/face'
const previewRoot = 'E:/FACE/.artifacts/预览'
const profileRoot = path.join(repoRoot, 'tmp', `m6-training-open-ui-${Date.now()}`)
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const screenshots = {
  adminTraining: path.join(previewRoot, 'M6-员工培训-管理端.png'),
  adminOpen: path.join(previewRoot, 'M6-培训开放平台-管理端.png'),
  technician: path.join(previewRoot, 'M6-培训资格-技师端.png'),
}
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
  const [adminLegacy, adminV3, technicianLegacy, technicianV3] = await Promise.all([
    postJson('/api/v2/auth/login', { username: 'admin', password: 'Face@123' }),
    postJson('/api/v3/auth/login', { username: 'admin', password: 'Face@123' }),
    postJson('/api/v2/client/auth/login', { username: 'jishi01', password: 'Face@123' }),
    postJson('/api/v3/auth/login', { username: 'jishi01', password: 'Face@123' }),
  ])
  return {
    admin: { legacyToken: adminLegacy.token, accessToken: adminV3.access_token, refreshToken: adminV3.refresh_token },
    technician: { ...technicianLegacy, v3AccessToken: technicianV3.access_token, v3RefreshToken: technicianV3.refresh_token },
  }
}

async function waitForUrl(url, attempts = 120) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try { if ((await fetch(url)).ok) return } catch { /* starting */ }
    await sleep(250)
  }
  throw new Error(`Timed out waiting for ${url}`)
}

function startVite(root, port, environment) {
  const child = spawn(process.env.ComSpec || 'cmd.exe', [
    '/d', '/s', '/c', `npm.cmd run dev -- --host 127.0.0.1 --port ${port} --strictPort`,
  ], { cwd: root, windowsHide: true, stdio: 'ignore', env: { ...process.env, ...environment } })
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
  const message = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (message.result.exceptionDetails) throw new Error(message.result.exceptionDetails.text)
  return message.result.result.value
}

async function waitForPage(predicate, label, attempts = 100) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    if (await evaluate(predicate)) return
    await sleep(250)
  }
  const diagnostic = await evaluate(`({ href: location.href, text: document.body?.innerText?.slice(0, 1800) })`)
  throw new Error(`Timed out waiting for ${label}: ${JSON.stringify(diagnostic)}`)
}

async function setStorage(url, entries) {
  await send('Page.navigate', { url })
  await sleep(350)
  await evaluate(`(() => {
    const entries = ${JSON.stringify(entries)};
    localStorage.clear();
    for (const [key, value] of Object.entries(entries)) localStorage.setItem(key, typeof value === 'string' ? value : JSON.stringify(value));
    return true;
  })()`)
}

async function setViewport(width, height, mobile = false) {
  await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile, screenWidth: width, screenHeight: height })
}

async function capture(filePath) {
  const message = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false, fromSurface: true })
  fs.writeFileSync(filePath, Buffer.from(message.result.data, 'base64'))
}

try {
  fs.mkdirSync(previewRoot, { recursive: true })
  fs.mkdirSync(profileRoot, { recursive: true })
  const auth = await sessions()
  startVite(path.join(repoRoot, 'admin-next'), adminPort, { VITE_CHAIN_API: backend })
  startVite(path.join(repoRoot, 'front-next'), frontPort, {
    VITE_CLIENT_API_BASE: `${backend}/api/v2/client`, VITE_V3_API_BASE: `${backend}/api/v3`,
  })
  await Promise.all([waitForUrl(`http://127.0.0.1:${adminPort}`), waitForUrl(`http://127.0.0.1:${frontPort}`)])
  const edge = spawn(edgePath, [
    `--user-data-dir=${profileRoot}`, '--headless=new', `--remote-debugging-port=${debugPort}`,
    '--no-first-run', '--disable-features=Translate', 'about:blank',
  ], { windowsHide: true, stdio: 'ignore' })
  processes.push(edge)
  await waitForUrl(`http://127.0.0.1:${debugPort}/json/version`)
  const pages = await fetch(`http://127.0.0.1:${debugPort}/json`).then((response) => response.json())
  const page = pages.find((candidate) => candidate.type === 'page' && !candidate.url.startsWith('chrome-extension://'))
  if (!page) throw new Error('No controllable M6 training/open UI page was found.')
  ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((resolve) => ws.once('open', resolve))
  ws.on('message', (payload) => {
    const message = JSON.parse(payload)
    if (message.id && pending.has(message.id)) { pending.get(message.id).resolve(message); pending.delete(message.id); return }
    if (message.method === 'Runtime.exceptionThrown') browserErrors.push(message.params?.exceptionDetails?.text ?? 'Runtime exception')
    if (message.method === 'Log.entryAdded' && message.params?.entry?.level === 'error') browserErrors.push(message.params.entry.text)
    if (message.method === 'Network.responseReceived' && message.params?.response?.status >= 400) networkFailures.push({ status: message.params.response.status, url: message.params.response.url })
  })
  await send('Page.enable'); await send('Runtime.enable'); await send('Log.enable'); await send('Network.enable')

  await setViewport(1440, 960)
  await setStorage(`http://127.0.0.1:${adminPort}/login`, {
    'face-chain-token': auth.admin.legacyToken,
    'face-chain-v3-access-token': auth.admin.accessToken,
    'face-chain-v3-refresh-token': auth.admin.refreshToken,
  })
  await send('Page.navigate', { url: `http://127.0.0.1:${adminPort}/training-integrations` })
  await waitForPage(`document.body.innerText.includes('培训与开放平台') && document.body.innerText.includes('课程版本') && document.body.innerText.includes('员工培训记录') && document.body.innerText.includes('基础护理规范')`, 'M6 admin training')
  const training = await evaluate(`(() => ({
    heading: document.body.innerText.includes('培训与开放平台'),
    immutableCopy: document.body.innerText.includes('发布后内容不可覆盖'),
    dutyCopy: document.body.innerText.includes('具备验证权限的其他账号确认结果'),
    coursePresent: document.body.innerText.includes('基础护理规范'),
  }))()`)
  await capture(screenshots.adminTraining)
  await evaluate(`([...document.querySelectorAll('.area-switch button')].find((button) => button.textContent.includes('开放平台'))?.click(), true)`)
  await waitForPage(`document.body.innerText.includes('当前开放边界') && document.body.innerText.includes('catalog:read') && document.body.innerText.includes('数据库仅保存密钥摘要')`, 'M6 admin open platform')
  const admin = await evaluate(`(() => ({
    ...${JSON.stringify(training)},
    boundary: document.body.innerText.includes('会员、护理、健康、支付、库存和员工联系方式均未开放'),
    secretTruth: document.body.innerText.includes('数据库仅保存密钥摘要'),
    revokedClient: document.body.innerText.includes('已撤销'),
    lightTheme: getComputedStyle(document.documentElement).colorScheme === 'light',
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
  }))()`)
  await capture(screenshots.adminOpen)

  await setViewport(390, 844, true)
  await setStorage(`http://127.0.0.1:${frontPort}/login`, { 'face-client-session-v2': auth.technician })
  await send('Page.navigate', { url: `http://127.0.0.1:${frontPort}/workbench` })
  await waitForPage(`document.body.innerText.includes('我的培训与资格') && document.body.innerText.includes('基础护理规范') && document.body.innerText.includes('已通过')`, 'M6 technician training')
  const technician = await evaluate(`(() => ({
    heading: document.body.innerText.includes('我的培训与资格'),
    selfCopy: document.body.innerText.includes('只能操作本人培训'),
    passed: document.body.innerText.includes('已通过'),
    certificate: document.body.innerText.includes('证书 CERT-'),
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
  }))()`)
  await evaluate(`(() => {
    const target = [...document.querySelectorAll('section, h2, h3')]
      .find((element) => element.textContent.includes('我的培训与资格'));
    target?.scrollIntoView({ block: 'start' });
    return Boolean(target);
  })()`)
  await sleep(250)
  await capture(screenshots.technician)

  const results = { admin, technician, screenshots, browserErrors, networkFailures }
  const checks = [
    admin.heading, admin.immutableCopy, admin.dutyCopy, admin.coursePresent, admin.boundary,
    admin.secretTruth, admin.revokedClient, admin.lightTheme, admin.noOverflow,
    technician.heading, technician.selfCopy, technician.passed, technician.certificate, technician.noOverflow,
    browserErrors.length === 0, networkFailures.length === 0,
  ]
  if (checks.some((check) => !check)) throw new Error(`M6 training/open UI verification failed: ${JSON.stringify(results)}`)
  console.log(JSON.stringify(results, null, 2))
  console.log('M6_TRAINING_OPEN_UI=PASS')
} finally {
  try { ws?.close() } catch { /* best effort */ }
  for (const child of processes.reverse()) {
    try { if (child?.pid) spawnSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' }) } catch { /* best effort */ }
  }
  await sleep(500)
  const resolved = path.resolve(profileRoot)
  const allowed = path.resolve(repoRoot, 'tmp') + path.sep
  if (resolved.startsWith(allowed)) fs.rmSync(profileRoot, { recursive: true, force: true })
}
