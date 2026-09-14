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
const adminPort = Number(args['admin-port'] ?? 8085)
const debugPort = Number(args['debug-port'] ?? 9347)
const repoRoot = 'E:/face'
const previewRoot = 'E:/FACE/.artifacts/预览'
const profileRoot = path.join(repoRoot, 'tmp', `m4-reconciliation-ui-${Date.now()}`)
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const screenshot = path.join(previewRoot, 'M4-管理端-支付与对账.png')
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
  if (build.status !== 0) throw new Error(`Admin build failed: ${build.stderr || build.stdout}`)
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
  return new Promise((resolve) => {
    const id = ++commandId
    pending.set(id, resolve)
    ws.send(JSON.stringify({ id, method, params }))
  })
}

async function evaluate(expression) {
  const message = await send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  })
  if (message.result.exceptionDetails) throw new Error(message.result.exceptionDetails.text)
  return message.result.result.value
}

async function waitForPage(predicate, label, attempts = 120) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    if (await evaluate(predicate)) return
    await sleep(250)
  }
  const text = await evaluate('document.body?.innerText?.slice(0, 1800)')
  const location = await evaluate('window.location.href')
  throw new Error(
    `Timed out waiting for ${label}: ${JSON.stringify({ location, text, networkFailures })}`,
  )
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
    throw new Error('Could not establish admin sessions.')
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
      pending.get(message.id)(message)
      pending.delete(message.id)
    } else if (message.method === 'Runtime.exceptionThrown') {
      browserErrors.push(message.params?.exceptionDetails?.text ?? 'Runtime exception')
    } else if (message.method === 'Log.entryAdded' && message.params?.entry?.level === 'error') {
      browserErrors.push(message.params.entry.text)
    } else if (
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
  })
  await send('Page.addScriptToEvaluateOnNewDocument', {
    source: `(() => {
    localStorage.setItem('face-chain-token', ${JSON.stringify(v2Login.data.token)});
    localStorage.setItem('face-chain-v3-access-token', ${JSON.stringify(v3Login.data.access_token)});
    localStorage.setItem('face-chain-v3-refresh-token', '');
  })();`,
  })
  await send('Page.navigate', { url: `http://127.0.0.1:${adminPort}/reconciliation` })
  await waitForPage(
    `document.body.innerText.includes('支付与对账')
      && document.body.innerText.includes('账务一致')
      && document.body.innerText.includes('已关闭')
      && document.querySelectorAll('.reconciliation-table tbody tr').length >= 2`,
    'reconciliation workspace',
  )
  await evaluate(`(() => {
    [...document.querySelectorAll('.reconciliation-table tbody tr')]
      .find((row) => row.textContent.includes('CARD'))
      ?.querySelector('button')?.click();
    return true;
  })()`)
  await waitForPage(
    `document.body.innerText.includes('差异记录')
      && document.body.innerText.includes('处理记录')`,
    'reconciliation detail',
  )
  const result = await evaluate(`(() => ({
    heading: document.body.innerText.includes('支付与对账'),
    navVisible: [...document.querySelectorAll('.sidebar-nav a')]
      .some((link) => link.textContent.includes('支付与对账')),
    rows: document.querySelectorAll('.reconciliation-table tbody tr').length,
    detail: document.body.innerText.includes('差异记录'),
    auditCopy: document.body.innerText.includes('不能直接抹平'),
    sandboxCopy: document.body.innerText.includes('SANDBOX'),
    lightTheme: getComputedStyle(document.documentElement).colorScheme === 'light',
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    errorVisible: Boolean(document.querySelector('.el-alert--error')),
  }))()`)
  const screenshotMessage = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: false,
    fromSurface: true,
  })
  fs.writeFileSync(screenshot, Buffer.from(screenshotMessage.result.data, 'base64'))
  if (
    !result.heading
    || !result.navVisible
    || result.rows < 2
    || !result.detail
    || !result.auditCopy
    || !result.sandboxCopy
    || !result.lightTheme
    || !result.noOverflow
    || result.errorVisible
    || browserErrors.length
    || networkFailures.length
  ) {
    throw new Error(
      `M4 reconciliation UI verification failed: ${JSON.stringify({
        result,
        browserErrors,
        networkFailures,
      })}`,
    )
  }
  console.log(JSON.stringify({ result, screenshot, browserErrors, networkFailures }, null, 2))
  console.log('M4_RECONCILIATION_UI=PASS')
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
