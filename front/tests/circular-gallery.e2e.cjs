const assert = require('assert')
const { chromium } = require(process.env.PLAYWRIGHT_CORE)

async function run() {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'
  })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.goto('http://127.0.0.1:8082/#/login', { waitUntil: 'networkidle', timeout: 60000 })
    const inputs = page.locator('input')
    await inputs.nth(0).fill('\u4f1a\u54581')
    await inputs.nth(1).fill('123')
    await page.locator('.login_btn').click()
    await page.waitForURL(/#\/index\/home$/, { timeout: 30000 })
    await page.locator('.circular-gallery canvas').first().waitFor({ state: 'visible', timeout: 30000 })
    await page.waitForTimeout(1500)

    assert.strictEqual(await page.locator('.circular-gallery').count(), 3, 'expected three recommendation galleries')
    assert.strictEqual(await page.locator('.circular-gallery canvas').count(), 3, 'expected one WebGL canvas per gallery')
    const unexpectedErrors = errors.filter(message => message !== 'AMap is not defined' && message !== 'Response')
    assert.deepStrictEqual(unexpectedErrors, [], `unexpected page errors: ${unexpectedErrors.join('; ')}`)

    await page.locator('#animate_recommendxinnengyuanqiche').scrollIntoViewIfNeeded()
    await page.screenshot({ path: 'E:/FACE/.artifacts/preview/face-circular-gallery.png', fullPage: false })
    await page.locator('.circular-gallery').first().focus()
    await page.keyboard.press('ArrowRight')
    await page.keyboard.press('Enter')
    await page.waitForURL(/#\/index\/xinnengyuanqicheDetail\?id=/, { timeout: 10000 })
    console.log('PASS circular galleries render without browser errors')
  } finally {
    await browser.close()
  }
}

run().catch(error => {
  console.error(error.stack || error)
  process.exit(1)
})
