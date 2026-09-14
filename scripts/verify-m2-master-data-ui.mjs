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
const adminPort = Number(args['admin-port'] ?? 8081)
const repoRoot = 'E:/face'
const adminRoot = path.join(repoRoot, 'admin-next')
const previewRoot = 'E:/FACE/.artifacts/预览'
const profileRoot = path.join(repoRoot, 'tmp', `m2-ui-profile-${Date.now()}`)
const desktopScreenshot = path.join(previewRoot, 'M2-基础资料与资源-桌面.png')
const mobileScreenshot = path.join(previewRoot, 'M2-基础资料与资源-窄屏.png')
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const results = {}
let vite
let edge
let ws
let commandId = 0
const pending = new Map()

const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))

async function waitForUrl(url, attempts = 120) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const response = await fetch(url)
      if (response.ok) return
    } catch {
      // Service is still starting.
    }
    await sleep(250)
  }
  throw new Error(`Timed out waiting for ${url}`)
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

async function capture(filePath) {
  const message = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: false,
    fromSurface: true,
  })
  fs.writeFileSync(filePath, Buffer.from(message.result.data, 'base64'))
}

async function waitForPage(predicate, attempts = 80) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    if (await evaluate(predicate)) return
    await sleep(250)
  }
  const diagnostic = await evaluate(`({
    href: location.href,
    title: document.title,
    text: document.body?.innerText?.slice(0, 1200),
    html: document.body?.innerHTML?.slice(0, 1200),
  })`)
  throw new Error(`Timed out waiting for the master data page: ${JSON.stringify(diagnostic)}`)
}

