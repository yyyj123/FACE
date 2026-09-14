import { money } from './format.ts'

export type PurchaseMode = 'CASH' | 'POINTS' | 'COMBINATION'

export interface MallSku {
  id: number
  skuCode?: string
  spec?: Record<string, unknown> | string | null
  imageUrl?: string | null
  cashPrice?: number | string | null
  pointsPrice?: number | string | null
  comboCashPrice?: number | string | null
  comboPointsPrice?: number | string | null
  cashEnabled?: boolean
  pointsEnabled?: boolean
  comboEnabled?: boolean
  purchaseLimit?: number | null
  availableQuantity: number
}

export interface PurchaseOption {
  mode: PurchaseMode
  priceLabel: string
  title: string
  action: '立即购买' | '立即兑换'
}

function positiveNumber(value: unknown) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

function points(value: unknown) {
  const parsed = positiveNumber(value)
  return parsed === null ? null : `${Math.trunc(parsed)}积分`
}

export function availablePurchaseOptions(sku: MallSku | null | undefined): PurchaseOption[] {
  if (!sku) return []
  const result: PurchaseOption[] = []
  const cashPrice = positiveNumber(sku.cashPrice)
  const pointsPrice = points(sku.pointsPrice)
  const comboCashPrice = positiveNumber(sku.comboCashPrice)
  const comboPointsPrice = points(sku.comboPointsPrice)

  if (sku.cashEnabled && cashPrice !== null) {
    result.push({ mode: 'CASH', priceLabel: money(cashPrice), title: '现金购买', action: '立即购买' })
  }
  if (sku.pointsEnabled && pointsPrice !== null) {
    result.push({ mode: 'POINTS', priceLabel: pointsPrice, title: '积分兑换', action: '立即兑换' })
  }
  if (sku.comboEnabled && comboCashPrice !== null && comboPointsPrice !== null) {
    result.push({
      mode: 'COMBINATION',
      priceLabel: `${comboPointsPrice} + ${money(comboCashPrice)}`,
      title: '混合支付',
      action: '立即购买',
    })
  }
  return result
}

export function resolvePurchaseMode(
  sku: MallSku | null | undefined,
  preferred: PurchaseMode | null | undefined,
): PurchaseMode | null {
  const options = availablePurchaseOptions(sku)
  if (preferred && options.some((option) => option.mode === preferred)) return preferred
  return options[0]?.mode ?? null
}

export function purchaseActionLabel(
  sku: MallSku | null | undefined,
  mode: PurchaseMode | null | undefined,
) {
  const option = availablePurchaseOptions(sku).find((item) => item.mode === mode)
  return option ? `${option.priceLabel} ${option.action}` : '暂不可购买'
}

export function purchaseModeTitle(mode: string | null | undefined) {
  if (mode === 'CASH') return '现金购买'
  if (mode === 'POINTS') return '积分兑换'
  if (mode === 'COMBINATION') return '混合支付'
  return '购买方式'
}

function parseSpec(spec: MallSku['spec']) {
  if (!spec) return null
  if (typeof spec === 'object') return spec
  try {
    const parsed = JSON.parse(spec) as unknown
    return parsed && typeof parsed === 'object' ? parsed as Record<string, unknown> : null
  } catch {
    return null
  }
}

export function skuDisplayName(sku: MallSku, index = 0) {
  const spec = parseSpec(sku.spec)
  const values = spec ? Object.values(spec).filter((value) => value !== null && value !== '') : []
  return values.length ? values.join(' · ') : index === 0 ? '默认规格' : `规格 ${index + 1}`
}
