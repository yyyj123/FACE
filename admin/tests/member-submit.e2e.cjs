const assert = require('assert')
const { chromium } = require(process.env.PLAYWRIGHT_CORE)

async function run() {
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    const responses = []
    page.on('response', response => {
      if (response.url().includes('/api/v1/management/members') && response.request().method() === 'POST') responses.push(response)
    })

    await page.goto('http://127.0.0.1:8081/#/login', { waitUntil: 'networkidle', timeout: 60000 })
    await page.locator('#admin-username').fill('admin')
    await page.locator('#admin-password').fill('Face@123')
    await page.locator('.loginInBt').click()
    await page.waitForURL(/#\/$/, { timeout: 30000 })
    await page.goto('http://127.0.0.1:8081/#/chezhu', { waitUntil: 'networkidle', timeout: 60000 })
    await page.locator('button.add').click()

    const form = page.locator('.add-update-preview')
    await form.waitFor({ state: 'visible' })
    const inputs = form.locator('input')
    const account = `动效测试${Date.now()}`
    await inputs.nth(0).fill(account)
    await inputs.nth(1).fill('123')
    await inputs.nth(2).fill('测试会员')
    await inputs.nth(4).fill('13554647890')

    const submit = form.locator('button.btn3')
    const rest = await submit.boundingBox()
    await page.mouse.move(rest.x + rest.width / 2, rest.y + rest.height / 2)
    await page.waitForTimeout(360)
    const hover = await submit.boundingBox()
    await page.mouse.click(hover.x + 12, hover.y + hover.height / 2)
    await page.waitForTimeout(1900)

    assert.strictEqual(responses.length, 1, `expected one member create request, got ${responses.length}`)
    const body = await responses[0].json()
    assert.strictEqual(body.code, 0, `save failed: ${JSON.stringify(body)}`)
    await page.locator('.tables').waitFor({ state: 'visible', timeout: 5000 })
    assert.ok(await page.getByText(account, { exact: true }).count(), 'saved member is not visible in the member list')
    const loginResponse = await page.request.post('http://127.0.0.1:8080/face/api/v1/auth/login', {
      data: { username: account, password: '123' }
    })
    const loginBody = await loginResponse.json()
    assert.strictEqual(loginBody.code, 0, `new member cannot log in: ${JSON.stringify(loginBody)}`)
    const token = await page.evaluate(() => JSON.parse(localStorage.getItem('Token')))
    const cleanupResponse = await page.request.post('http://127.0.0.1:8080/face/api/v1/management/members/delete', {
      headers: { Token: token },
      data: [body.data.id]
    })
    const cleanupBody = await cleanupResponse.json()
    assert.strictEqual(cleanupBody.code, 0, `test member cleanup failed: ${JSON.stringify(cleanupBody)}`)
    console.log(`PASS member submit saved ${account}, returned to the list, and the new account can log in`)
  } finally {
    await browser.close()
  }
}

run().catch(error => {
  console.error(error.stack || error)
  process.exit(1)
})
