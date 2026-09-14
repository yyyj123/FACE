import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../src/views/PointsStoreView.vue', import.meta.url), 'utf8')

test('product cards show one main price and a dynamic purchase-mode disclosure without SKU codes', () => {
  assert.match(source, /class="product-main-price"/)
  assert.match(source, /purchaseOptions\(cardSku\(product\)\)\.length/)
  assert.match(source, /种购买方式/)
  assert.doesNotMatch(source, /\{\{\s*sku\.skuCode\s*\}\}/)
  assert.doesNotMatch(source, />3种购买方式</)
})

test('desktop and mobile can inspect purchase modes and dismiss overlays', () => {
  assert.match(source, /class="purchase-mode-popover"/)
  assert.match(source, /@mouseenter=/)
  assert.match(source, /aria-haspopup="dialog"/)
  assert.match(source, /event\.key === 'Escape'/)
  assert.match(source, /@media\(max-width:760px\)[\s\S]*\.product-detail-panel/)
})

test('product detail selects SKU and purchase mode with a linked primary action', () => {
  assert.match(source, /class="product-detail-overlay"/)
  assert.match(source, /class="sku-selector"/)
  assert.match(source, /class="purchase-choice-list"/)
  assert.match(source, /purchaseActionLabel\(selectedSku, selectedMode\)/)
  assert.match(source, /selectSku\(/)
})

test('cart supports search, quantity increase, quantity decrease, and removal', () => {
  assert.match(source, /v-model="cartQuery"/)
  assert.match(source, /filteredCartItems/)
  assert.match(source, /changeCartQuantity\(item, -1\)/)
  assert.match(source, /changeCartQuantity\(item, 1\)/)
  assert.match(source, /removeCartItem\(item\)/)
})
