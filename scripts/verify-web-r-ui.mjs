import { mkdirSync, writeFileSync } from 'node:fs'
import { spawn } from 'node:child_process'
import WebSocket from '../front/node_modules/ws/index.js'

const options = Object.fromEntries(process.argv.slice(2).map((entry) => {
  const [key, ...rest] = entry.replace(/^--/, '').split('=')
  return [key, rest.join('=')]
}))
const { url, output } = options
const previews = options.previews ?? process.env.FACE_WEB_R_PREVIEWS
const gatePassword = options['gate-password']
const accountPassword = options['account-password']
const run = Number(options.run ?? 1)
const smoke = options.smoke === 'true'
if (!url || !gatePassword || !accountPassword || !output || !previews) {
  throw new Error('WEB-R browser check requires url, gate-password, account-password, output and previews.')
}

mkdirSync(output, { recursive: true })
mkdirSync(previews, { recursive: true })
const debuggingPort = 9360 + run
const edgeDirectory = options['edge-dir'] ?? `${output}/edge`
const edge = spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
  '--headless=new', `--remote-debugging-port=${debuggingPort}`, '--disable-gpu', '--no-first-run',
  `--user-data-dir=${edgeDirectory}`, url,
], { windowsHide: true, stdio: 'ignore' })

const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))
const assert = (condition, message) => { if (!condition) throw new Error(message) }
let socket

