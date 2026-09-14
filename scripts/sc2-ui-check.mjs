import fs from 'node:fs'
import { spawn } from 'node:child_process'
import WebSocket from '../front/node_modules/ws/index.js'

const edgePath = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const previewDir = 'E:/FACE/.artifacts/预览'
const edge = spawn(edgePath, [
  '--headless=new', '--disable-gpu', '--no-first-run',
  '--remote-debugging-port=9332', '--user-data-dir=E:/face/.runtime/sc2-ui-edge',
  'about:blank',
], { windowsHide: true, stdio: 'ignore' })

const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))
let tabs
for (let attempt = 0; attempt < 30; attempt += 1) {
  try {
    tabs = await (await fetch('http://127.0.0.1:9332/json')).json()
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

await send('Page.enable')
await send('Runtime.enable')
const checks = []
for (const page of [
  { name: 'SC2-用户端登录-桌面', url: 'http://127.0.0.1:4301/login', width: 1440, height: 1000 },
  { name: 'SC2-用户端登录-移动', url: 'http://127.0.0.1:4301/login', width: 390, height: 844 },
  { name: 'SC2-运营平台登录-桌面', url: 'http://127.0.0.1:4302/login', width: 1440, height: 1000 },
  { name: 'SC2-运营平台登录-移动', url: 'http://127.0.0.1:4302/login', width: 390, height: 844 },
]) {
  await send('Emulation.setDeviceMetricsOverride', {
    width: page.width, height: page.height, deviceScaleFactor: 1, mobile: page.width < 600,
  })
  await send('Page.navigate', { url: page.url })
  await sleep(900)
  const metrics = await send('Runtime.evaluate', {
    expression: `({innerWidth, scrollWidth: document.documentElement.scrollWidth,
      scrollHeight: document.documentElement.scrollHeight,
      overflowX: document.documentElement.scrollWidth > innerWidth,
      title: document.title,
      h1: document.querySelector('h1')?.textContent?.trim()})`,
    returnByValue: true,
  })
  const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  fs.writeFileSync(`${previewDir}/${page.name}.png`, Buffer.from(screenshot.data, 'base64'))
  checks.push({ name: page.name, width: page.width, height: page.height, ...metrics.result.value })
}

socket.close()
edge.kill()
console.log(JSON.stringify(checks, null, 2))
