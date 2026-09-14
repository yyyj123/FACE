import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(process.argv[2] ?? process.cwd())
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8')
const requireText = (file, patterns) => {
  const source = read(file)
  for (const pattern of patterns) {
    if (!source.includes(pattern)) throw new Error(`${file} is missing ${pattern}`)
  }
}

requireText('front-next/src/views/CheckoutView.vue', [
  '默认不使用任何优惠', 'quote.points.reason', 'ZERO_AMOUNT', 'STORED_VALUE',
  'quote.paymentChannels.filter', 'onBeforeUnmount',
])
requireText('front-next/src/views/BenefitsView.vue', ['我的卡项与优惠', 'ONLINE_PURCHASE', 'STORED_VALUE'])
requireText('admin-next/src/views/BenefitsOperationsView.vue', [
  'COMBO_TIMES', 'STORED_VALUE', 'DISCOUNT', 'THRESHOLD_REDUCTION',
  'SERVICE_EXPERIENCE', 'OFFLINE_SALE', 'GIFT', 'REISSUE', 'maskedConfig',
])
requireText('admin-next/src/router/index.ts', ["path: 'benefits'", "'benefit:view'", "'payment_config:view'"])
requireText('backend-next/src/main/java/com/face/platform/checkout/CheckoutApplicationService.java', ['积分抵扣将在 SC5 开放'])
console.log('SC4_RESPONSIVE_UI_CONTRACT=PASS')
