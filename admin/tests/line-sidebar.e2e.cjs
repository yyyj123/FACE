const assert = require('assert')
const { chromium } = require(process.env.PLAYWRIGHT_CORE)

async function run() {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'
  })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    await page.goto('http://127.0.0.1:8081/#/login', { waitUntil: 'networkidle', timeout: 60000 })
    await page.locator('#admin-username').fill('admin')
    await page.locator('#admin-password').fill('Face@123')
    await page.locator('.loginInBt').click()
    await page.waitForURL(/#\/$/, { timeout: 30000 })
    await page.locator('.line-sidebar-shell').waitFor({ state: 'visible', timeout: 30000 })

    const items = page.locator('.line-sidebar > .line-sidebar__item')
    assert.ok(await items.count() >= 6, 'expected top-level admin navigation items')
    assert.strictEqual(await page.locator('.line-sidebar__marker').count(), await items.count(), 'each top-level item needs a marker')

    await items.nth(3).hover()
    await page.waitForTimeout(250)
    const effect = Number(await items.nth(3).evaluate(element => element.style.getPropertyValue('--effect')))
    assert.ok(effect > 0.5, `proximity effect did not activate: ${effect}`)

    await page.screenshot({ path: 'E:/FACE/.artifacts/preview/face-admin-line-sidebar.png', fullPage: false })

    await page.locator('.line-sidebar-toggle').click()
    await page.waitForTimeout(350)
    const collapsedWidth = await page.locator('.line-sidebar-shell').evaluate(element => element.getBoundingClientRect().width)
    assert.ok(collapsedWidth < 100, `collapsed sidebar is still too wide: ${collapsedWidth}`)
    assert.strictEqual(await page.locator('.line-sidebar__index').first().evaluate(element => getComputedStyle(element).display), 'none')
    console.log('PASS admin LineSidebar renders and responds to pointer proximity')
  } finally {
    await browser.close()
  }
}

run().catch(error => {
  console.error(error.stack || error)
  process.exit(1)
})
