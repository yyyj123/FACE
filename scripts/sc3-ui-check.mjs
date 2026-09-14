import fs from 'node:fs'
import { spawn } from 'node:child_process'
import WebSocket from '../front/node_modules/ws/index.js'

const options = Object.fromEntries(
  process.argv.slice(2).map((entry) => {
    const [key, ...parts] = entry.replace(/^--/, '').split('=')
    return [key, parts.join('=')]
  }),
)
const clientOrigin = options.client ?? 'http://127.0.0.1:8882'
const adminOrigin = options.admin ?? 'http://127.0.0.1:8881'
const previewDir = 'E:/FACE/.artifacts/预览'
const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const debugPort = Number(options.debugPort ?? 9333)

fs.mkdirSync(previewDir, { recursive: true })
const edge = spawn(edgePath, [
  '--headless=new', '--disable-gpu', '--no-first-run',
  `--remote-debugging-port=${debugPort}`, `--user-data-dir=E:/face/.runtime/sc3-ui-edge-${debugPort}`,
  'about:blank',
], { windowsHide: true, stdio: 'ignore' })

const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))
let tabs
for (let attempt = 0; attempt < 40; attempt += 1) {
  try {
    tabs = await (await fetch(`http://127.0.0.1:${debugPort}/json`)).json()
    break
  } catch { await sleep(250) }
}
const tab = tabs?.find((entry) => entry.type === 'page')
if (!tab) throw new Error('Edge debugging page did not start')
const socket = new WebSocket(tab.webSocketDebuggerUrl)
await new Promise((resolve, reject) => {
  socket.once('open', resolve)
  socket.once('error', reject)
})

let sequence = 0
const pending = new Map()
socket.on('message', (raw) => {
  const message = JSON.parse(raw)
  if (!message.id || !pending.has(message.id)) return
  const item = pending.get(message.id)
  pending.delete(message.id)
  if (message.error) item.reject(new Error(message.error.message))
  else item.resolve(message.result)
})
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence
    pending.set(id, { resolve, reject })
    socket.send(JSON.stringify({ id, method, params }))
  })
}
async function navigate(url, delay = 1000) {
  await send('Page.navigate', { url })
  await sleep(delay)
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
  return result.result.value
}

await send('Page.enable')
await send('Runtime.enable')

await navigate(clientOrigin)
await evaluate(`(async () => {
  const response = await fetch('/face-next/api/v3/client/identity/password-login', {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({phone: '13900003031', password: 'Face@123'})
  })
  const payload = await response.json()
  if (!response.ok || !payload.data?.access_token) throw new Error('client login failed')
  const value = payload.data
  localStorage.setItem('face-client-session-v2', JSON.stringify({
    token: value.access_token, accountId: value.account_id, shopId: value.shop_id,
    username: '13900003031', role: value.role, memberId: value.member_id,
    v3AccessToken: value.access_token, v3RefreshToken: value.refresh_token
  }))
  return true
})()`)

await navigate(adminOrigin)
await evaluate(`(async () => {
  const response = await fetch('/face-next/api/v3/auth/admin-login', {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({username: 'admin', password: 'Face@123'})
  })
  const payload = await response.json()
  if (!response.ok || !payload.data?.access_token) throw new Error('admin login failed')
  localStorage.setItem('face-chain-token', payload.data.access_token)
  localStorage.setItem('face-chain-v3-access-token', payload.data.access_token)
  localStorage.setItem('face-chain-v3-refresh-token', payload.data.refresh_token)
  return true
})()`)

const checks = []
for (const page of [
  { name: 'SC3-用户预约-桌面', url: `${clientOrigin}/booking?serviceId=1`, width: 1440, height: 1100, expected: '预约到店护理' },
  { name: 'SC3-用户预约-移动', url: `${clientOrigin}/booking?serviceId=1`, width: 390, height: 844, expected: '预约到店护理' },
  { name: 'SC3-运营排班-桌面', url: `${adminOrigin}/booking-operations`, width: 1440, height: 1100, expected: '预约与排班' },
  { name: 'SC3-运营排班-移动', url: `${adminOrigin}/booking-operations`, width: 390, height: 844, expected: '预约与排班' },
]) {
  await send('Emulation.setDeviceMetricsOverride', {
    width: page.width, height: page.height, deviceScaleFactor: 1, mobile: page.width < 600,
  })
  // Reparse the viewport meta tag under the new emulated device. This avoids
  // retaining Edge's previous desktop layout viewport when switching sizes.
  await navigate('about:blank', 150)
  await navigate(page.url, 1600)
  const metrics = await evaluate(`({
    innerWidth,
    screenWidth: screen.width,
    visualViewportWidth: visualViewport?.width,
    scrollWidth: document.documentElement.scrollWidth,
    scrollHeight: document.documentElement.scrollHeight,
    overflowX: document.documentElement.scrollWidth > innerWidth,
    title: document.title,
    h1: document.querySelector('h1')?.textContent?.trim(),
    viewportMeta: document.querySelector('meta[name="viewport"]')?.content,
    htmlMinWidth: getComputedStyle(document.documentElement).minWidth,
    bodyMinWidth: getComputedStyle(document.body).minWidth,
    serviceOptions: document.querySelectorAll('.service-options button').length,
    scheduleCards: document.querySelectorAll('.schedule-card').length,
    runtimeErrors: [...document.querySelectorAll('.error, .el-alert--error')].map(node => node.textContent?.trim()).filter(Boolean)
  })`)
  if (metrics.overflowX) throw new Error(`${page.name} has horizontal overflow`)
  if (metrics.innerWidth !== page.width) throw new Error(`${page.name} viewport mismatch: ${JSON.stringify(metrics)}`)
  if (metrics.h1 !== page.expected) throw new Error(`${page.name} unexpected heading: ${metrics.h1}`)
  const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  fs.writeFileSync(`${previewDir}/${page.name}.png`, Buffer.from(screenshot.data, 'base64'))
  checks.push({ name: page.name, width: page.width, height: page.height, ...metrics })
}

socket.close()
edge.kill()
console.log(JSON.stringify(checks, null, 2))
