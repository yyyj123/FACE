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
const adminPort = Number(args['admin-port'] ?? 8083)
const debugPort = Number(args['debug-port'] ?? 9345)
const repoRoot = 'E:/face'
const previewRoot = 'E:/FACE/.artifacts/预览'
const profileRoot = path.join(repoRoot, 'tmp', `m4-purchase-ui-${Date.now()}`)
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const screenshots = {
  orders: path.join(previewRoot, 'M4-管理端-采购单闭环.png'),
  batches: path.join(previewRoot, 'M4-管理端-批次库存.png'),
}

if (!stateFile || !fs.existsSync(stateFile)) {
  throw new Error('M4 purchase UI verifier requires a valid temporary state file.')
}

JSON.parse(fs.readFileSync(stateFile, 'utf8'))
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

function startAdmin() {
  const environment = { ...process.env, VITE_CHAIN_API: backend }
  const build = spawnSync(
    process.env.ComSpec || 'cmd.exe',
    ['/d', '/s', '/c', 'npm.cmd run build'],
    {
      cwd: path.join(repoRoot, 'admin-next'),
      windowsHide: true,
      stdio: 'pipe',
      encoding: 'utf8',
      env: environment,
    },
  )
  if (build.status !== 0) {
    throw new Error(`Admin build failed: ${build.stderr || build.stdout}`)
  }
  const child = spawn(
    process.env.ComSpec || 'cmd.exe',
    ['/d', '/s', '/c', `npm.cmd run preview -- --host 127.0.0.1 --port ${adminPort} --strictPort`],
    {
      cwd: path.join(repoRoot, 'admin-next'),
      windowsHide: true,
      stdio: 'ignore',
      env: environment,
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

try {
  fs.mkdirSync(previewRoot, { recursive: true })
  fs.mkdirSync(profileRoot, { recursive: true })
  const v2Login = await fetch(`${backend}/api/v2/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'Face@123' }),
  }).then((response) => response.json())
  const v3Login = await fetch(`${backend}/api/v3/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'Face@123' }),
  }).then((response) => response.json())
  if (!v2Login.data?.token || !v3Login.data?.access_token) {
    throw new Error('M4 purchase UI verifier could not establish fresh admin sessions.')
  }
  startAdmin()
  await waitForUrl(`http://127.0.0.1:${adminPort}`)

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
  if (!page) throw new Error('No controllable admin page was found.')
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
      && message.params?.entry?.level === 'error'
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
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 960,
    deviceScaleFactor: 1,
    mobile: false,
    screenWidth: 1440,
    screenHeight: 960,
  })

  await send('Page.navigate', { url: `http://127.0.0.1:${adminPort}/login` })
  await sleep(350)
  await evaluate(`(() => {
    localStorage.setItem('face-chain-token', ${JSON.stringify(v2Login.data.token)});
    localStorage.setItem('face-chain-v3-access-token', ${JSON.stringify(v3Login.data.access_token)});
    localStorage.setItem('face-chain-v3-refresh-token', '');
    return true;
  })()`)
  await send('Page.navigate', { url: `http://127.0.0.1:${adminPort}/purchases` })
  await waitForPage(
    `document.body.innerText.includes('采购与批次')
      && document.body.innerText.includes('M4合成测试供应商')
      && document.querySelectorAll('.purchase-table tbody tr').length >= 2`,
    'purchase order workspace',
  )
  await evaluate(`(() => {
    [...document.querySelectorAll('.purchase-table tbody tr')]
      .find((row) => row.textContent.includes('M4合成测试供应商'))
      ?.querySelector('button')?.click();
    return true;
  })()`)
  await waitForPage(
    `document.body.innerText.includes('商品与到货进度')
      && document.body.innerText.includes('历史收货')`,
    'purchase order detail',
  )
  const orders = await evaluate(`(() => ({
    heading: document.body.innerText.includes('采购与批次'),
    navVisible: [...document.querySelectorAll('.sidebar-nav a')]
      .some((link) => link.textContent.includes('采购与批次')),
    rows: document.querySelectorAll('.purchase-table tbody tr').length,
    detail: document.body.innerText.includes('商品与到货进度'),
    receipts: document.body.innerText.includes('历史收货'),
    separationCopy: document.body.innerText.includes('申请人与审批人分离'),
    lightTheme: getComputedStyle(document.documentElement).colorScheme === 'light',
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    errorVisible: Boolean(document.querySelector('.el-alert--error')),
  }))()`)
  await capture(screenshots.orders)

  await evaluate(`(() => {
    [...document.querySelectorAll('.purchase-tabs button')]
      .find((button) => button.textContent.includes('批次库存'))?.click();
    return true;
  })()`)
  await waitForPage(
    `document.body.innerText.includes('按商品、地点和效期顺序')
      && document.querySelectorAll('.purchase-batch-table tbody tr').length >= 2`,
    'batch inventory workspace',
  )
  const batches = await evaluate(`(() => ({
    rows: document.querySelectorAll('.purchase-batch-table tbody tr').length,
    fefoCopy: document.body.innerText.includes('效期'),
    sourceVisible: document.body.innerText.includes('PURCHASE_RECEIPT'),
    filters: document.querySelectorAll('.purchase-filters .el-select').length >= 3,
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    errorVisible: Boolean(document.querySelector('.el-alert--error')),
  }))()`)
  await capture(screenshots.batches)

  const results = { orders, batches, screenshots, browserErrors, networkFailures }
  const checks = [
    orders.heading,
    orders.navVisible,
    orders.rows >= 2,
    orders.detail,
    orders.receipts,
    orders.separationCopy,
    orders.lightTheme,
    orders.noOverflow,
    !orders.errorVisible,
    batches.rows >= 2,
    batches.fefoCopy,
    batches.sourceVisible,
    batches.filters,
    batches.noOverflow,
    !batches.errorVisible,
    browserErrors.length === 0,
    networkFailures.length === 0,
  ]
  if (checks.some((check) => !check)) {
    throw new Error(`M4 purchase UI verification failed: ${JSON.stringify(results)}`)
  }
  console.log(JSON.stringify(results, null, 2))
  console.log('M4_PURCHASE_UI=PASS')
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
