import assert from 'node:assert/strict'
import test from 'node:test'
import {
  availablePurchaseOptions,
  purchaseActionLabel,
  resolvePurchaseMode,
  type MallSku,
} from '../src/utils/mall.ts'

function sku(overrides: Partial<MallSku> = {}): MallSku {
  return {
    id: 1,
    cashPrice: 99,
    pointsPrice: 1000,
    comboCashPrice: 39,
    comboPointsPrice: 500,
    cashEnabled: false,
    pointsEnabled: false,
    comboEnabled: false,
    availableQuantity: 10,
    ...overrides,
  }
}

test('supports cash-only SKU without a purchase-count badge', () => {
  const options = availablePurchaseOptions(sku({ cashEnabled: true }))
  assert.deepEqual(options.map((option) => option.mode), ['CASH'])
  assert.equal(options[0]?.priceLabel, '¥99')
})

test('supports points-only SKU', () => {
  const options = availablePurchaseOptions(sku({ pointsEnabled: true }))
  assert.deepEqual(options.map((option) => option.mode), ['POINTS'])
  assert.equal(options[0]?.priceLabel, '1000积分')
})

test('supports combination-only SKU', () => {
  const options = availablePurchaseOptions(sku({ comboEnabled: true }))
  assert.deepEqual(options.map((option) => option.mode), ['COMBINATION'])
  assert.equal(options[0]?.priceLabel, '500积分 + ¥39')
})

test('counts cash and points modes from current SKU configuration', () => {
  const options = availablePurchaseOptions(sku({ cashEnabled: true, pointsEnabled: true }))
  assert.deepEqual(options.map((option) => option.mode), ['CASH', 'POINTS'])
})

test('keeps all three configured modes without a hard-coded count', () => {
  const options = availablePurchaseOptions(sku({ cashEnabled: true, pointsEnabled: true, comboEnabled: true }))
  assert.deepEqual(options.map((option) => option.mode), ['CASH', 'POINTS', 'COMBINATION'])
})

test('does not expose enabled modes whose backend price is missing or invalid', () => {
  const options = availablePurchaseOptions(sku({
    cashEnabled: true,
    cashPrice: null,
    pointsEnabled: true,
    pointsPrice: Number.NaN,
    comboEnabled: true,
    comboPointsPrice: 0,
  }))
  assert.deepEqual(options, [])
})

test('SKU switch preserves a still-valid mode', () => {
  const next = sku({ cashEnabled: true, pointsEnabled: true })
  assert.equal(resolvePurchaseMode(next, 'POINTS'), 'POINTS')
})

test('SKU switch replaces a mode that the new SKU no longer supports', () => {
  const next = sku({ cashEnabled: true })
  assert.equal(resolvePurchaseMode(next, 'POINTS'), 'CASH')
  assert.equal(resolvePurchaseMode(sku(), 'POINTS'), null)
})

test('purchase button wording follows the selected mode', () => {
  const current = sku({ cashEnabled: true, pointsEnabled: true, comboEnabled: true })
  assert.equal(purchaseActionLabel(current, 'CASH'), '¥99 立即购买')
  assert.equal(purchaseActionLabel(current, 'POINTS'), '1000积分 立即兑换')
  assert.equal(purchaseActionLabel(current, 'COMBINATION'), '500积分 + ¥39 立即购买')
  assert.equal(purchaseActionLabel(sku(), null), '暂不可购买')
})
