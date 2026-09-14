import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import WebSocket from '../admin/node_modules/ws/index.js'

const baseUrl = (process.env.FACE_PUBLIC_URL || 'https://nav-water-layout-shore.trycloudflare.com').replace(/\/$/, '')
const previewDir = 'E:/FACE/.artifacts/预览/FACE-公网修复验收'
const profileDir = 'E:/face/.runtime/public-client-hotfix-profile'
const debugPort = 9254
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
let browser
let socket
let nextId = 0
const pending = new Map()
const runtimeErrors = []
const apiFailures = []

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

async function waitFor(expression, timeout = 20000) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    if (await evaluate(expression)) return
    await sleep(250)
  }
  throw new Error(`Timed out waiting for: ${expression}`)
}

async function screenshot(filename) {
  const result = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  fs.writeFileSync(path.join(previewDir, filename), Buffer.from(result.result.data, 'base64'))
}

try {
  fs.mkdirSync(previewDir, { recursive: true })
  fs.mkdirSync(profileDir, { recursive: true })
  browser = spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
    '--headless=new',
    '--use-angle=swiftshader',
    '--enable-webgl',
    '--no-first-run',
    '--window-size=1440,1000',
    `--remote-debugging-port=${debugPort}`,
    `--user-data-dir=${profileDir}`,
    baseUrl,
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
    if (message.method === 'Runtime.exceptionThrown') {
      runtimeErrors.push(message.params.exceptionDetails.text)
    }
    if (message.method === 'Network.responseReceived') {
      const { response } = message.params
      if (response.url.includes('/face-next/api/') && response.status >= 400) {
        apiFailures.push({ status: response.status, url: response.url })
      }
    }
  })

  await send('Runtime.enable')
  await send('Page.enable')
  await send('Network.enable')
  await waitFor(`document.body.innerText.includes('本店热门护理项目')`)
  await waitFor(`document.querySelectorAll('.gallery-stage').length === 2`)
  const homeState = await evaluate(`({
    hasGenericError: document.body.innerText.includes('Request failed with status code'),
    notices: [...document.querySelectorAll('.notice')].map((node) => node.textContent.trim()),
    galleryCount: document.querySelectorAll('.gallery-stage').length
  })`)
  await evaluate(`document.querySelector('.gallery-showcase').scrollIntoView({ block: 'start' })`)
  await sleep(800)
  await screenshot('01-首页项目与技师.png')

  await send('Page.navigate', { url: `${baseUrl}/login` })
  await waitFor(`document.body.innerText.includes('会员注册')`)
  await evaluate(`[...document.querySelectorAll('.mode-tabs button')].find((button) => button.textContent.includes('会员注册')).click()`)
  await waitFor(`!!document.querySelector('#register-phone')`)
  const formState = await evaluate(`(() => {
    const username = document.querySelector('#register-username')
    const phone = document.querySelector('#register-phone')
    username.value = 'dd'
    phone.value = 'dd'
    username.dispatchEvent(new Event('input', { bubbles: true }))
    phone.dispatchEvent(new Event('input', { bubbles: true }))
    return {
      usernameValid: username.checkValidity(),
      phoneValid: phone.checkValidity(),
      usernameMinLength: username.minLength,
      phonePattern: phone.pattern
    }
  })()`)
  await screenshot('02-会员注册校验.png')

  if (homeState.hasGenericError || homeState.notices.length > 0 || homeState.galleryCount !== 2) {
    throw new Error(`Home page acceptance failed: ${JSON.stringify(homeState)}`)
  }
  if (formState.phoneValid || formState.usernameMinLength !== 3 || formState.phonePattern !== '1[3-9][0-9]{9}') {
    throw new Error(`Registration validation acceptance failed: ${JSON.stringify(formState)}`)
  }
  if (apiFailures.length > 0 || runtimeErrors.length > 0) {
    throw new Error(`Browser errors: ${JSON.stringify({ apiFailures, runtimeErrors })}`)
  }

  console.log(`PUBLIC_BROWSER_HOME=${JSON.stringify(homeState)}`)
  console.log(`PUBLIC_BROWSER_REGISTER_VALIDATION=${JSON.stringify(formState)}`)
  console.log('PUBLIC_BROWSER_ACCEPTANCE=PASS')
  console.log(`PUBLIC_BROWSER_PREVIEW=${previewDir}`)
} finally {
  if (socket) socket.close()
  if (browser) browser.kill()
}
