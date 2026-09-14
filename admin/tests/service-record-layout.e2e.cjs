const assert = require('assert')
const { chromium } = require(process.env.PLAYWRIGHT_CORE)

async function run() {
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' })
  try {
    const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    page.on('response', response => {
      if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`)
    })

    await page.goto('http://127.0.0.1:8081/#/login', { waitUntil: 'networkidle', timeout: 60000 })
    await page.locator('#admin-username').fill('admin')
    await page.locator('#admin-password').fill('Face@123')
    await page.locator('.loginInBt').click()
    await page.waitForURL(/#\/$/, { timeout: 30000 })
    await page.goto('http://127.0.0.1:8081/#/weixiujilu', { waitUntil: 'networkidle', timeout: 60000 })
    await page.locator('.oc-admin-table').waitFor({ state: 'visible', timeout: 15000 })
    await page.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 15000 })

    const layout = await page.evaluate(() => {
      const toolbar = document.querySelector('.oc-admin-toolbar')
      const filters = document.querySelector('.oc-admin-filter')
      const actions = document.querySelector('.oc-admin-actions')
      const pagination = document.querySelector('.el-pagination')
      const table = document.querySelector('.oc-admin-table')
      const filterRect = filters.getBoundingClientRect()
      const actionsRect = actions.getBoundingClientRect()
      return {
        toolbarDirection: getComputedStyle(toolbar).flexDirection,
        actionsBelowFilters: actionsRect.top >= filterRect.bottom - 1,
        paginationBackground: getComputedStyle(pagination).backgroundColor,
        tableBackground: getComputedStyle(table).backgroundColor,
        loadingVisible: Boolean(document.querySelector('.el-loading-mask') && getComputedStyle(document.querySelector('.el-loading-mask')).display !== 'none')
      }
    })

    assert.strictEqual(layout.toolbarDirection, 'column', JSON.stringify(layout))
    assert.ok(layout.actionsBelowFilters, JSON.stringify(layout))
    assert.notStrictEqual(layout.paginationBackground, 'rgb(255, 255, 255)', JSON.stringify(layout))
    assert.strictEqual(layout.loadingVisible, false, JSON.stringify(layout))

    const dashboardResponse = page.waitForResponse(response => response.url().includes('/api/v1/management/dashboard'))
    await page.getByRole('button', { name: /日收入/ }).click()
    const chartResponse = await dashboardResponse
    assert.strictEqual(chartResponse.status(), 200, `dashboard status ${chartResponse.status()}`)
    await page.locator('#zongjiaChart1 canvas').waitFor({ state: 'visible', timeout: 10000 })
    await page.waitForTimeout(500)
    await page.screenshot({ path: 'E:/FACE/.artifacts/预览/face-admin-revenue-chart-fixed.png', fullPage: true })
    await page.locator('.el-dialog__wrapper:visible .el-dialog__headerbtn').click()
    assert.deepStrictEqual(errors, [], `browser errors: ${errors.join('\n')}`)

    await page.screenshot({ path: 'E:/FACE/.artifacts/预览/face-admin-service-record-fixed.png', fullPage: true })
    console.log(`PASS admin service-record layout ${JSON.stringify(layout)}`)
  } finally {
    await browser.close()
  }
}

run().catch(error => {
  console.error(error.stack || error)
  process.exit(1)
})
