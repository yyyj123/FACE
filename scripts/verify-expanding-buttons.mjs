import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawn } from 'node:child_process'
import WebSocket from '../front/node_modules/ws/index.js'

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'face-button-qa-'))
const previewDir = 'E:/FACE/.artifacts/预览/face-ui-20260722'
const debugFrontRoute = process.env.FACE_DEBUG_ROUTE || ''
const debugAdminRoute = process.env.FACE_ADMIN_DEBUG_ROUTE || ''
const viewportWidth = Number(process.env.FACE_VIEWPORT_WIDTH || 1440)
const viewportHeight = Number(process.env.FACE_VIEWPORT_HEIGHT || 1000)
const results = {}
let edge
let socket
let requestId = 0
const pending = new Map()

const send = (method, params = {}) => new Promise(resolve => {
  const id = ++requestId
  pending.set(id, resolve)
  socket.send(JSON.stringify({ id, method, params }))
})

const evaluate = async expression => {
  const response = await send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true
  })
  return response.result.result.value
}

async function navigate(url) {
  await send('Page.navigate', { url })
  await sleep(1800)
}

async function capturePage(name) {
  const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  fs.mkdirSync(previewDir, { recursive: true })
  fs.writeFileSync(path.join(previewDir, name), Buffer.from(shot.result.data, 'base64'))
}

async function inspectButton(selector, screenshotName) {
  const initial = await evaluate(`(() => {
    const button = document.querySelector(${JSON.stringify(selector)})
    if (!button) return null
    const rect = button.getBoundingClientRect()
    return { x: rect.x, y: rect.y, width: rect.width, height: rect.height }
  })()`)
  if (!initial) return { found: false }

  await send('Input.dispatchMouseEvent', {
    type: 'mouseMoved',
    x: initial.x + initial.width / 2,
    y: initial.y + initial.height / 2
  })

  const samples = []
  for (let index = 0; index < 12; index += 1) {
    await sleep(50)
    samples.push(await evaluate(`(() => {
      const button = document.querySelector(${JSON.stringify(selector)})
      const rect = button.getBoundingClientRect()
      const before = getComputedStyle(button, '::before')
      return {
        width: rect.width,
        height: rect.height,
        hovered: button.matches(':hover'),
        surfaceTransform: before.transform,
        rootOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth
      }
    })()`))
  }

  const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  fs.mkdirSync(previewDir, { recursive: true })
  fs.writeFileSync(path.join(previewDir, screenshotName), Buffer.from(shot.result.data, 'base64'))

  return {
    found: true,
    initial,
    widths: [...new Set(samples.map(sample => sample.width))],
    hoverStayedActive: samples.every(sample => sample.hovered),
    maxRootOverflow: Math.max(...samples.map(sample => sample.rootOverflow)),
    finalSurfaceTransform: samples.at(-1).surfaceTransform
  }
}

