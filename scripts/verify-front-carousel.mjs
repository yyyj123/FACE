import { spawn } from 'node:child_process'
import fs from 'node:fs'
import crypto from 'node:crypto'
import WebSocket from '../admin/node_modules/ws/index.js'

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const previewDir = 'E:/FACE/.artifacts/预览/FACE-轮播图问题'
const profileDir = 'E:/face/.runtime/front-carousel-profile'
const port = 9242
const pageUrl = `http://127.0.0.1:8082/?carouselQa=${Date.now()}#/index/home`
let browser
let socket
let requestId = 0
const pending = new Map()
const runtimeErrors = []

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

const waitFor = async (expression, timeout = 15000) => {
  const endAt = Date.now() + timeout
  while (Date.now() < endAt) {
    if (await evaluate(expression)) return
    await sleep(200)
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
    '--window-size=1440,1000',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profileDir}`,
    pageUrl
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

  socket = new WebSocket(tabs.find(tab => tab.type === 'page').webSocketDebuggerUrl)
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
      runtimeErrors.push(message.params.exceptionDetails.text)
    }
  })
  await send('Runtime.enable')
  await send('Page.enable')
  await send('Network.enable')
  await send('Network.setCacheDisabled', { cacheDisabled: true })
  await send('Page.navigate', { url: pageUrl })

  await waitFor(`document.querySelector('.main-containers')?.__vue__?.carouselList?.length >= 5`)
  await waitFor(`document.querySelectorAll('.oc-hero__image img').length >= 5`)
  await waitFor(`[...document.querySelectorAll('.oc-hero__image img')].every(img => img.complete && img.naturalWidth > 0)`)

  const before = await evaluate(`(() => {
    const shell = document.querySelector('.main-containers').__vue__
    return {
      dataCount: shell.carouselList.length,
      slideCount: document.querySelectorAll('.oc-hero__slide').length,
      imageSources: [...new Set([...document.querySelectorAll('.oc-hero__slide img')].map(img => img.src))],
      activeSlide: shell.carouselActiveIndex,
      activeImage: document.querySelector('.oc-hero__slide:not([style*="display: none"]) img')?.src || '',
      heroTitle: document.querySelector('#oc-hero-title')?.textContent?.trim() || '',
      reducedMotion: window.matchMedia('(prefers-reduced-motion: reduce)').matches,
      timerActive: Number.isInteger(shell.carouselAdvanceTimer),
      pauseButtonPresent: !!document.querySelector('.oc-swiper-toggle')
    }
  })()`)

  const beforeScreenshot = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: false
  })
  const beforeBuffer = Buffer.from(beforeScreenshot.result.data, 'base64')
  fs.writeFileSync(`${previewDir}/修复后-轮播切换前.png`, beforeBuffer)

  await sleep(5600)

  const after = await evaluate(`(() => {
    const shell = document.querySelector('.main-containers').__vue__
    return {
      activeSlide: shell.carouselActiveIndex,
      activeImage: document.querySelector('.oc-hero__slide:not([style*="display: none"]) img')?.src || ''
    }
  })()`)

  const screenshot = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: false
  })
  const afterBuffer = Buffer.from(screenshot.result.data, 'base64')
  fs.writeFileSync(`${previewDir}/修复后-管理端轮播图自动播放.png`, afterBuffer)

  const result = {
    before,
    after,
    runtimeErrors,
    showsManagedBanners: before.dataCount >= 5
      && before.imageSources.filter(src => /banner-0[1-5]\.webp/.test(src)).length === 5,
    showsConfiguredHeroTitle: before.heroTitle.length > 0,
    screenshotChanged: crypto.createHash('sha256').update(beforeBuffer).digest('hex')
      !== crypto.createHash('sha256').update(afterBuffer).digest('hex'),
    autoplayAdvanced: before.timerActive
      && before.activeSlide !== after.activeSlide
      && before.activeImage !== after.activeImage,
    pauseButtonRemoved: !before.pauseButtonPresent
  }
  console.log(JSON.stringify(result, null, 2))
  if (!result.showsManagedBanners || !result.showsConfiguredHeroTitle || !result.autoplayAdvanced || !result.screenshotChanged || !result.pauseButtonRemoved || runtimeErrors.length) {
    process.exitCode = 1
  }
} finally {
  try { socket?.close() } catch {}
  try { browser?.kill() } catch {}
  await sleep(300)
}
