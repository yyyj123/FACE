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
const debugPort = Number(args['debug-port'] ?? 9343)
const repoRoot = 'E:/face'
const previewRoot = 'E:/FACE/.artifacts/预览'
const profileRoot = path.join(repoRoot, 'tmp', `m3-ui-profile-${Date.now()}`)
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const screenshots = {
  technician: path.join(previewRoot, 'M3-技师护理工作台-桌面.png'),
  member: path.join(previewRoot, 'M3-顾客护理确认-窄屏.png'),
  admin: path.join(previewRoot, 'M3-管理端追加更正-桌面.png'),
}

if (!stateFile || !fs.existsSync(stateFile)) {
  throw new Error('M3 UI verifier requires a valid temporary state file.')
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
    text: document.body?.innerText?.slice(0, 1600),
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
    for (const [key, value] of Object.entries(entries)) {
      localStorage.setItem(key, typeof value === 'string' ? value : JSON.stringify(value));
    }
    return true;
  })()`)
}

try {
  fs.mkdirSync(previewRoot, { recursive: true })
  fs.mkdirSync(profileRoot, { recursive: true })

  const directAppointments = await fetch(`${backend}/api/v2/client/appointments`, {
    headers: { Authorization: `Bearer ${state.technicianSession.token}` },
  })
  if (!directAppointments.ok) {
    throw new Error(
      `M3 direct technician appointments returned ${directAppointments.status}: ${
        await directAppointments.text()
      }`,
    )
  }

  startVite(path.join(repoRoot, 'front-next'), frontPort, {
    VITE_CLIENT_API_BASE: `${backend}/api/v2/client`,
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
  await setStorage(`http://127.0.0.1:${frontPort}/login`, {
    'face-client-session-v2': state.technicianSession,
  })
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
  await sleep(350)
  await evaluate(`(() => {
    [...document.querySelectorAll('button')]
      .find((button) => button.textContent.trim() === '填写护理记录')?.click();
    return true;
  })()`)
  await waitForPage(
    `document.body.innerText.includes('记录本次护理并完成服务')
      && Boolean(document.querySelector('#summary'))
      && Boolean(document.querySelector('#observations'))`,
    'technician care workspace',
  )
  const technician = await evaluate(`(() => ({
    heading: document.body.innerText.includes('今日护理任务'),
    workspace: Boolean(document.querySelector('.care-workspace')),
    inventoryCopy: document.body.innerText.includes('完成服务时同步扣减库存'),
    lightTheme: getComputedStyle(document.body).backgroundColor !== 'rgb(10, 8, 12)',
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    alertVisible: Boolean(document.querySelector('[role="alert"]')),
  }))()`)
  await capture(screenshots.technician)

  await setViewport(390, 844, true)
  await setStorage(`http://127.0.0.1:${frontPort}/login`, {
    'face-client-session-v2': state.memberSession,
  })
  await send('Page.navigate', { url: `http://127.0.0.1:${frontPort}/appointments` })
  await waitForPage(
    `document.body.innerText.includes('待确认的护理结果')
      && document.body.innerText.includes('确认无误')
      && document.body.innerText.includes('反馈问题')`,
    'member confirmation',
  )
  const member = await evaluate(`(() => ({
    heading: document.body.innerText.includes('预约与护理记录'),
    pending: document.querySelectorAll('.confirmation-row').length > 0,
    independentCopy: document.body.innerText.includes('不会改写已完成的服务事实'),
    actions: [...document.querySelectorAll('.confirmation-actions button')].length >= 2,
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    alertVisible: Boolean(document.querySelector('[role="alert"]')),
  }))()`)
  await capture(screenshots.member)

  await setViewport(1440, 960)
  await setStorage(`http://127.0.0.1:${adminPort}/login`, {
    'face-chain-token': state.adminV2Token,
    'face-chain-v3-access-token': state.adminV3Session.access_token,
    'face-chain-v3-refresh-token': state.adminV3Session.refresh_token,
  })
  await send('Page.navigate', { url: `http://127.0.0.1:${adminPort}/services` })
  await waitForPage(
    `document.body.innerText.includes('到店服务与护理档案')
      && document.querySelectorAll('.service-table tbody tr').length > 0`,
    'admin service records',
  )
  await evaluate(`(() => {
    [...document.querySelectorAll('button')]
      .find((button) => button.textContent.trim() === '追加更正')?.click();
    return true;
  })()`)
  await waitForPage(
    `document.body.innerText.includes('追加护理更正')
      && document.body.innerText.includes('更正原因')`,
    'admin append correction workspace',
  )
  const admin = await evaluate(`(() => ({
    heading: document.body.innerText.includes('到店服务与护理档案'),
    records: document.querySelectorAll('.service-table tbody tr').length,
    correctionWorkspace: document.body.innerText.includes('追加护理更正'),
    immutableCopy: document.body.innerText.includes('提交后不可覆盖原始护理事实'),
    lightTheme: getComputedStyle(document.documentElement).colorScheme === 'light',
    noOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    errorVisible: Boolean(document.querySelector('.page-alert')),
  }))()`)
  await capture(screenshots.admin)

  const results = { technician, member, admin, screenshots, browserErrors }
  const checks = [
    technician.heading,
    technician.workspace,
    technician.inventoryCopy,
    technician.lightTheme,
    technician.noOverflow,
    !technician.alertVisible,
    member.heading,
    member.pending,
    member.independentCopy,
    member.actions,
    member.noOverflow,
    !member.alertVisible,
    admin.heading,
    admin.records > 0,
    admin.correctionWorkspace,
    admin.immutableCopy,
    admin.lightTheme,
    admin.noOverflow,
    !admin.errorVisible,
    browserErrors.length === 0,
  ]
  if (checks.some((check) => !check)) {
    throw new Error(`M3 UI verification failed: ${JSON.stringify(results)}`)
  }
  console.log(JSON.stringify(results, null, 2))
  console.log('M3_THREE_CLIENT_UI=PASS')
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
