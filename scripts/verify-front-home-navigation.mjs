import { spawn } from 'node:child_process'
import fs from 'node:fs'
import WebSocket from '../admin/node_modules/ws/index.js'

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const previewDir = 'E:/FACE/.artifacts/预览/FACE-首页返回问题'
const profileDir = 'E:/face/.runtime/front-home-navigation-profile'
const port = 9241
const pageUrl = `http://127.0.0.1:8082/?qa=${Date.now()}#/index/home`
let browser
let socket
let requestId = 0
const pending = new Map()
const runtimeErrors = []

const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++requestId
  pending.set(id, { resolve, reject })
  socket.send(JSON.stringify({ id, method, params }))
})

const evaluate = async expression => {
  const response = await send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true
  })
  if (response.exceptionDetails) throw new Error(response.exceptionDetails.text)
  if (!response.result) return null
  return response.result.result.value
}

const waitFor = async (expression, timeout = 15000) => {
  const endAt = Date.now() + timeout
  while (Date.now() < endAt) {
    const value = await evaluate(expression)
    if (value) return value
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
      pending.get(message.id).resolve(message)
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

  await waitFor(`document.querySelector('.oc-nav__item') && document.body.scrollHeight > innerHeight`)
  const initialState = await evaluate(`({
    hash: location.hash,
    heroVisible: !!document.querySelector('.oc-hero'),
    routeViewClass: document.querySelector('#scrollView')?.className || '',
    routeViewText: document.querySelector('#scrollView')?.innerText?.trim().slice(0, 60) || ''
  })`)
  await evaluate(`window.scrollTo(0, Math.min(1200, document.body.scrollHeight - innerHeight))`)
  await sleep(200)
  const beforeSameRouteClick = await evaluate(`({ hash: location.hash, scrollY: Math.round(window.scrollY) })`)
  await evaluate(`(() => {
    const home = [...document.querySelectorAll('.oc-nav__item')].find(item => item.textContent.trim() === '\u9996\u9875')
    if (!home) throw new Error('Home navigation button not found')
    home.click()
    return true
  })()`)
  await sleep(300)
  const afterSameRouteClick = await evaluate(`({ hash: location.hash, scrollY: Math.round(window.scrollY) })`)

  await evaluate(`location.hash = '#/index/xinnengyuanqiche'`)
  await waitFor(`location.hash.startsWith('#/index/xinnengyuanqiche')`)
  await sleep(500)
  await evaluate(`(() => {
    const home = [...document.querySelectorAll('.oc-nav__item')].find(item => item.textContent.trim() === '\u9996\u9875')
    if (!home) throw new Error('Home navigation button not found')
    home.click()
    return true
  })()`)
  try {
    await waitFor(`!!(location.hash === '#/index/home' && document.querySelector('.oc-hero') && document.querySelector('#scrollView.home-preview'))`)
  } catch (error) {
    const diagnosticState = await evaluate(`({
      href: location.href,
      hash: location.hash,
      readyState: document.readyState,
      heroVisible: !!document.querySelector('.oc-hero'),
      navLabels: [...document.querySelectorAll('.oc-nav__item')].map(item => item.textContent.trim()),
      routeViewClass: document.querySelector('#scrollView')?.className || '',
      routeViewText: document.querySelector('#scrollView')?.innerText?.trim().slice(0, 80) || ''
    })`)
    console.error(JSON.stringify({ diagnosticState, runtimeErrors }, null, 2))
    throw error
  }
  await sleep(200)
  const afterOtherRouteClick = await evaluate(`({
    hash: location.hash,
    scrollY: Math.round(window.scrollY),
    heroVisible: !!document.querySelector('.oc-hero'),
    appRoute: document.querySelector('#app')?.__vue__?.$route?.path || '',
    shellRoute: document.querySelector('.main-containers')?.__vue__?.$route?.path || '',
    routeViewClass: document.querySelector('#scrollView')?.firstElementChild?.className || '',
    routeViewText: document.querySelector('#scrollView')?.innerText?.trim().slice(0, 80) || '',
    shellIsHomePage: document.querySelector('.main-containers')?.__vue__?.isHomePage
  })`)

  const screenshot = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: false
  })
  fs.writeFileSync(
    `${previewDir}/修复后-首页返回.png`,
    Buffer.from(screenshot.result.data, 'base64')
  )

  const result = {
    initialState,
    beforeSameRouteClick,
    afterSameRouteClick,
    afterOtherRouteClick,
    runtimeErrors,
    sameRouteReturnedToTop: beforeSameRouteClick.scrollY > 0 && afterSameRouteClick.scrollY === 0,
    otherRouteReturnedHome: afterOtherRouteClick.hash === '#/index/home'
      && afterOtherRouteClick.scrollY === 0
      && afterOtherRouteClick.heroVisible
  }
  console.log(JSON.stringify(result, null, 2))
  if (!result.otherRouteReturnedHome) process.exitCode = 1
} finally {
  try { socket?.close() } catch {}
  try { browser?.kill() } catch {}
  await sleep(300)
}
