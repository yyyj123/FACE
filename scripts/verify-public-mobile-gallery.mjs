import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import WebSocket from '../admin/node_modules/ws/index.js'

const baseUrl = (process.env.FACE_PUBLIC_URL || 'https://nav-water-layout-shore.trycloudflare.com').replace(/\/$/, '')
const previewDir = 'E:/FACE/.artifacts/预览/FACE-公网修复验收'
const profileDir = 'E:/face/.runtime/public-mobile-gallery-profile'
const debugPort = 9255
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
let browser
let socket
let nextId = 0
const pending = new Map()
const failures = []
const imageTraffic = []

function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++nextId
    pending.set(id, { resolve, reject })
    socket.send(JSON.stringify({ id, method, params }))
  })
}

async function evaluate(expression) {
  const response = await send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  })
  if (response.exceptionDetails) throw new Error(response.exceptionDetails.text)
  return response.result?.result?.value
}

async function waitFor(expression, timeout = 30000) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    if (await evaluate(expression)) return
    await sleep(250)
  }
  throw new Error(`Timed out waiting for: ${expression}`)
}

try {
  fs.mkdirSync(previewDir, { recursive: true })
  fs.mkdirSync(profileDir, { recursive: true })
  browser = spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    `--remote-debugging-port=${debugPort}`,
    `--user-data-dir=${profileDir}`,
    'about:blank',
  ], { windowsHide: true, stdio: 'ignore' })

  let tabs
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      tabs = await (await fetch(`http://127.0.0.1:${debugPort}/json`)).json()
      break
    } catch {
      await sleep(250)
    }
  }
  if (!tabs) throw new Error('Browser did not start')

  socket = new WebSocket(tabs.find((tab) => tab.type === 'page').webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    socket.once('open', resolve)
    socket.once('error', reject)
  })
  socket.on('message', (raw) => {
    const message = JSON.parse(raw)
    if (message.id && pending.has(message.id)) {
      pending.get(message.id).resolve(message)
      pending.delete(message.id)
    }
    if (message.method === 'Network.responseReceived') {
      const response = message.params.response
      if (response.status >= 400) failures.push({ status: response.status, url: response.url })
      if (message.params.type === 'Image') imageTraffic.push({ event: 'response', status: response.status, mimeType: response.mimeType, url: response.url })
    }
    if (message.method === 'Network.loadingFailed' && message.params.type === 'Image') {
      imageTraffic.push({ event: 'failed', errorText: message.params.errorText, blockedReason: message.params.blockedReason })
    }
  })

  await send('Runtime.enable')
  await send('Page.enable')
  await send('Network.enable')
  await send('Emulation.setDeviceMetricsOverride', {
    width: 390,
    height: 844,
    deviceScaleFactor: 3,
    mobile: true,
  })
  await send('Page.navigate', { url: baseUrl })
  await waitFor(`document.querySelectorAll('.native-gallery-list').length === 2`)
  await sleep(3000)
  const initialImages = await evaluate(`[...document.querySelectorAll('.native-gallery-list img')].slice(0, 4).map((image) => ({
    src: image.src, currentSrc: image.currentSrc, loading: image.loading,
    complete: image.complete, naturalWidth: image.naturalWidth
  }))`)
  console.log(`PUBLIC_MOBILE_GALLERY_DEBUG=${JSON.stringify({ initialImages, imageTraffic })}`)

  for (const index of [0, 1]) {
    await evaluate(`document.querySelectorAll('.native-gallery-list')[${index}].scrollIntoView({ block: 'center' })`)
    await waitFor(`(() => {
      const image = document.querySelectorAll('.native-gallery-list')[${index}].querySelector('img')
      return image && image.complete && image.naturalWidth > 0
    })()`)
  }

  const state = await evaluate(`({
    viewport: { width: innerWidth, height: innerHeight },
    nativeGalleryCount: document.querySelectorAll('.native-gallery-list').length,
    webglCanvasCount: document.querySelectorAll('.gallery-stage canvas').length,
    firstImages: [...document.querySelectorAll('.native-gallery-list')].map((list) => {
      const image = list.querySelector('img')
      return { complete: image.complete, naturalWidth: image.naturalWidth, naturalHeight: image.naturalHeight }
    }),
    staffCardCount: document.querySelectorAll('.gallery-stage--staff .native-gallery-list li').length,
    hasGenericError: document.body.innerText.includes('Request failed with status code')
  })`)

  await evaluate(`document.querySelector('.gallery-showcase').scrollIntoView({ block: 'start' })`)
  await sleep(500)
  const capture = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  const screenshotPath = path.join(previewDir, '03-手机端原生图片画廊.png')
  fs.writeFileSync(screenshotPath, Buffer.from(capture.result.data, 'base64'))

  if (state.nativeGalleryCount !== 2
    || state.webglCanvasCount !== 0
    || state.firstImages.some((image) => !image.complete || image.naturalWidth <= 0)
    || state.hasGenericError
    || failures.length > 0) {
    throw new Error(`Mobile gallery acceptance failed: ${JSON.stringify({ state, failures })}`)
  }

  console.log(`PUBLIC_MOBILE_GALLERY=${JSON.stringify(state)}`)
  console.log(`PUBLIC_MOBILE_GALLERY_PREVIEW=${screenshotPath}`)
  console.log('PUBLIC_MOBILE_GALLERY_ACCEPTANCE=PASS')
} finally {
  if (socket) socket.close()
  if (browser) browser.kill()
}
