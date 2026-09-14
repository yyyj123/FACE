const assert = require('assert')
const { chromium } = require(process.env.PLAYWRIGHT_CORE)

const isWhite = color => /rgba?\(\s*255\s*,\s*255\s*,\s*255(?:\s*,\s*1)?\s*\)/i.test(color)

async function run() {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe'
  })

  try {
    const page = await browser.newPage({ viewport: { width: 1920, height: 1000 }, deviceScaleFactor: 1 })
    await page.goto('http://127.0.0.1:8082/#/login', { waitUntil: 'networkidle', timeout: 60000 })
    const inputs = page.locator('input')
    await inputs.nth(0).fill('\u4f1a\u54581')
    await inputs.nth(1).fill('123')
    await page.locator('.login_btn').click()
    await page.waitForURL(/#\/index\/home$/, { timeout: 30000 })
    await page.goto('http://127.0.0.1:8082/#/index/xinnengyuanqiche', { waitUntil: 'networkidle', timeout: 60000 })
    await page.locator('.list-preview').waitFor({ state: 'visible', timeout: 30000 })
    await page.waitForTimeout(800)

    const result = await page.evaluate(() => {
      const background = selector => {
        const node = document.querySelector(selector)
        return node ? getComputedStyle(node).backgroundColor : null
      }
      const rect = selector => document.querySelector(selector)?.getBoundingClientRect()
      const label = rect('.list-form-pv .list-item .lable')
      const input = rect('.list-form-pv .list-item .el-input__inner')
      const pagination = document.querySelector('.pagination')
      const pageChildren = pagination ? [...pagination.children].map(node => node.getBoundingClientRect()) : []

      return {
        selectBackground: background('.select2'),
        sortBackground: background('.sort_view'),
        listBackground: background('.list'),
        emptyBackground: background('.oc-empty'),
        paginationBackground: background('.pagination'),
        labelCenter: label ? label.y + label.height / 2 : null,
        inputCenter: input ? input.y + input.height / 2 : null,
        paginationCenterSpread: pageChildren.length
          ? Math.max(...pageChildren.map(box => box.y + box.height / 2)) - Math.min(...pageChildren.map(box => box.y + box.height / 2))
          : null
      }
    })

    for (const [name, color] of Object.entries(result).filter(([key]) => key.endsWith('Background'))) {
      assert(color, `${name} was not found`)
      assert(!isWhite(color), `${name} remains white: ${color}`)
    }
    assert(Math.abs(result.labelCenter - result.inputCenter) <= 2, `filter label/input are not centered: ${JSON.stringify(result)}`)
    assert(result.paginationCenterSpread <= 2, `pagination controls are not aligned: ${JSON.stringify(result)}`)

    await page.screenshot({ path: 'E:/FACE/.artifacts/preview/face-user-list-theme.png', fullPage: true })
    console.log('PASS user list theme, form alignment and pagination alignment')
  } finally {
    await browser.close()
  }
}

run().catch(error => {
  console.error(error.stack || error)
  process.exit(1)
})
