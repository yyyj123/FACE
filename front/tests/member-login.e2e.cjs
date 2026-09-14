const assert = require('assert')
const { chromium } = require(process.env.PLAYWRIGHT_CORE)

async function run() {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'
  })

  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    await page.goto('http://127.0.0.1:8082/#/login', { waitUntil: 'networkidle', timeout: 60000 })
    const inputs = page.locator('input')
    await inputs.nth(0).fill('会员1')
    await inputs.nth(1).fill('123')
    await page.locator('.login_btn').click()
    await page.waitForTimeout(3500)

    assert.match(page.url(), /#\/index\/home$/, `会员登录后仍停留在 ${page.url()}`)
    assert.ok(await page.evaluate(() => localStorage.getItem('frontToken')), '登录后没有保存 frontToken')
    assert.strictEqual(await page.locator('.oc-hero__image img').count(), 3, '会员首页轮播未正常渲染')
    console.log('PASS member login reaches the salon home page')
  } finally {
    await browser.close()
  }
}

run().catch(error => {
  console.error(error.stack || error)
  process.exit(1)
})
