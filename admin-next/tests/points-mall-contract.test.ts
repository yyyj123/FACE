import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')

test('mall product creation declares the physical shipping mode expected by the backend', () => {
  const view = read('../src/views/PointsMallOperationsView.vue')

  const createProduct = view.match(/async function createProduct\(\)[\s\S]*?\n}\n\nasync function ship/)
  assert.ok(createProduct, 'createProduct function must remain discoverable by the contract test')
  assert.match(createProduct[0], /separate_shipping:\s*false/)
})

test('points settings translate system codes into merchant-friendly language', () => {
  const view = read('../src/views/PointsMallOperationsView.vue')

  assert.match(view, /GLOBAL_EARN:\s*'消费送积分'/)
  assert.match(view, /GLOBAL_REDEEM:\s*'积分抵现金'/)
  assert.match(view, /GLOBAL:\s*'全店通用'/)
  assert.doesNotMatch(view, /<el-table-column prop="ruleCode"/)
  assert.doesNotMatch(view, /<el-table-column prop="ruleType"/)
  assert.doesNotMatch(view, /<el-table-column prop="targetType"/)
  assert.doesNotMatch(view, /POINTS &amp; COMMERCE/)
  assert.match(view, /在这里统一管理会员积分、商品库存、商城订单和发货记录。/)
  assert.match(view, /placeholder="请选择要调整的规则"/)
})
