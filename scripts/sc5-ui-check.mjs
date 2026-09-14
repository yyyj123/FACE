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

requireText('front-next/src/views/PointsStoreView.vue', [
  '积分商城', '可用积分', '每日任务', 'availablePurchaseOptions', '购买方式',
  'cartQuery', 'changeCartQuantity', 'removeCartItem', '提交时重新校验共享库存和价格',
])
requireText('front-next/src/utils/mall.ts', [
  "'CASH'", "'POINTS'", "'COMBINATION'", 'availablePurchaseOptions',
  'resolvePurchaseMode', 'purchaseActionLabel',
])
requireText('front-next/src/components/ClientShell.vue', ['/points-store', '积分商城'])
requireText('admin-next/src/views/PointsMallOperationsView.vue', [
  '积分与商城运营', '共享 SKU 库存', '人工补偿积分', '扫描到期提醒',
  '商品上架', '包裹履约', '不进入采购、调拨和复杂成本',
])
requireText('admin-next/src/router/index.ts', ["path: 'points-mall'", "'points:view'", "'mall:view'"])
requireText('backend-next/src/main/java/com/face/platform/store/MallApplicationService.java', [
  'MallPolicy.splitKey', 'mall_stock_reservation', 'mall_package_sub_order',
  '已支付订单售后属于 SC6',
])
requireText('backend-next/src/main/java/com/face/platform/points/PointsApplicationService.java', [
  'allocateFefo', 'points_reservation_allocation', 'points_expiry_notice',
])
console.log('SC5_RESPONSIVE_UI_CONTRACT=PASS')
