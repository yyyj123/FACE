const assert = require('assert')
const { chromium } = require(process.env.PLAYWRIGHT_CORE)

async function expectExpandingButton(button, label) {
  await button.page().mouse.move(2, 2)
  await button.page().waitForTimeout(360)
  const rest = await button.evaluate(element => ({
    width: element.getBoundingClientRect().width,
    height: element.getBoundingClientRect().height,
    radius: getComputedStyle(element).borderRadius,
    transitionDuration: getComputedStyle(element).transitionDuration,
    transitionProperty: getComputedStyle(element).transitionProperty,
    className: element.className,
    inlineStyle: element.getAttribute('style') || ''
  }))
  assert.ok(rest.width >= 48 && rest.width <= 52, `${label} resting width is ${rest.width}`)
  assert.ok(rest.height >= 48 && rest.height <= 52, `${label} resting height is ${rest.height}`)
  assert.ok(parseFloat(rest.radius) >= 24, `${label} is not circular: ${rest.radius}; class=${rest.className}; style=${rest.inlineStyle}`)
  assert.ok(rest.transitionDuration.includes('0.3s'), `${label} transition is not 300ms: ${rest.transitionDuration}`)
  assert.ok(rest.transitionProperty.includes('width'), `${label} does not transition width: ${rest.transitionProperty}`)

  const box = await button.boundingBox()
  await button.page().mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  const frames = [await button.evaluate(element => ({
    width: element.getBoundingClientRect().width,
    hovered: element.matches(':hover'),
    x: element.getBoundingClientRect().x
  }))]
  for (const delay of [60, 60, 80, 180]) {
    await button.page().waitForTimeout(delay)
    frames.push(await button.evaluate(element => ({
      width: element.getBoundingClientRect().width,
      hovered: element.matches(':hover'),
      x: element.getBoundingClientRect().x
    })))
  }
  const hover = await button.evaluate(element => ({
    width: element.getBoundingClientRect().width,
    opacity: Number(getComputedStyle(element.querySelector(':scope > span')).opacity)
  }))
  const widths = frames.map(frame => frame.width)
  console.log(`${label} frames: ${frames.map(frame => `${Math.round(frame.width)}px@${Math.round(frame.x)}${frame.hovered ? '*' : ''}`).join(' -> ')}`)
  assert.ok(widths.some(width => width > 58 && width < 132), `${label} has no animated intermediate width: ${widths.join(', ')}`)
  assert.ok(frames.every(frame => frame.hovered), `${label} loses pointer hover while expanding`)
  assert.ok(hover.width >= 132, `${label} did not expand: ${hover.width}`)
  assert.ok(hover.opacity > 0.9, `${label} label is not visible: ${hover.opacity}`)
  console.log(`${label}: ${widths.map(width => Math.round(width)).join(' -> ')}px`)
}

async function run() {
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' })
  try {
    const admin = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    await admin.goto('http://127.0.0.1:8081/#/login', { waitUntil: 'networkidle', timeout: 60000 })
    await admin.locator('#admin-username').fill('admin')
    await admin.locator('#admin-password').fill('Face@123')
    await admin.locator('.loginInBt').click()
    await admin.waitForURL(/#\/$/, { timeout: 30000 })
    await admin.goto('http://127.0.0.1:8081/#/chezhu', { waitUntil: 'networkidle', timeout: 60000 })
    const adminButton = admin.locator('.el-button.search').first()
    await adminButton.waitFor({ state: 'visible', timeout: 30000 })
    await expectExpandingButton(adminButton, 'admin search button')
    await admin.screenshot({ path: 'E:/FACE/.artifacts/preview/face-expanding-buttons-admin.png', fullPage: false })

    const front = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    await front.goto('http://127.0.0.1:8082/#/login', { waitUntil: 'networkidle', timeout: 60000 })
    const frontButton = front.locator('.login_btn')
    await frontButton.waitFor({ state: 'visible', timeout: 30000 })
    const hasGlobalButtonCss = await front.evaluate(() => Array.from(document.styleSheets).some(sheet => {
      try { return Array.from(sheet.cssRules || []).some(rule => (rule.selectorText || '').includes('button.el-button.el-button')) } catch (_) { return false }
    }))
    assert.ok(hasGlobalButtonCss, 'customer dev server has not loaded expanding-buttons.scss')
    await expectExpandingButton(frontButton, 'customer login button')
    await front.screenshot({ path: 'E:/FACE/.artifacts/preview/face-expanding-buttons-front.png', fullPage: false })

    console.log('PASS expanding buttons work in admin and customer clients')
  } finally {
    await browser.close()
  }
}

run().catch(error => {
  console.error(error.stack || error)
  process.exit(1)
})