try {
  let tabs
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try { tabs = await (await fetch(`http://127.0.0.1:${debuggingPort}/json`)).json(); break } catch { await sleep(250) }
  }
  assert(tabs, 'Edge debugging endpoint did not start.')
  const tab = tabs.find((item) => item.type === 'page')
  socket = new WebSocket(tab.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => { socket.once('open', resolve); socket.once('error', reject) })

  let requestId = 0
  const pending = new Map()
  const severeEvents = []
  const ignoredExtensionEvents = []
  socket.on('message', (raw) => {
    const message = JSON.parse(raw)
    if (message.id && pending.has(message.id)) { pending.get(message.id)(message); pending.delete(message.id); return }
    if (message.method === 'Runtime.exceptionThrown') {
      const description = message.params?.exceptionDetails?.exception?.description ?? ''
      if (description.includes('chrome-extension://')) ignoredExtensionEvents.push(message)
      else severeEvents.push(message)
    }
    if (message.method === 'Network.responseReceived' && message.params?.response?.status >= 500) severeEvents.push(message)
    if (message.method === 'Network.loadingFailed' && !message.params?.canceled) severeEvents.push(message)
  })
  const send = (method, params = {}) => new Promise((resolve) => {
    const id = ++requestId
    pending.set(id, resolve)
    socket.send(JSON.stringify({ id, method, params }))
  })
  const evaluate = async (expression) => {
    const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
    if (result.result.exceptionDetails) throw new Error(result.result.exceptionDetails.text)
    return result.result.result.value
  }
  const waitFor = async (expression, message, attempts = 30) => {
    for (let attempt = 0; attempt < attempts; attempt += 1) { if (await evaluate(expression)) return; await sleep(500) }
    throw new Error(message)
  }
  const navigate = async (path, readySelector = 'main') => {
    for (let attempt = 1; attempt <= 3; attempt += 1) {
      await send('Page.navigate', { url: `${url}${path}` })
      try {
        await waitFor(`document.readyState === 'complete' && Boolean(document.querySelector(${JSON.stringify(readySelector)}))`, `Page did not become ready: ${path}`, 30)
        await sleep(600)
        return
      } catch (error) {
        if (attempt === 3) throw error
        await sleep(500)
      }
    }
  }
  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')
  await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] })
  const gateResponse = await fetch(`${url}/demo/session`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ password: gatePassword }),
  })
  const setCookie = gateResponse.headers.get('set-cookie') ?? ''
  assert(gateResponse.ok && setCookie.startsWith('face_demo_access='), `Demo gate API failed: ${gateResponse.status}`)
  const cookiePair = setCookie.split(';', 1)[0]
  const cookieValue = cookiePair.slice(cookiePair.indexOf('=') + 1)
  const secureOrigin = new URL(url).protocol === 'https:'
  const cookieResult = await send('Network.setCookie', { name: 'face_demo_access', value: cookieValue, url, secure: secureOrigin, httpOnly: true, sameSite: 'Lax' })
  assert(cookieResult.result.success, 'Browser rejected the secure demo cookie.')
  await send('Page.navigate', { url })
  await waitFor(`Boolean(document.querySelector('#gate-form'))`, 'Public demo gate did not become ready.', 60)
  await waitFor(`document.querySelector('.entry')?.getAttribute('aria-disabled') === 'false'`, 'Browser gate authorization failed.')
  const cookies = await send('Network.getAllCookies')
  assert(cookies.result.cookies.some((cookie) => cookie.name === 'face_demo_access' && cookie.httpOnly && cookie.secure === secureOrigin), 'Browser demo cookie is missing or has the wrong local transport flag.')

  const login = async (path, body) => {
    const response = await fetch(`${url}${path}`, { method: 'POST', headers: { Cookie: cookiePair, 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    const text = await response.text()
    let payload
    try { payload = JSON.parse(text) } catch { throw new Error(`${path} returned non-JSON ${response.status}: ${text.slice(0, 120)}`) }
    assert(response.ok && payload.data, `${path} login failed: ${response.status} ${payload.message ?? ''}`)
    return payload.data
  }
  const identity = await login('/api/v3/client/identity/password-login', { phone: '13900000001', password: accountPassword })
  const memberSession = { token: identity.access_token, accountId: identity.account_id, shopId: identity.shop_id,
    username: '13900000001', role: identity.role, memberId: identity.member_id, v3AccessToken: identity.access_token,
    v3RefreshToken: identity.refresh_token }
  await evaluate(`localStorage.setItem('face-client-session-v2', ${JSON.stringify(JSON.stringify(memberSession))}); true`)
  await navigate('/client/', '.client-shell')

  const adminSession = await login('/api/v3/auth/admin-login', { username: 'admin', password: accountPassword })
  await evaluate(`localStorage.setItem('face-chain-token', ${JSON.stringify(adminSession.access_token)});
    localStorage.setItem('face-chain-v3-access-token', ${JSON.stringify(adminSession.access_token)});
    localStorage.setItem('face-chain-v3-refresh-token', ${JSON.stringify(adminSession.refresh_token)}); true`)
  await navigate('/admin/', '.app-shell')

  const completeViewports = [
    { name: 'mobile-portrait', width: 390, height: 844, mobile: true },
    { name: 'tablet-portrait', width: 768, height: 1024, mobile: true },
    { name: 'desktop', width: 1440, height: 960, mobile: false },
    { name: 'mobile-landscape', width: 844, height: 390, mobile: true },
  ]
  const viewports = smoke ? completeViewports.filter((item) => ['mobile-portrait', 'desktop'].includes(item.name)) : completeViewports
  const clientRoutes = smoke ? [''] : ['', 'services', 'booking', 'appointments', 'benefits', 'points-store', 'care-feedback', 'profile']
  const adminRoutes = smoke ? [''] : ['', 'workbench', 'booking-operations', 'fulfillment', 'member-operations']
  const results = { run, mode: smoke ? 'restart-smoke' : 'full-matrix', url, viewports: {}, severeEventCount: 0, ignoredExtensionEventCount: 0 }

  for (const viewport of viewports) {
    await send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height, deviceScaleFactor: viewport.mobile ? 2 : 1, mobile: viewport.mobile })
    const viewportResult = { width: viewport.width, height: viewport.height, client: {}, admin: {} }
    results.viewports[viewport.name] = viewportResult

    for (const route of clientRoutes) {
      await navigate(`/client/${route}`, '#main-content')
      await waitFor(`(document.querySelector('#main-content')?.innerText.trim().length ?? 0) > 0`, `${viewport.name} client ${route || 'home'} remained empty.`, 40)
      const state = await evaluate(`(() => {
        const main = document.querySelector('#main-content'); const mobile = matchMedia('(max-width: 900px)').matches;
        const mobileNav = document.querySelector('.mobile-nav'); const desktopNav = document.querySelector('.desktop-nav');
        const activeNav = mobile ? mobileNav : desktopNav;
        const targets = [...(activeNav?.querySelectorAll('a') ?? [])].filter((item) => getComputedStyle(item).display !== 'none');
        const overflow = document.documentElement.scrollWidth <= innerWidth + 1;
        const overflowDetails = overflow ? [] : [...document.querySelectorAll('body *')]
          .map((element) => { const rect = element.getBoundingClientRect(); return { tag: element.tagName, className: String(element.className ?? '').slice(0, 120), left: Math.round(rect.left), right: Math.round(rect.right), width: Math.round(rect.width) } })
          .filter((item) => item.left < -1 || item.right > innerWidth + 1)
          .sort((a, b) => (b.right - innerWidth) - (a.right - innerWidth)).slice(0, 8);
        return { title: document.title, overflow,
          viewportWidth: innerWidth, documentWidth: document.documentElement.scrollWidth, overflowDetails,
          mainTarget: main?.getAttribute('tabindex') === '-1', mainFocused: document.activeElement === main,
          skipTarget: document.querySelector('.skip-link')?.getAttribute('href') === '#main-content',
          correctNavigation: mobile ? getComputedStyle(mobileNav).display === 'grid' : getComputedStyle(desktopNav).display !== 'none',
          touchTargets: mobile ? targets.every((item) => item.getBoundingClientRect().height >= 44) : true,
          frozenTechnicianEntry: document.body.innerText.includes('技师工作台'), body: main?.innerText.slice(0, 100) ?? '' };
      })()`)
      assert(state.overflow, `${viewport.name} client ${route || 'home'} overflows horizontally.`)
      assert(state.mainTarget && state.skipTarget && state.correctNavigation && state.touchTargets, `${viewport.name} client ${route || 'home'} failed accessibility/navigation contract: ${JSON.stringify(state)}`)
      assert(!state.frozenTechnicianEntry && state.body.trim(), `${viewport.name} client ${route || 'home'} exposed frozen scope or empty content: ${JSON.stringify(state)}`)
      if (route === '') {
        await waitFor(`document.querySelectorAll('.staff-card').length > 0`, `${viewport.name} home staff cards did not load.`, 40)
        const firstStaffPage = await evaluate(`(() => ({
          cardCount: document.querySelectorAll('.staff-card').length,
          cardWidths: [...document.querySelectorAll('.staff-card')].map((card) => Math.round(card.getBoundingClientRect().width)),
          pageCount: document.querySelectorAll('.staff-page-numbers button').length,
          currentPage: document.querySelector('.staff-page-numbers button[aria-current="page"]')?.textContent.trim(),
          nextDisabled: document.querySelector('.staff-page-control:last-child')?.disabled,
        }))()`)
        assert(firstStaffPage.cardCount === 5 && firstStaffPage.currentPage === '1', `${viewport.name} home did not show exactly five technicians on page 1: ${JSON.stringify(firstStaffPage)}`)
        if (firstStaffPage.pageCount > 1) {
          await evaluate(`document.querySelector('.staff-page-control:last-child')?.click(); true`)
          await waitFor(`document.querySelector('.staff-page-numbers button[aria-current="page"]')?.textContent.trim() === '2'`, `${viewport.name} home staff page 2 did not activate.`)
          const secondStaffPage = await evaluate(`(() => ({
            cardCount: document.querySelectorAll('.staff-card').length,
            cardWidths: [...document.querySelectorAll('.staff-card')].map((card) => Math.round(card.getBoundingClientRect().width)),
            currentPage: document.querySelector('.staff-page-numbers button[aria-current="page"]')?.textContent.trim(),
            nextDisabled: document.querySelector('.staff-page-control:last-child')?.disabled,
          }))()`)
          assert(secondStaffPage.cardCount > 0 && secondStaffPage.cardCount <= 5 && secondStaffPage.currentPage === '2', `${viewport.name} home staff page 2 is invalid: ${JSON.stringify(secondStaffPage)}`)
          assert(secondStaffPage.cardWidths.every((width) => Math.abs(width - firstStaffPage.cardWidths[0]) <= 1), `${viewport.name} home staff cards changed width between pages: ${JSON.stringify({ firstStaffPage, secondStaffPage })}`)
          await evaluate(`document.querySelector('.staff-page-numbers button:first-child')?.click(); true`)
          await waitFor(`document.querySelector('.staff-page-numbers button[aria-current="page"]')?.textContent.trim() === '1'`, `${viewport.name} home staff page 1 did not restore.`)
          state.staffPagination = { first: firstStaffPage, second: secondStaffPage }
        }
      }
      if (route === 'booking') {
        await waitFor(`Boolean(document.querySelector('.service-picker-trigger'))`, `${viewport.name} booking service picker did not load.`, 40)
        await evaluate(`document.querySelector('.service-picker-trigger')?.click(); true`)
        await waitFor(`document.querySelectorAll('.service-picker-categories button').length > 0 && document.querySelectorAll('.service-picker-option').length > 0`, `${viewport.name} booking category picker did not open.`, 40)
        const servicePicker = await evaluate(`(() => {
          const panel = document.querySelector('.service-picker-panel')
          const rect = panel?.getBoundingClientRect()
          return {
            categoryLabels: [...document.querySelectorAll('.service-picker-categories button')].map((item) => item.textContent.trim()),
            optionCount: document.querySelectorAll('.service-picker-option').length,
            expanded: document.querySelector('.service-picker-trigger')?.getAttribute('aria-expanded'),
            panelHeight: rect ? Math.round(rect.height) : 0,
          }
        })()`)
        assert(servicePicker.categoryLabels.length > 1 && servicePicker.optionCount > 0 && servicePicker.expanded === 'true', `${viewport.name} booking service picker is not categorized: ${JSON.stringify(servicePicker)}`)
        const pickerShot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
        writeFileSync(`${previews}/run-${run}-${viewport.name}-booking-picker.png`, Buffer.from(pickerShot.result.data, 'base64'))
        await evaluate(`document.querySelector('.service-picker-option')?.click(); true`)
        await waitFor(`document.querySelectorAll('.date-tabs button').length === 7`, `${viewport.name} booking availability did not load.`, 40)
        await waitFor(`Boolean(document.querySelector('.terms-card li'))`, `${viewport.name} booking rule summary did not render.`, 40)
        const bookingRule = await evaluate(`(() => ({
          intervalText: document.querySelector('.terms-card li')?.textContent.trim() ?? '',
          durationText: [...document.querySelectorAll('.booking-summary dd')].map((item) => item.textContent.trim()).find((value) => value.endsWith('分钟')) ?? '',
          holdButtonText: document.querySelector('.submit-button')?.textContent.trim() ?? '',
        }))()`)
        assert(bookingRule.intervalText.includes('每 1 小时开放一个预约开始时间'), `${viewport.name} booking rule is not displayed in hours: ${JSON.stringify(bookingRule)}`)
        assert(bookingRule.holdButtonText.includes('暂时保留所选时段 15 分钟'), `${viewport.name} booking hold duration is ambiguous: ${JSON.stringify(bookingRule)}`)
        const selectedAvailableDate = await evaluate(`(() => {
          const button = [...document.querySelectorAll('.date-tabs button')].find((item) => {
            const count = Number.parseInt(item.querySelector('span')?.textContent ?? '0', 10)
            return count > 0
          })
          button?.click()
          return Boolean(button)
        })()`)
        assert(selectedAvailableDate, `${viewport.name} booking did not provide an available date for hourly verification.`)
        await waitFor(`document.querySelectorAll('.time-slots button').length > 0`, `${viewport.name} booking hourly slots did not render.`, 40)
        const hourlyBooking = await evaluate(`(() => {
          const times = [...document.querySelectorAll('.time-slots button strong')].map((item) => item.textContent.trim())
          return {
            times,
            onlyWholeHours: times.length > 0 && times.every((value) => value.endsWith(':00')),
            intervalCopy: document.querySelector('.terms-card li')?.textContent.trim() ?? '',
          }
        })()`)
        assert(hourlyBooking.onlyWholeHours && hourlyBooking.intervalCopy.includes('每 1 小时开放一个预约开始时间'), `${viewport.name} booking exposed non-hourly starts: ${JSON.stringify(hourlyBooking)}`)
        state.servicePicker = servicePicker
        state.hourlyBooking = { ...hourlyBooking, ...bookingRule }
      }
      if (route === 'points-store') {
        await waitFor(`document.querySelectorAll('.product-card').length > 0`, `${viewport.name} mall products did not load.`, 40)
        const mallList = await evaluate(`(() => ({
          cardCount: document.querySelectorAll('.product-card').length,
          mainPriceCount: document.querySelectorAll('.product-main-price').length,
          modeTriggerCount: document.querySelectorAll('.purchase-mode-trigger').length,
          exposesSkuCode: [...document.querySelectorAll('.product-card')].some((card) => /DEMO-SKU|1231/.test(card.innerText)),
          firstProductName: document.querySelector('.product-title')?.textContent.trim() ?? '',
        }))()`)
        assert(mallList.cardCount > 0 && mallList.mainPriceCount > 0 && mallList.modeTriggerCount > 0 && !mallList.exposesSkuCode, `${viewport.name} mall list price disclosure is invalid: ${JSON.stringify(mallList)}`)

        await evaluate(`document.querySelector('.purchase-mode-trigger')?.click(); true`)
        await waitFor(`document.querySelectorAll('.purchase-mode-popover > button').length > 1`, `${viewport.name} mall purchase-mode popover did not open.`)
        const popover = await evaluate(`(() => ({
          optionCount: document.querySelectorAll('.purchase-mode-popover > button').length,
          labels: [...document.querySelectorAll('.purchase-mode-popover > button')].map((button) => button.innerText.trim()),
          overflow: document.documentElement.scrollWidth <= innerWidth + 1,
        }))()`)
        assert(popover.optionCount > 1 && popover.overflow, `${viewport.name} mall purchase-mode popover is invalid: ${JSON.stringify(popover)}`)

        await evaluate(`document.querySelector('.product-title')?.click(); true`)
        await waitFor(`Boolean(document.querySelector('.product-detail-panel'))`, `${viewport.name} mall product detail did not open.`)
        await waitFor(`Boolean(document.querySelector('.detail-image')?.complete && document.querySelector('.detail-image')?.naturalWidth > 0)`, `${viewport.name} mall product detail image did not load.`, 40)
        const detailBefore = await evaluate(`(() => ({
          choiceCount: document.querySelectorAll('.purchase-choice-list > button').length,
          selectedCount: document.querySelectorAll('.purchase-choice-list > button.active').length,
          actionLabel: document.querySelector('.detail-purchase-bar .button')?.textContent.trim() ?? '',
          skuLabels: [...document.querySelectorAll('.sku-selector > button')].map((button) => button.textContent.trim()),
          exposesSkuCode: /DEMO-SKU|1231/.test(document.querySelector('.product-detail-panel')?.innerText ?? ''),
        }))()`)
        assert(detailBefore.choiceCount > 0 && detailBefore.selectedCount === 1 && detailBefore.actionLabel && !detailBefore.exposesSkuCode, `${viewport.name} mall product detail is invalid: ${JSON.stringify(detailBefore)}`)
        if (detailBefore.choiceCount > 1) {
          await evaluate(`document.querySelectorAll('.purchase-choice-list > button')[1]?.click(); true`)
          await waitFor(`document.querySelectorAll('.purchase-choice-list > button')[1]?.classList.contains('active')`, `${viewport.name} mall purchase mode did not switch.`)
        }
        const detailShot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
        writeFileSync(`${previews}/run-${run}-${viewport.name}-mall-detail.png`, Buffer.from(detailShot.result.data, 'base64'))

        await evaluate(`document.querySelector('.detail-purchase-bar .button')?.click(); true`)
        await waitFor(`Boolean(document.querySelector('.cart-search input'))`, `${viewport.name} mall purchase did not enter the cart.`)
        await evaluate(`(() => { const input = document.querySelector('.cart-search input'); const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set; setter.call(input, ${JSON.stringify(mallList.firstProductName)}); input.dispatchEvent(new Event('input', { bubbles: true })); return true })()`)
        await waitFor(`document.querySelectorAll('.cart-row').length > 0`, `${viewport.name} mall cart search did not find the purchased item.`)
        const cartBefore = await evaluate(`(() => ({
          rowCount: document.querySelectorAll('.cart-row').length,
          quantity: Number(document.querySelector('.quantity-stepper span')?.textContent ?? 0),
          searchValue: document.querySelector('.cart-search input')?.value ?? '',
          exposesSkuCode: /DEMO-SKU|1231/.test(document.querySelector('.cart-list')?.innerText ?? ''),
          plusDisabled: Boolean(document.querySelector('.quantity-stepper button:last-child')?.disabled),
        }))()`)
        assert(cartBefore.rowCount > 0 && cartBefore.searchValue === mallList.firstProductName && !cartBefore.exposesSkuCode && !cartBefore.plusDisabled, `${viewport.name} mall cart search or quantity state is invalid: ${JSON.stringify(cartBefore)}`)
        await evaluate(`document.querySelector('.quantity-stepper button:last-child')?.click(); true`)
        await waitFor(`Number(document.querySelector('.quantity-stepper span')?.textContent ?? 0) === ${cartBefore.quantity + 1}`, `${viewport.name} mall cart quantity did not increase.`)
        await evaluate(`document.querySelector('.quantity-stepper button:first-child')?.click(); true`)
        await waitFor(`Number(document.querySelector('.quantity-stepper span')?.textContent ?? 0) === ${cartBefore.quantity}`, `${viewport.name} mall cart quantity did not decrease.`)
        const cartShot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
        writeFileSync(`${previews}/run-${run}-${viewport.name}-mall-cart.png`, Buffer.from(cartShot.result.data, 'base64'))
        await evaluate(`document.querySelector('.cart-remove')?.click(); true`)
        await waitFor(`document.querySelectorAll('.cart-row').length === ${cartBefore.rowCount - 1}`, `${viewport.name} mall cart item did not delete.`)
        state.mall = { list: mallList, popover, detail: detailBefore, cart: cartBefore }
      }
      viewportResult.client[route || 'home'] = state
    }
    await navigate('/client/', '#main-content')
    const clientShot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    writeFileSync(`${previews}/run-${run}-${viewport.name}-client.png`, Buffer.from(clientShot.result.data, 'base64'))

    for (const route of adminRoutes) {
      await navigate(`/admin/${route}`, '#main-content')
      await waitFor(`(document.querySelector('#main-content')?.innerText.trim().length ?? 0) > 0`, `${viewport.name} admin ${route || 'overview'} remained empty.`, 40)
      const state = await evaluate(`(() => {
        const main = document.querySelector('#main-content'); const mobile = matchMedia('(max-width: 900px)').matches;
        const menu = document.querySelector('.mobile-menu-button');
        const overflow = document.documentElement.scrollWidth <= innerWidth + 1;
        const overflowDetails = overflow ? [] : [...document.querySelectorAll('body *')]
          .map((element) => { const rect = element.getBoundingClientRect(); return { tag: element.tagName, className: String(element.className ?? '').slice(0, 120), left: Math.round(rect.left), right: Math.round(rect.right), width: Math.round(rect.width) } })
          .filter((item) => item.left < -1 || item.right > innerWidth + 1)
          .sort((a, b) => (b.right - innerWidth) - (a.right - innerWidth)).slice(0, 8);
        return { title: document.title, overflow,
          viewportWidth: innerWidth, documentWidth: document.documentElement.scrollWidth, overflowDetails,
          mainTarget: main?.getAttribute('tabindex') === '-1', mainFocused: document.activeElement === main,
          skipTarget: document.querySelector('.skip-link')?.getAttribute('href') === '#main-content',
          correctNavigation: mobile ? getComputedStyle(menu).display !== 'none' : getComputedStyle(document.querySelector('.app-sidebar')).position !== 'fixed',
          touchTarget: mobile ? menu.getBoundingClientRect().width >= 44 && menu.getBoundingClientRect().height >= 44 : true,
          staleStageLabel: /SC\\d+/.test(document.querySelector('.app-header')?.innerText ?? ''), body: main?.innerText.slice(0, 100) ?? '' };
      })()`)
      assert(state.overflow, `${viewport.name} admin ${route || 'overview'} overflows horizontally: ${JSON.stringify(state)}`)
      assert(state.mainTarget && state.skipTarget && state.correctNavigation && state.touchTarget, `${viewport.name} admin ${route || 'overview'} failed accessibility/navigation contract: ${JSON.stringify(state)}`)
      assert(!state.staleStageLabel && state.body.trim(), `${viewport.name} admin ${route || 'overview'} exposed a stale stage label or empty content.`)
      if (route === 'booking-operations') {
        await waitFor(`document.querySelectorAll('.admin-day-cell').length > 0`, `${viewport.name} admin weekly schedule did not load.`, 40)
        await evaluate(`document.querySelector('.admin-day-cell')?.click(); true`)
        await waitFor(`document.querySelectorAll('.hour-list > article').length > 0`, `${viewport.name} admin hourly schedule did not open.`, 40)
        const hourlySchedule = await evaluate(`(() => {
          const ranges = [...document.querySelectorAll('.hour-list > article > div > strong')].map((item) => item.textContent.trim())
          const minutes = (value) => { const [hour, minute] = value.split(':').map(Number); return hour * 60 + minute }
          return {
            ranges,
            title: document.querySelector('.hour-list h3')?.textContent.trim(),
            everyRangeIsOneHour: ranges.length > 0 && ranges.every((range) => {
              const [start, end] = range.split('–')
              return minutes(end) - minutes(start) === 60
            }),
          }
        })()`)
        assert(hourlySchedule.title === '每小时安排' && hourlySchedule.everyRangeIsOneHour, `${viewport.name} admin schedule is not hourly: ${JSON.stringify(hourlySchedule)}`)
        state.hourlySchedule = hourlySchedule
      }
      viewportResult.admin[route || 'overview'] = state
    }
    if (viewport.width <= 900) {
      const drawer = await evaluate(`(async () => {
        const menu = document.querySelector('.mobile-menu-button'); menu.click(); await new Promise((resolve) => setTimeout(resolve, 100));
        const opened = menu.getAttribute('aria-expanded') === 'true' && document.body.classList.contains('mobile-nav-open');
        document.querySelector('.app-shell').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        await new Promise((resolve) => setTimeout(resolve, 100));
        return { opened, closed: menu.getAttribute('aria-expanded') === 'false', focusReturned: document.activeElement === menu };
      })()`)
      assert(drawer.opened && drawer.closed && drawer.focusReturned, `${viewport.name} admin drawer keyboard contract failed: ${JSON.stringify(drawer)}`)
      viewportResult.adminDrawer = drawer
    }
    await navigate('/admin/', '#main-content')
    const adminShot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    writeFileSync(`${previews}/run-${run}-${viewport.name}-admin.png`, Buffer.from(adminShot.result.data, 'base64'))
  }

  results.severeEventCount = severeEvents.length
  results.ignoredExtensionEventCount = ignoredExtensionEvents.length
  results.severeEvents = severeEvents.slice(-20).map((event) => ({
    method: event.method,
    url: event.params?.response?.url ?? event.params?.requestId,
    status: event.params?.response?.status,
    errorText: event.params?.errorText,
    exception: event.params?.exceptionDetails?.exception?.description ?? event.params?.exceptionDetails?.text,
  }))
  writeFileSync(`${output}/browser-matrix-run-${run}.json`, JSON.stringify(results, null, 2))
  assert(severeEvents.length === 0, `Browser recorded severe runtime/network events: ${JSON.stringify(results.severeEvents)}`)
  console.log(JSON.stringify({ run, mode: results.mode, viewports: Object.keys(results.viewports), severeEventCount: results.severeEventCount, ignoredExtensionEventCount: results.ignoredExtensionEventCount }, null, 2))
  console.log(`WEB_R_BROWSER_${smoke ? 'RESTART_SMOKE' : 'MATRIX'}_RUN_${run}=PASS`)
} finally {
  try { socket?.close() } catch {}
  edge.kill()
}
