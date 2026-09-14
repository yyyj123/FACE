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

requireText('front-next/src/views/CareFeedbackView.vue', [
  '护理确认、评价与售后', '24 小时内确认', '三项评分必填',
  '历史版本仍保留', '48 小时确认期', '验货通过前不会返还积分、款项或运费',
])
requireText('front-next/src/components/ClientShell.vue', [
  'v-if="auth.isSignedIn && !auth.isTechnician" to="/care-feedback"', '评价与售后',
])
requireText('front-next/src/router/index.ts', [
  "path: 'care-feedback'", "name: 'care-feedback'", 'requiresAuth: true',
])
requireText('admin-next/src/views/FulfillmentOperationsView.vue', [
  '履约、评价与售后', '普通管理员单次资产影响上限 500 元',
  '48 小时未操作自动关单', '验货前禁止返还资产', '不得修改顾客原文',
])
requireText('admin-next/src/router/index.ts', [
  "path: 'fulfillment'", "'fulfillment:view'", "'review:moderate'", "'aftersale:view'",
])
requireText('backend-next/src/main/java/com/face/platform/servicecare/CustomerConfirmationService.java', [
  'SYSTEM_AUTO_CONFIRMED', 'service_fulfillment_fact', 'PENDING_CUSTOMER_CONFIRMATION',
])
requireText('backend-next/src/main/java/com/face/platform/sc6/Sc6ReviewApplicationService.java', [
  'service_review_version', 'average', 'LOW_SCORE_REVIEW', 'CONTACT_REQUEST',
])
requireText('backend-next/src/main/java/com/face/platform/sc6/Sc6AfterSaleApplicationService.java', [
  'requiresSuperAdmin', 'releaseCompletedRefunds', 'customer_response_due_at',
  'PENDING_INSPECTION', 'after_sale_asset_ledger',
])
console.log('SC6_RESPONSIVE_UI_AND_DOMAIN_CONTRACT=PASS')
