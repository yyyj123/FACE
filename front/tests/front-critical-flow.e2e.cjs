const assert = require('assert')
const { chromium } = require(process.env.PLAYWRIGHT_CORE)

async function login(page) {
  await page.goto('http://127.0.0.1:8082/#/login', { waitUntil: 'networkidle', timeout: 60000 })
  const inputs = page.locator('input')
  await inputs.nth(0).fill('\u4f1a\u54581')
  await inputs.nth(1).fill('123')
  await page.locator('.login_btn').click()
  await page.waitForURL(/#\/index\/home$/, { timeout: 30000 })
}

async function run() {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'
  })

  try {
    const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 })
    page.on('pageerror', error => console.log('PAGE_ERROR', error.message))
    page.on('requestfailed', request => console.log('REQUEST_FAILED', request.url(), request.failure()?.errorText))
    page.on('response', response => {
      if (response.status() >= 400) console.log('HTTP_ERROR', response.status(), response.url())
    })
    page.on('console', message => {
      if (message.type() === 'error') console.log('CONSOLE_ERROR', message.text())
    })
    await login(page)

    await page.goto('http://127.0.0.1:8082/#/index/shouhoufuwu', { waitUntil: 'networkidle', timeout: 60000 })
    await page.locator('.list-preview').waitFor({ state: 'visible', timeout: 30000 })
    const categoryColors = await page.evaluate(() => {
      const color = selector => {
        const node = document.querySelector(selector)
        return node ? getComputedStyle(node).backgroundColor : null
      }
      return { select: color('.select2'), row: color('.select2-list'), active: color('.select2 .item.active') }
    })
    console.log('CATEGORY_COLORS', JSON.stringify(categoryColors))

    await page.getByRole('button', { name: '\u5728\u7ebf\u5ba2\u670d' }).click()
    await page.getByRole('dialog', { name: '\u5728\u7ebf\u54a8\u8be2' }).waitFor({ state: 'visible', timeout: 10000 })
    const message = `\u5ba2\u670d\u6d4b\u8bd5-${Date.now()}`
    await page.locator('.oc-chat-composer input').fill(message)
    console.log('COMPOSER', await page.locator('.oc-chat-composer').innerText(), await page.locator('.oc-chat-composer input').inputValue())
    console.log('SEND_BUTTON', await page.locator('.oc-chat-composer .oc-chat-send').evaluate(node => {
      const rect = node.getBoundingClientRect()
      node.addEventListener('click', () => console.log('DOM_SEND_CLICKED'), { once: true })
      return {
        html: node.outerHTML,
        disabled: node.disabled,
        rect: rect.toJSON(),
        hitTarget: document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2)?.outerHTML,
        pointerEvents: getComputedStyle(node).pointerEvents
      }
    }))
    const responsePromise = page.waitForResponse(response => response.url().includes('/chat/add'), { timeout: 8000 })
    await page.locator('.oc-chat-composer .oc-chat-send').click()
    const response = await responsePromise
    const body = await response.text()
    console.log('CHAT_RESPONSE', response.status(), body)
    assert(response.ok(), `chat request HTTP ${response.status()}: ${body}`)
    const payload = JSON.parse(body)
    assert.strictEqual(payload.code, 0, `chat API failed: ${body}`)
    await page.getByText(message, { exact: true }).waitFor({ state: 'visible', timeout: 10000 })
    await page.screenshot({ path: 'E:/FACE/.artifacts/preview/face-front-critical-flow.png', fullPage: false })
    console.log('PASS front category theme and customer-service message flow')
  } finally {
    await browser.close()
  }
}

run().catch(error => {
  console.error(error.stack || error)
  process.exit(1)
})
