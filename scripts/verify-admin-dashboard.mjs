import { spawn } from 'node:child_process'
import fs from 'node:fs'
import WebSocket from '../admin/node_modules/ws/index.js'

const token = process.env.DASHBOARD_TEST_TOKEN
if (!token) throw new Error('DASHBOARD_TEST_TOKEN is required')

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const previewDir = 'E:/FACE/.artifacts/预览/FACE-管理端仪表盘'
const profileDir = 'E:/face/.runtime/admin-dashboard-profile'
const port = 9243
const pageUrl = `http://127.0.0.1:8081/?dashboardQa=${Date.now()}#/`
let browser
let socket
let requestId = 0
const pending = new Map()
const runtimeErrors = []
const failedRequests = []

const send = (method, params = {}) => new Promise(resolve => {
  const id = ++requestId
  pending.set(id, resolve)
  socket.send(JSON.stringify({ id, method, params }))
})

const evaluate = async expression => {
  const response = await send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true
  })
  if (response.exceptionDetails) throw new Error(response.exceptionDetails.text)
  return response.result?.result?.value
}

const waitFor = async (expression, timeout = 20000) => {
  const endAt = Date.now() + timeout
  while (Date.now() < endAt) {
    if (await evaluate(expression)) return
    await sleep(250)
  }
  throw new Error(`Timed out waiting for: ${expression}`)
}

try {
  fs.mkdirSync(profileDir, { recursive: true })
  fs.mkdirSync(previewDir, { recursive: true })
  browser = spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--window-size=1920,1200',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profileDir}`,
    'about:blank'
  ], { windowsHide: true, stdio: 'ignore' })

  let tabs
  for (let attempt = 0; attempt < 50; attempt += 1) {
    try {
      tabs = await (await fetch(`http://127.0.0.1:${port}/json`)).json()
      break
    } catch {
      await sleep(200)
    }
  }
  if (!tabs) throw new Error('Browser did not start')

  const pageTab = tabs.find(tab => tab.type === 'page')
  if (!pageTab) {
    throw new Error(`Admin page target not found: ${tabs.map(tab => `${tab.type}:${tab.url}`).join(', ')}`)
  }
  socket = new WebSocket(pageTab.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    socket.once('open', resolve)
    socket.once('error', reject)
  })
  socket.on('message', raw => {
    const message = JSON.parse(raw)
    if (message.id && pending.has(message.id)) {
      pending.get(message.id)(message)
      pending.delete(message.id)
    }
    if (message.method === 'Runtime.exceptionThrown') {
      const details = message.params.exceptionDetails
      runtimeErrors.push(details.exception?.description || details.text)
    }
    if (message.method === 'Network.responseReceived' && message.params.response.status >= 400) {
      failedRequests.push({
        status: message.params.response.status,
        url: message.params.response.url
      })
    }
  })
  await send('Runtime.enable')
  await send('Page.enable')
  await send('Network.enable')
  await send('Network.setCacheDisabled', { cacheDisabled: true })
  await send('Page.addScriptToEvaluateOnNewDocument', {
    source: `(() => {
      localStorage.setItem('Token', ${JSON.stringify(token)})
      localStorage.setItem('role', '管理员')
      localStorage.setItem('adminName', 'admin')
      localStorage.setItem('apiRole', 'OWNER')
      localStorage.setItem('sessionTable', 'account')
    })()`
  })
  await send('Page.navigate', { url: pageUrl })

  await waitFor(`document.querySelector('.home-content') !== null`)
  await waitFor(`document.querySelectorAll('.type3 canvas').length >= 3`)
  await waitFor(`document.querySelector('.statis-box')?.innerText?.includes('5')`)
  await sleep(1200)

  const state = await evaluate(`(() => {
    const home = document.querySelector('.home-content')?.__vue__
    return {
      memberCount: home?.chezhuCount,
      todayAppointments: home?.weixiujishiCount,
      todayCompleted: home?.weixiujiluCount,
      chartCanvasCount: document.querySelectorAll('.type3 canvas').length,
      chartText: document.querySelector('.type3')?.innerText || ''
    }
  })()`)

  const screenshot = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: false
  })
  fs.writeFileSync(
    `${previewDir}/修复后-业务数据统计.png`,
    Buffer.from(screenshot.result.data, 'base64')
  )

  const result = {
    state,
    runtimeErrors,
    failedRequests,
    summaryLoaded: state.memberCount === 5 && state.todayAppointments === 1,
    chartsRendered: state.chartCanvasCount >= 3
  }
  console.log(JSON.stringify(result, null, 2))
  if (!result.summaryLoaded || !result.chartsRendered || runtimeErrors.length) process.exitCode = 1
} catch (error) {
  let diagnostic = null
  if (socket?.readyState === WebSocket.OPEN) {
    try {
      diagnostic = await evaluate(`({
        href: location.href,
        title: document.title,
        readyState: document.readyState,
        bodyText: document.body?.innerText?.slice(0, 1200) || '',
        homeCount: document.querySelectorAll('.home-content').length,
        canvasCount: document.querySelectorAll('canvas').length
      })`)
    } catch {}
  }
  console.error(JSON.stringify({ error: error.message, diagnostic, runtimeErrors, failedRequests }, null, 2))
  process.exitCode = 1
} finally {
  if (socket?.readyState === WebSocket.OPEN) {
    try {
      await evaluate(`(() => {
        localStorage.removeItem('Token')
        localStorage.removeItem('role')
        localStorage.removeItem('adminName')
        localStorage.removeItem('apiRole')
        localStorage.removeItem('sessionTable')
        return true
      })()`)
    } catch {}
  }
  try { socket?.close() } catch {}
  try { browser?.kill() } catch {}
  await sleep(300)
}