async function auditCurrentPage() {
  return evaluate(`(() => {
    const visible = node => !!(node.offsetWidth || node.offsetHeight || node.getClientRects().length)
    const buttons = [...document.querySelectorAll('.el-button')].filter(visible)
    const rootOverflow = document.documentElement.scrollWidth - document.documentElement.clientWidth
    const heroVisible = [...document.querySelectorAll('.oc-hero')].some(visible)
    const unexpectedHero = !location.hash.startsWith('#/index/home') && heroVisible
    const horizontalOverflow = [...document.querySelectorAll('.center-form-pv, .list-form-pv, .oc-admin-actions, .actions, .add-update-preview, .add-update-form')]
      .filter(visible).some(node => node.scrollWidth > node.clientWidth + 1)
    const unstableButton = buttons.some(button => {
      const transition = getComputedStyle(button).transitionProperty
      return transition.includes('width') || transition.includes('min-width')
    })
    const lightEdges = [...document.querySelectorAll('.el-input__inner, .el-table, .list-form-pv, .center-form-pv')]
      .filter(visible).filter(node => {
        const color = getComputedStyle(node).borderColor
        return color === 'rgb(221, 221, 221)' || color === 'rgb(238, 238, 238)' || color === 'rgb(255, 255, 255)'
      }).length
    const tableHeaders = [...document.querySelectorAll('.el-table__header-wrapper th .cell')].filter(visible)
    const clippedTableHeaders = tableHeaders.filter(cell => {
      const cellRect = cell.getBoundingClientRect()
      const contentRight = [...cell.children].reduce((right, child) => Math.max(right, child.getBoundingClientRect().right), cellRect.left)
      return cell.scrollWidth > cell.clientWidth + 1 || contentRight > cellRect.right + 1
    }).map(cell => ({
      text: cell.innerText.trim(),
      clientWidth: Math.round(cell.clientWidth),
      scrollWidth: Math.round(cell.scrollWidth)
    }))
    const tableBodyWrappers = [...document.querySelectorAll('.el-table__body-wrapper')].filter(visible)
    const overflowNodes = horizontalOverflow ? [...document.querySelectorAll('*')]
      .filter(node => {
        if (!visible(node)) return false
        const parentWidth = node.parentElement?.clientWidth || Infinity
        return node.scrollWidth > node.clientWidth + 1 || node.getBoundingClientRect().width > parentWidth + 1
      })
      .slice(0, 24)
      .map(node => ({
        tag: node.tagName,
        className: typeof node.className === 'string' ? node.className : '',
        clientWidth: node.clientWidth,
        scrollWidth: node.scrollWidth,
        rectWidth: Math.round(node.getBoundingClientRect().width),
        inlineStyle: node.getAttribute('style') || ''
      })) : []
    return {
      href: location.hash,
      rootOverflow,
      horizontalOverflow,
      unstableButton,
      lightEdges,
      unexpectedHero,
      buttons: buttons.length,
      overflowNodes,
      tableHeaderCount: tableHeaders.length,
      clippedTableHeaders,
      tableScrollAvailable: tableBodyWrappers.every(wrapper => wrapper.scrollWidth <= wrapper.clientWidth + 1 || getComputedStyle(wrapper).overflowX !== 'hidden')
    }
  })()`)
}