try {
  fs.mkdirSync(previewRoot, { recursive: true })
  fs.mkdirSync(profileRoot, { recursive: true })

  const legacyLogin = await fetch(`${backend}/api/v2/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'jishi01', password: 'Face@123' }),
  }).then((response) => response.json())
  const v3Login = await fetch(`${backend}/api/v3/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'jishi01', password: 'Face@123' }),
  }).then((response) => response.json())
  if (!legacyLogin.data?.token || !v3Login.data?.access_token) {
    throw new Error('Synthetic UI login failed')
  }

  vite = spawn(
    'npm.cmd',
    ['run', 'dev', '--', '--host', '127.0.0.1', '--port', String(adminPort)],
    {
      cwd: adminRoot,
      windowsHide: true,
      shell: true,
      stdio: 'ignore',
      env: {
        ...process.env,
        VITE_CHAIN_API: backend,
      },
    },
  )
  await waitForUrl(`http://127.0.0.1:${adminPort}`)

  edge = spawn(
    edgePath,
    [
      `--user-data-dir=${profileRoot}`,
      '--headless=new',
      '--remote-debugging-port=9341',
      '--no-first-run',
      '--disable-features=Translate',
      'about:blank',
    ],
    { windowsHide: true, stdio: 'ignore' },
  )
  await waitForUrl('http://127.0.0.1:9341/json/version')
  const pages = await fetch('http://127.0.0.1:9341/json').then((response) => response.json())
  const applicationPage = pages.find(
    (page) => page.type === 'page' && !page.url.startsWith('chrome-extension://'),
  )
  if (!applicationPage) throw new Error('No controllable application page was found')
  ws = new WebSocket(applicationPage.webSocketDebuggerUrl)
  await new Promise((resolve) => ws.once('open', resolve))
  ws.on('message', (payload) => {
    const message = JSON.parse(payload)
    if (!message.id || !pending.has(message.id)) return
    pending.get(message.id).resolve(message)
    pending.delete(message.id)
  })
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 960,
    deviceScaleFactor: 1,
    mobile: false,
  })
  await send('Page.navigate', { url: `http://127.0.0.1:${adminPort}/login` })
  await sleep(500)
  await evaluate(`(() => {
    localStorage.setItem('face-chain-token', ${JSON.stringify(legacyLogin.data.token)});
    localStorage.setItem('face-chain-v3-access-token', ${JSON.stringify(v3Login.data.access_token)});
    localStorage.setItem('face-chain-v3-refresh-token', ${JSON.stringify(v3Login.data.refresh_token)});
    return true;
  })()`)
  await send('Page.navigate', { url: `http://127.0.0.1:${adminPort}/master-data` })
  await waitForPage(`document.body.innerText.includes('把可预约条件配置清楚')`)
  await sleep(1000)

  results.desktop = await evaluate(`(() => {
    const text = document.body.innerText;
    const tables = [...document.querySelectorAll('.master-table')];
    const nav = [...document.querySelectorAll('.sidebar-nav a')].find(
      (item) => item.textContent.includes('基础资料与资源')
    );
    return {
      heading: text.includes('把可预约条件配置清楚'),
      navActive: Boolean(nav?.classList.contains('active')),
      whiteTheme: getComputedStyle(document.documentElement).colorScheme === 'light',
      serviceRows: document.querySelectorAll('.master-table tbody tr').length,
      noDocumentOverflow: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
      tableWidthsValid: tables.every((table) => table.scrollWidth >= table.clientWidth),
      errorVisible: Boolean(document.querySelector('.master-error')),
    };
  })()`)
  await capture(desktopScreenshot)

  await evaluate(`(() => {
    [...document.querySelectorAll('.master-tabs button')]
      .find((button) => button.textContent.includes('房间与设备'))?.click();
    return true;
  })()`)
  await sleep(400)
  await evaluate(`(() => {
    [...document.querySelectorAll('button')]
      .find((button) => button.textContent.trim() === '新增资源')?.click();
    return true;
  })()`)
  await sleep(350)
  results.editor = await evaluate(`({
    open: Boolean(document.querySelector('.master-editor')),
    codeField: Boolean(document.querySelector('.master-editor input')),
    primaryAction: [...document.querySelectorAll('.master-editor button')]
      .some((button) => button.textContent.trim() === '保存'),
  })`)

  await send('Emulation.setDeviceMetricsOverride', {
    width: 390,
    height: 844,
    deviceScaleFactor: 1,
    mobile: true,
    screenWidth: 390,
    screenHeight: 844,
  })
  await send('Page.reload', { ignoreCache: true })
  await waitForPage(`document.body.innerText.includes('把可预约条件配置清楚')`)
  await sleep(800)
  await evaluate(`(() => {
    [...document.querySelectorAll('.master-tabs button')]
      .find((button) => button.textContent.includes('房间与设备'))?.click();
    return true;
  })()`)
  await sleep(300)
  await evaluate(`(() => {
    [...document.querySelectorAll('button')]
      .find((button) => button.textContent.trim() === '新增资源')?.click();
    return true;
  })()`)
  await sleep(350)
  results.mobile = await evaluate(`(() => {
    const viewportWidth = document.documentElement.clientWidth;
    const hasClippingAncestor = (element) => {
      let parent = element.parentElement;
      while (parent && parent !== document.body) {
        const overflowX = getComputedStyle(parent).overflowX;
        if (['auto', 'scroll', 'hidden', 'clip'].includes(overflowX)) return true;
        parent = parent.parentElement;
      }
      return false;
    };
    const overflowSources = [...document.querySelectorAll('*')]
      .filter((element) => {
        const rect = element.getBoundingClientRect();
        return rect.width > 0
          && rect.right > viewportWidth + 1
          && !hasClippingAncestor(element);
      })
      .slice(0, 12)
      .map((element) => {
        const rect = element.getBoundingClientRect();
        return {
          tag: element.tagName,
          className: typeof element.className === 'string' ? element.className : '',
          left: Math.round(rect.left),
          right: Math.round(rect.right),
          width: Math.round(rect.width),
          clientWidth: element.clientWidth,
          scrollWidth: element.scrollWidth,
        };
      });
    return {
    width: viewportWidth,
    documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth,
    editorBeforeResults: (() => {
      const editor = document.querySelector('.master-editor');
      const results = document.querySelector('.master-results');
      if (!editor || !results) return false;
      return editor.getBoundingClientRect().top < results.getBoundingClientRect().top;
    })(),
    noDocumentOverflow: document.body.scrollWidth <= viewportWidth
      && overflowSources.length === 0,
    nestedTableScrollAvailable: [...document.querySelectorAll('.master-table-wrap')]
      .every((wrapper) => wrapper.scrollWidth >= wrapper.clientWidth),
    shopControlVisible: document.querySelector('.master-heading-actions')?.getBoundingClientRect().width > 0,
    overflowSources,
  };
  })()`)
  await capture(mobileScreenshot)

  const checks = [
    results.desktop.heading,
    results.desktop.navActive,
    results.desktop.whiteTheme,
    results.desktop.serviceRows > 0,
    results.desktop.noDocumentOverflow,
    !results.desktop.errorVisible,
    results.editor.open,
    results.editor.primaryAction,
    results.mobile.editorBeforeResults,
    results.mobile.noDocumentOverflow,
    results.mobile.nestedTableScrollAvailable,
    results.mobile.shopControlVisible,
  ]
  if (checks.some((check) => !check)) {
    throw new Error(`M2 UI verification failed: ${JSON.stringify(results)}`)
  }
  results.screenshots = { desktopScreenshot, mobileScreenshot }
  console.log(JSON.stringify(results, null, 2))
  console.log('M2_MASTER_DATA_UI=PASS')
} finally {
  try {
    ws?.close()
  } catch {
    // Best-effort browser cleanup.
  }
  try {
    if (edge?.pid) {
      spawnSync('taskkill.exe', ['/PID', String(edge.pid), '/T', '/F'], {
        windowsHide: true,
        stdio: 'ignore',
      })
    }
  } catch {
    // Best-effort browser cleanup.
  }
  try {
    if (vite?.pid) {
      spawnSync('taskkill.exe', ['/PID', String(vite.pid), '/T', '/F'], {
        windowsHide: true,
        stdio: 'ignore',
      })
    }
  } catch {
    // Best-effort dev server cleanup.
  }
  await sleep(500)
  const resolvedProfile = path.resolve(profileRoot)
  const allowedProfileRoot = path.resolve(repoRoot, 'tmp') + path.sep
  if (resolvedProfile.startsWith(allowedProfileRoot)) {
    fs.rmSync(resolvedProfile, { recursive: true, force: true })
  }
}