try {
  edge = spawn('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
    `--user-data-dir=${profile}`,
    '--headless=new',
    '--remote-debugging-port=9341',
    '--no-first-run',
    'about:blank'
  ], { windowsHide: true, stdio: 'ignore' })

  await sleep(1200)
  const targets = await fetch('http://127.0.0.1:9341/json').then(response => response.json())
  const target = targets.find(item => item.type === 'page')
  socket = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise(resolve => socket.once('open', resolve))
  socket.on('message', data => {
    const message = JSON.parse(data)
    if (message.id && pending.has(message.id)) {
      pending.get(message.id)(message)
      pending.delete(message.id)
    }
    if (message.method === 'Fetch.requestPaused') {
      const body = Buffer.from(JSON.stringify({
        code: 0,
        data: {
          list: [], total: 0, pageSize: 10, totalPage: 1,
          summary: {}, appointmentStatus: [], dailyRevenue: []
        }
      })).toString('base64')
      send('Fetch.fulfillRequest', {
        requestId: message.params.requestId,
        responseCode: 200,
        responseHeaders: [{ name: 'Content-Type', value: 'application/json; charset=utf-8' }],
        body
      })
    }
  })

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Fetch.enable', { patterns: [{ urlPattern: '*/face/*', requestStage: 'Request' }] })
  await send('Emulation.setDeviceMetricsOverride', {
    width: viewportWidth,
    height: viewportHeight,
    deviceScaleFactor: 1,
    mobile: false
  })

  await navigate('http://127.0.0.1:8081/#/login')
  results.adminLogin = await evaluate(`(() => ({
    identityOptions: [...document.querySelectorAll('.identity-switch .el-radio-button')].map(node => node.textContent.trim()),
    selectedIdentity: document.querySelector('.identity-switch .el-radio-button.is-active')?.textContent.trim() || '',
    horizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
  }))()`)
  await capturePage('admin-login-identities.png')
  await evaluate(`localStorage.setItem('Token', JSON.stringify('qa-token')); localStorage.setItem('role', JSON.stringify('管理员')); localStorage.setItem('sessionTable', JSON.stringify('users')); localStorage.setItem('adminName', JSON.stringify('admin'));`)
  await navigate('http://127.0.0.1:8081/#/weixiujilu')
  results.adminPage = await evaluate(`({ href: location.href, title: document.title, text: document.body.innerText.slice(0, 240), buttons: document.querySelectorAll('.el-button').length })`)
  results.admin = await inspectButton('.center-form-pv .el-button:not(.is-disabled)', 'admin-expanded-button.png')
  results.adminLayout = await evaluate(`(() => ({
    rootOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    toolbarOverflow: [...document.querySelectorAll('.center-form-pv')].some(node => node.scrollWidth > node.clientWidth + 1),
    actionOverflow: [...document.querySelectorAll('.center-form-pv .actions')].some(node => node.scrollWidth > node.clientWidth + 1),
    lightEdges: [...document.querySelectorAll('.center-form-pv .el-input__inner, .el-table')].filter(node => {
      const color = getComputedStyle(node).borderColor
      return color === 'rgb(221, 221, 221)' || color === 'rgb(238, 238, 238)' || color === 'rgb(255, 255, 255)'
    }).length
  }))()`)

  const adminModules = debugAdminRoute ? [debugAdminRoute] : debugFrontRoute ? [] : [
    'guzhangfenlei', 'peijianchuku', 'pinpaixinxi', 'weixiujilu',
    'xinnengyuanqiche', 'weixiujishi', 'peijianxinxi', 'fuwufenlei',
    'guzhangpaicha', 'chat', 'shouhoufuwu', 'fuwuyuyue',
    'pingjiafankui', 'weixiuziliao', 'config', 'chezhu',
    'chatmessage', 'friend', 'storeup', 'users'
  ]
  results.adminModules = {}
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 4, y: 4 })
  for (const route of adminModules) {
    await evaluate(`location.hash = '#/${route}'`)
    await sleep(450)
    results.adminModules[route] = await auditCurrentPage()
    if (debugAdminRoute || results.adminModules[route].clippedTableHeaders.length) {
      await capturePage(`admin-table-audit-${route}.png`)
    }
  }

  await evaluate(`location.hash = '#/xinnengyuanqiche'`)
  await sleep(500)
  results.adminTableAndFilters = await evaluate(`(() => {
    const visible = node => !!(node.offsetWidth || node.offsetHeight || node.getClientRects().length)
    const headers = [...document.querySelectorAll('.el-table th .cell')].filter(visible)
    const fields = [...document.querySelectorAll('.package-filter-field, .center-form-pv > .el-row:first-child > div')].filter(visible)
    const rects = fields.map(node => {
      const rect = node.getBoundingClientRect()
      return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom }
    })
    const overlaps = rects.some((a, index) => rects.slice(index + 1).some(b =>
      a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
    ))
    const labelsAboveInputs = fields.every(field => {
      const label = field.querySelector('.item-label')
      const control = field.querySelector('.el-input, .el-select')
      if (!label || !control) return true
      return label.getBoundingClientRect().bottom <= control.getBoundingClientRect().top + 1
    })
    return {
      headerCount: headers.length,
      headersCentered: headers.every(node => getComputedStyle(node).textAlign === 'center'),
      filterCount: fields.length,
      filterOverlap: overlaps,
      labelsAboveInputs,
      toolbarOverflow: [...document.querySelectorAll('.center-form-pv')].some(node => node.scrollWidth > node.clientWidth + 1)
    }
  })()`)
  await capturePage('admin-centered-table-and-filters.png')

  await navigate('http://127.0.0.1:8082/#/login')
  results.frontLogin = await evaluate(`(() => {
    const button = document.querySelector('.login_btn')
    if (!button) return { found: false }
    const before = getComputedStyle(button, '::before')
    const after = getComputedStyle(button, '::after')
    const span = button.querySelector('span')
    return {
      found: true,
      width: Math.round(button.getBoundingClientRect().width),
      borderRadius: getComputedStyle(button).borderRadius,
      background: getComputedStyle(button).backgroundColor,
      beforeDisplay: before.display,
      afterDisplay: after.display,
      labelOpacity: span ? getComputedStyle(span).opacity : null,
      horizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
    }
  })()`)
  await capturePage('front-static-login-button.png')

  await evaluate(`
    localStorage.setItem('UserTableName', 'chezhu');
    localStorage.setItem('frontSessionTable', 'chezhu');
    localStorage.setItem('username', '会员1');
    localStorage.setItem('sessionForm', JSON.stringify({
      id: 1,
      zhanghao: 'member1',
      xingming: '会员1',
      xingbie: '女',
      shouji: '13900000002',
      touxiang: ''
    }));
  `)
  await navigate('http://127.0.0.1:8082/#/index/center')
  results.frontCenter = await evaluate(`(() => {
    const visible = node => !!(node.offsetWidth || node.offsetHeight || node.getClientRects().length)
    const inspected = [...document.querySelectorAll(
      '.center-tabs, .center-tabs .el-tabs__item, .center-tabs .el-tabs__content, .center-preview-pv, .center-item, .center-item .el-input__inner, .center-item .el-select .el-input__inner, .center-item .el-upload--picture-card'
    )].filter(visible)
    const whiteSurfaces = inspected.filter(node => {
      const color = getComputedStyle(node).backgroundColor
      return color === 'rgb(255, 255, 255)' || color === 'rgba(255, 255, 255, 1)'
    }).length
    const legacyBlue = inspected.filter(node => {
      const color = getComputedStyle(node).backgroundColor
      return color === 'rgb(0, 102, 212)' || color === 'rgb(0, 99, 205)'
    }).length
    const tabs = [...document.querySelectorAll('.center-tabs .el-tabs__item')].filter(visible)
    const buttons = [...document.querySelectorAll('.center-btn-item .el-button')].filter(visible)
    const form = document.querySelector('.center-preview-pv')
    return {
      tabCount: tabs.length,
      whiteSurfaces,
      legacyBlue,
      titleHidden: !visible(document.querySelector('.center-title')),
      semanticButtons: buttons.length === 2 && buttons.every(node => node.tagName === 'BUTTON'),
      formOverflow: form ? form.scrollWidth > form.clientWidth + 1 : true,
      rootOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth
    }
  })()`)
  await capturePage('front-center-dark-personal-info.png')
  await evaluate(`([...document.querySelectorAll('.center-tabs .el-tabs__item')].find(node => node.textContent.trim() === '修改密码'))?.click()`)
  await sleep(250)
  results.frontCenterPassword = await evaluate(`(() => {
    const fields = [...document.querySelectorAll('.center-preview-pv .el-input__inner')].filter(node => node.offsetWidth)
    return {
      fieldCount: fields.length,
      whiteFields: fields.filter(node => getComputedStyle(node).backgroundColor === 'rgb(255, 255, 255)').length,
      horizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
    }
  })()`)
  await capturePage('front-center-dark-password.png')
  await evaluate(`([...document.querySelectorAll('.center-tabs .el-tabs__item')].find(node => node.textContent.trim() === '聊天记录'))?.click()`)
  await sleep(250)
  results.frontCenterChat = await evaluate(`(() => {
    const content = document.querySelector('.center-tabs .el-tabs__content')
    return {
      emptyStateVisible: !!document.querySelector('.center-tabs .oc-empty'),
      whiteContent: content ? getComputedStyle(content).backgroundColor === 'rgb(255, 255, 255)' : true,
      horizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
    }
  })()`)
  await capturePage('front-center-dark-chat.png')

  results.frontCenterInlineModules = {}
  for (const [label, key] of [
    ['项目预约', 'appointments'],
    ['服务记录', 'records'],
    ['服务评价', 'reviews'],
    ['我的收藏', 'favorites']
  ]) {
    await evaluate(`([...document.querySelectorAll('.center-tabs .el-tabs__item')].find(node => node.textContent.trim() === ${JSON.stringify(label)}))?.click()`)
    await sleep(700)
    results.frontCenterInlineModules[key] = await evaluate(`(() => {
      const visible = node => !!node && !!(node.offsetWidth || node.offsetHeight || node.getClientRects().length)
      const pane = [...document.querySelectorAll('.center-tabs .el-tab-pane')].find(visible)
      const embedded = pane?.querySelector('.center-embedded-module')
      const toolbar = pane?.querySelector('.center-record-toolbar')
      const toolbarFields = [...(toolbar?.querySelectorAll('.el-form-item.list-item') || [])].filter(visible)
      const fieldRects = toolbarFields.map(field => {
        const label = field.querySelector('.lable')?.getBoundingClientRect()
        const input = field.querySelector('.el-input')?.getBoundingClientRect()
        return label && input ? { label, input } : null
      }).filter(Boolean)
      const inputWidths = fieldRects.map(({ input }) => input.width)
      const inspected = [...(pane?.querySelectorAll(
        '.center-embedded-module, .list-preview, .list-form-pv, .storeup-preview, .storeup-toolbar, .storeup-card, .pagination, .pagination .btn-prev, .pagination .btn-next, .el-input__inner'
      ) || [])].filter(visible)
      const whiteSurfaces = inspected.filter(node => getComputedStyle(node).backgroundColor === 'rgb(255, 255, 255)').length
      const prev = pane?.querySelector('.storeup-preview .pagination .btn-prev')
      return {
        route: location.hash,
        embeddedVisible: visible(embedded),
        repeatedBreadcrumbs: pane?.querySelectorAll('.breadcrumb-preview').length || 0,
        repeatedBackButtons: pane?.querySelectorAll('.back_box').length || 0,
        labelsAboveInputs: !toolbar || fieldRects.every(({ label, input }) => label.bottom <= input.top + 1),
        evenlySizedInputs: !toolbar || inputWidths.length < 2 || Math.max(...inputWidths) - Math.min(...inputWidths) <= 2,
        toolbarColumns: toolbar ? getComputedStyle(toolbar).gridTemplateColumns : null,
        whiteSurfaces,
        previousButtonBackground: prev ? getComputedStyle(prev).backgroundColor : null,
        previousButtonMatchesTheme: !prev || getComputedStyle(prev).backgroundColor !== 'rgb(255, 255, 255)',
        paneOverflow: pane ? pane.scrollWidth > pane.clientWidth + 1 : true,
        rootOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth
      }
    })()`)
    await capturePage(`front-center-inline-${key}.png`)
  }

  await navigate('http://127.0.0.1:8082/#/index/home')
  const homeHeroInitial = await evaluate(`({
    visible: !!document.querySelector('.oc-hero'),
    swiperInitialized: !!document.querySelector('.oc-hero .swiper-container-initialized')
  })`)
  await evaluate(`location.hash = '#/index/xinnengyuanqiche'`)
  await sleep(500)
  const innerHero = await evaluate(`({
    visible: !!document.querySelector('.oc-hero'),
    route: location.hash
  })`)
  await capturePage('front-package-without-home-hero.png')
  await evaluate(`location.hash = '#/index/home'`)
  await sleep(500)
  const homeHeroAfterReturn = await evaluate(`({
    visible: !!document.querySelector('.oc-hero'),
    swiperInitialized: !!document.querySelector('.oc-hero .swiper-container-initialized')
  })`)
  results.frontHeroVisibility = { homeHeroInitial, innerHero, homeHeroAfterReturn }

  await navigate('http://127.0.0.1:8082/#/index/fuwuyuyue')
  results.frontPage = await evaluate(`({ href: location.href, title: document.title, text: document.body.innerText.slice(0, 240), buttons: document.querySelectorAll('.el-button').length })`)
  results.front = await inspectButton('.el-button:not(.is-disabled)', 'front-expanded-button.png')
  results.frontLayout = await evaluate(`({
    rootOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    listOverflow: [...document.querySelectorAll('.list-preview, .list-form-pv')].some(node => node.scrollWidth > node.clientWidth + 1)
  })`)

  await navigate('http://127.0.0.1:8082/#/index/xinnengyuanqiche')
  results.frontPackageFilters = await evaluate(`(() => {
    const visible = node => !!(node.offsetWidth || node.offsetHeight || node.getClientRects().length)
    const fields = [...document.querySelectorAll('.front-package-page .package-filter-field')].filter(visible)
    const rects = fields.map(node => {
      const rect = node.getBoundingClientRect()
      return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom }
    })
    const overlaps = rects.some((a, index) => rects.slice(index + 1).some(b =>
      a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
    ))
    const labelsAboveInputs = fields.every(field => {
      const label = field.querySelector('.lable')
      const control = field.querySelector('.el-input')
      if (!label || !control) return false
      return label.getBoundingClientRect().bottom <= control.getBoundingClientRect().top + 1
    })
    const actions = [...document.querySelectorAll('.front-package-page .package-filter-actions .el-button')].filter(visible)
    const actionTops = actions.map(node => Math.round(node.getBoundingClientRect().top))
    const toolbar = document.querySelector('.front-package-page .package-search-toolbar')
    return {
      filterCount: fields.length,
      filterOverlap: overlaps,
      labelsAboveInputs,
      actionCount: actions.length,
      actionsSameRow: actionTops.length < 2 || Math.max(...actionTops) - Math.min(...actionTops) <= 2,
      toolbarOverflow: toolbar ? toolbar.scrollWidth > toolbar.clientWidth + 1 : true,
      rootOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth
    }
  })()`)
  await capturePage('front-centered-package-filters.png')

  const frontModules = debugFrontRoute ? [debugFrontRoute] : debugAdminRoute ? [] : [
    'chezhu', 'weixiujishi', 'pinpaixinxi', 'xinnengyuanqiche',
    'fuwufenlei', 'shouhoufuwu', 'fuwuyuyue', 'weixiujilu',
    'pingjiafankui', 'peijianxinxi', 'peijianchuku', 'guzhangpaicha',
    'guzhangfenlei', 'weixiuziliao', 'chatmessage', 'friend'
  ]
  results.frontModules = {}
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 4, y: 4 })
  for (const route of frontModules) {
    for (const suffix of debugFrontRoute ? ['Add'] : ['', 'Detail', 'Add']) {
      const page = `${route}${suffix}`
      await evaluate(`location.hash = '#/index/${page}'`)
      await sleep(320)
      results.frontModules[page] = await auditCurrentPage()
    }
  }

  await send('Emulation.setDeviceMetricsOverride', {
    width: 390,
    height: 844,
    deviceScaleFactor: 1,
    mobile: true
  })

  await navigate('http://127.0.0.1:8082/#/index/center')
  results.mobileFrontCenter = await evaluate(`(() => {
    const visible = node => !!(node.offsetWidth || node.offsetHeight || node.getClientRects().length)
    const fields = [...document.querySelectorAll('.center-preview-pv .center-item')].filter(visible)
    const labelsAboveInputs = fields.every(field => {
      const label = field.querySelector('.el-form-item__label')
      const control = field.querySelector('.el-input, .el-select, .upload')
      if (!label || !control) return true
      return label.getBoundingClientRect().bottom <= control.getBoundingClientRect().top + 1
    })
    const labelsLeftAligned = fields.every(field => {
      const label = field.querySelector('.el-form-item__label')
      return !label || getComputedStyle(label).textAlign === 'left'
    })
    const whiteSurfaces = [...document.querySelectorAll('.center-item, .center-item .el-input__inner')]
      .filter(visible)
      .filter(node => getComputedStyle(node).backgroundColor === 'rgb(255, 255, 255)').length
    return {
      fieldCount: fields.length,
      labelsAboveInputs,
      labelsLeftAligned,
      whiteSurfaces,
      rootOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth
    }
  })()`)
  await capturePage('front-center-dark-mobile.png')

  await navigate('http://127.0.0.1:8081/#/weixiujilu')
  results.mobileAdminModules = {}
  for (const route of adminModules) {
    await evaluate(`location.hash = '#/${route}'`)
    await sleep(220)
    results.mobileAdminModules[route] = await auditCurrentPage()
  }

  await navigate('http://127.0.0.1:8082/#/index/fuwuyuyue')
  results.mobileFrontModules = {}
  for (const route of frontModules) {
    for (const suffix of debugFrontRoute ? ['Add'] : ['', 'Detail', 'Add']) {
      const page = `${route}${suffix}`
      await evaluate(`location.hash = '#/index/${page}'`)
      await sleep(180)
      results.mobileFrontModules[page] = await auditCurrentPage()
    }
  }

  const failed = entries => Object.entries(entries).filter(([, page]) =>
    page.rootOverflow !== 0 || page.horizontalOverflow || page.unstableButton || page.lightEdges !== 0 || page.unexpectedHero || page.clippedTableHeaders?.length || page.tableScrollAvailable === false
  ).map(([route, page]) => ({ route, ...page }))
  console.log(JSON.stringify({
    adminLogin: results.adminLogin,
    frontLogin: results.frontLogin,
    frontCenter: results.frontCenter,
    frontCenterPassword: results.frontCenterPassword,
    frontCenterChat: results.frontCenterChat,
    frontCenterInlineModules: results.frontCenterInlineModules,
    mobileFrontCenter: results.mobileFrontCenter,
    frontHeroVisibility: results.frontHeroVisibility,
    adminTableAndFilters: results.adminTableAndFilters,
    frontPackageFilters: results.frontPackageFilters,
    adminButton: results.admin,
    frontButton: results.front,
    adminPagesChecked: Object.keys(results.adminModules).length,
    frontPagesChecked: Object.keys(results.frontModules).length,
    mobileAdminPagesChecked: Object.keys(results.mobileAdminModules).length,
    mobileFrontPagesChecked: Object.keys(results.mobileFrontModules).length,
    adminFailures: failed(results.adminModules),
    frontFailures: failed(results.frontModules),
    mobileAdminFailures: failed(results.mobileAdminModules),
    mobileFrontFailures: failed(results.mobileFrontModules)
  }, null, 2))
} finally {
  try { socket?.close() } catch {}
  try { edge?.kill() } catch {}
  await sleep(300)
  fs.rmSync(profile, { recursive: true, force: true })
}
