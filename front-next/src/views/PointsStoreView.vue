<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { Close, Delete as DeleteIcon, Minus, Plus, Search } from '@element-plus/icons-vue'
import { api } from '../api/client'
import { mediaUrl } from '../utils/format'
import {
  availablePurchaseOptions,
  purchaseActionLabel,
  purchaseModeTitle,
  resolvePurchaseMode,
  skuDisplayName,
  type MallSku,
  type PurchaseMode,
} from '../utils/mall'

interface MallProduct {
  id: number
  productType: string
  name: string
  brandName?: string
  categoryName: string
  coverUrl?: string
  description?: string
  deliveryMode: 'DELIVERY' | 'PICKUP' | 'BOTH' | 'DIGITAL'
  skus: MallSku[]
}

interface CatalogResponse {
  products: MallProduct[]
  coupons: Array<Record<string, unknown>>
}

interface CartItem {
  id: number
  skuId: number
  productName: string
  spec?: MallSku['spec']
  purchaseMode: PurchaseMode
  quantity: number
  deliveryMode: 'DELIVERY' | 'PICKUP' | 'DIGITAL'
  pickupShopId?: number | null
  availableQuantity: number
}

interface CartResponse {
  items: CartItem[]
  inventoryReserved?: boolean
}

const loading = ref(true)
const acting = ref(false)
const tab = ref<'store' | 'points' | 'cart'>('store')
const points = ref<Record<string, any>>({})
const catalog = ref<CatalogResponse>({ products: [], coupons: [] })
const cart = ref<CartResponse>({ items: [] })
const lastOrder = ref<Record<string, any> | null>(null)
const notice = ref('')
const cartQuery = ref('')
const openModeSkuId = ref<number | null>(null)
const selectedProduct = ref<MallProduct | null>(null)
const selectedSkuId = ref<number | null>(null)
const selectedMode = ref<PurchaseMode | null>(null)
const detailPanel = ref<HTMLElement | null>(null)
const detailCloseButton = ref<HTMLButtonElement | null>(null)
const detailReturnFocus = ref<HTMLElement | null>(null)

const balance = computed(() => points.value.account?.availablePoints ?? 0)
const cartCount = computed(() => cart.value.items.reduce(
  (sum, item) => sum + Number(item.quantity || 0), 0,
))
const filteredCartItems = computed(() => {
  const query = cartQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return cart.value.items
  return cart.value.items.filter((item) => {
    const spec = skuDisplayName({ id: item.skuId, availableQuantity: item.availableQuantity, spec: item.spec })
    return `${item.productName} ${spec} ${purchaseModeTitle(item.purchaseMode)}`
      .toLocaleLowerCase('zh-CN')
      .includes(query)
  })
})
const selectedSku = computed(() => selectedProduct.value?.skus.find(
  (sku) => sku.id === selectedSkuId.value,
) ?? null)

async function load() {
  loading.value = true
  try {
    const [pointsData, catalogData, cartData] = await Promise.all([
      api.points(), api.mallCatalog(), api.mallCart(),
    ])
    points.value = pointsData
    catalog.value = catalogData as unknown as CatalogResponse
    cart.value = cartData as unknown as CartResponse
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '积分商城加载失败'
  } finally {
    loading.value = false
  }
}

function purchaseOptions(sku: MallSku | null | undefined) {
  return availablePurchaseOptions(sku)
}

function mainPurchaseOption(sku: MallSku | null | undefined) {
  return purchaseOptions(sku)[0] ?? null
}

function cardSku(product: MallProduct) {
  return product.skus.find((sku) => purchaseOptions(sku).length > 0) ?? product.skus[0]
}

function deliveryFor(product: MallProduct): CartItem['deliveryMode'] {
  return product.deliveryMode === 'DIGITAL' ? 'DIGITAL' : 'DELIVERY'
}

function existingCartQuantity(product: MallProduct, sku: MallSku, mode: PurchaseMode) {
  const deliveryMode = deliveryFor(product)
  return cart.value.items.find((item) => (
    item.skuId === sku.id
    && item.purchaseMode === mode
    && item.deliveryMode === deliveryMode
  ))?.quantity ?? 0
}

function existingCartItem(product: MallProduct, sku: MallSku, mode: PurchaseMode) {
  const deliveryMode = deliveryFor(product)
  return cart.value.items.find((item) => (
    item.skuId === sku.id
    && item.purchaseMode === mode
    && item.deliveryMode === deliveryMode
  ))
}

async function add(product: MallProduct, sku: MallSku, mode: PurchaseMode) {
  if (!purchaseOptions(sku).some((option) => option.mode === mode)) {
    notice.value = '该规格暂不支持所选购买方式'
    return false
  }
  const currentItem = existingCartItem(product, sku, mode)
  const quantity = existingCartQuantity(product, sku, mode) + 1
  if (quantity > Number(sku.availableQuantity || 0)) {
    notice.value = '购买数量不能超过当前库存'
    return false
  }
  acting.value = true
  try {
    cart.value = currentItem
      ? await api.updateMallCartItem(currentItem.id, quantity) as unknown as CartResponse
      : await api.putMallCart({
        sku_id: sku.id,
        purchase_mode: mode,
        quantity,
        delivery_mode: deliveryFor(product),
      }) as unknown as CartResponse
    notice.value = '已加入购物车，下单时会重新校验库存与价格'
    return true
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '加入购物车失败'
    return false
  } finally {
    acting.value = false
  }
}

function showModePopover(sku: MallSku | undefined) {
  if (sku && window.matchMedia('(hover: hover)').matches) openModeSkuId.value = sku.id
}

function hideModePopover() {
  if (window.matchMedia('(hover: hover)').matches) openModeSkuId.value = null
}

function toggleModePopover(sku: MallSku | undefined) {
  if (!sku) return
  openModeSkuId.value = openModeSkuId.value === sku.id ? null : sku.id
}

function openProduct(product: MallProduct, event?: Event, preferredMode?: PurchaseMode) {
  if (event?.currentTarget instanceof HTMLElement) detailReturnFocus.value = event.currentTarget
  selectedProduct.value = product
  const firstSku = cardSku(product)
  selectedSkuId.value = firstSku?.id ?? null
  selectedMode.value = resolvePurchaseMode(firstSku, preferredMode ?? selectedMode.value)
  openModeSkuId.value = null
  document.body.style.overflow = 'hidden'
  nextTick(() => detailCloseButton.value?.focus())
}

function inspectMode(product: MallProduct, sku: MallSku | undefined, mode: PurchaseMode, event: Event) {
  if (!sku) return
  openProduct(product, event, mode)
  selectedSkuId.value = sku.id
  selectedMode.value = resolvePurchaseMode(sku, mode)
}

function closeProduct(returnFocus = true) {
  selectedProduct.value = null
  selectedSkuId.value = null
  selectedMode.value = null
  document.body.style.overflow = ''
  if (returnFocus) nextTick(() => detailReturnFocus.value?.focus())
}

function selectSku(skuId: number) {
  selectedSkuId.value = skuId
  selectedMode.value = resolvePurchaseMode(selectedProduct.value?.skus.find((sku) => sku.id === skuId), selectedMode.value)
}

async function buySelected() {
  if (!selectedProduct.value || !selectedSku.value || !selectedMode.value) return
  const added = await add(selectedProduct.value, selectedSku.value, selectedMode.value)
  if (added) {
    closeProduct(false)
    tab.value = 'cart'
  }
}

async function changeCartQuantity(item: CartItem, delta: number) {
  const quantity = Number(item.quantity) + delta
  if (quantity <= 0) {
    await removeCartItem(item)
    return
  }
  if (quantity > Number(item.availableQuantity || 0)) {
    notice.value = '购买数量不能超过当前库存'
    return
  }
  acting.value = true
  try {
    cart.value = await api.updateMallCartItem(item.id, quantity) as unknown as CartResponse
    notice.value = '购物车数量已更新'
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '更新购物车失败'
  } finally {
    acting.value = false
  }
}

async function removeCartItem(item: CartItem) {
  acting.value = true
  try {
    cart.value = await api.removeMallCartItem(item.id) as unknown as CartResponse
    notice.value = `已从购物车移除“${item.productName}”`
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '删除购物车商品失败'
  } finally {
    acting.value = false
  }
}

async function checkin() {
  acting.value = true
  try {
    const result = await api.pointsCheckin()
    notice.value = `签到成功，获得 ${result.rewardPoints} 积分`
    points.value = await api.points()
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '签到失败'
  } finally {
    acting.value = false
  }
}

async function checkout() {
  acting.value = true
  try {
    lastOrder.value = await api.createMallCheckout({ payment_method: 'DEMO_MOCK' })
    cart.value = await api.mallCart() as unknown as CartResponse
    points.value = await api.points()
    notice.value = lastOrder.value.status === 'PAID' ? '兑换成功' : '订单已创建，请完成支付'
  } catch (error) {
    notice.value = error instanceof Error ? error.message : '结算失败'
  } finally {
    acting.value = false
  }
}

function handleDocumentPointer(event: PointerEvent) {
  if (event.target instanceof Element && !event.target.closest('.purchase-mode-control')) {
    openModeSkuId.value = null
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    if (selectedProduct.value) closeProduct()
    else openModeSkuId.value = null
    return
  }
  if (event.key !== 'Tab' || !selectedProduct.value || !detailPanel.value) return
  const focusable = Array.from(detailPanel.value.querySelectorAll<HTMLElement>(
    'button:not(:disabled), [href], input:not(:disabled), [tabindex]:not([tabindex="-1"])',
  ))
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}

onMounted(() => {
  load()
  document.addEventListener('pointerdown', handleDocumentPointer)
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', handleDocumentPointer)
  document.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <section class="store-page" v-loading="loading">
    <div class="store-hero">
      <div>
        <h1>积分商城</h1>
        <p>现金、积分或混合支付，价格与库存均以提交订单时的服务端校验为准。</p>
      </div>
      <div class="balance-card">
        <span>可用积分</span>
        <strong>{{ balance.toLocaleString() }}</strong>
        <small v-if="points.expiringIn30Days">其中 {{ points.expiringIn30Days }} 积分将在 30 天内到期</small>
      </div>
    </div>

    <div class="store-tabs" role="tablist" aria-label="积分商城导航">
      <button :class="{ active: tab === 'store' }" type="button" @click="tab = 'store'">选购</button>
      <button :class="{ active: tab === 'points' }" type="button" @click="tab = 'points'">积分明细</button>
      <button :class="{ active: tab === 'cart' }" type="button" @click="tab = 'cart'">购物车 {{ cartCount }}</button>
    </div>
    <p v-if="notice" class="store-notice" role="status" aria-live="polite">{{ notice }}</p>

    <div v-if="tab === 'store'" class="product-grid">
      <article v-for="product in catalog.products" :key="product.id" class="product-card">
        <button class="product-visual" type="button" :style="{ backgroundImage: `url(${mediaUrl(product.coverUrl, product.id)})` }" :aria-label="`查看${product.name}详情`" @click="openProduct(product, $event)">
          <span>{{ product.productType === 'CARE_ENTITLEMENT' ? '护理兑换' : product.categoryName }}</span>
        </button>
        <div class="product-copy">
          <small>{{ product.brandName || product.categoryName }}</small>
          <button class="product-title" type="button" @click="openProduct(product, $event)">{{ product.name }}</button>
          <p>{{ product.description || '到店护理与居家好物，库存与门店系统实时同步。' }}</p>
          <div class="product-price-row">
            <strong v-if="mainPurchaseOption(cardSku(product))" class="product-main-price">{{ mainPurchaseOption(cardSku(product))?.priceLabel }}</strong>
            <strong v-else class="product-unavailable">暂不可购买</strong>
            <div v-if="purchaseOptions(cardSku(product)).length > 1" class="purchase-mode-control" @mouseenter="showModePopover(cardSku(product))" @mouseleave="hideModePopover">
              <button class="purchase-mode-trigger" type="button" aria-haspopup="dialog" :aria-expanded="openModeSkuId === cardSku(product)?.id" @click.stop="toggleModePopover(cardSku(product))">{{ purchaseOptions(cardSku(product)).length }}种购买方式</button>
              <div v-if="openModeSkuId === cardSku(product)?.id" class="purchase-mode-popover" role="dialog" :aria-label="`${product.name}购买方式`">
                <strong>购买方式</strong>
                <button v-for="option in purchaseOptions(cardSku(product))" :key="option.mode" type="button" @click.stop="inspectMode(product, cardSku(product), option.mode, $event)">
                  <span>{{ option.priceLabel }}</span><small>{{ option.title }}</small>
                </button>
              </div>
            </div>
          </div>
          <div class="product-meta-row">
            <span>{{ Number(cardSku(product)?.availableQuantity || 0) > 0 ? `库存 ${cardSku(product)?.availableQuantity}` : '暂时缺货' }}</span>
            <button type="button" @click="openProduct(product, $event)">查看详情</button>
          </div>
        </div>
      </article>
      <div v-if="!catalog.products.length" class="store-empty">商品正在准备中，请稍后再来。</div>
    </div>

    <div v-else-if="tab === 'points'" class="points-layout">
      <article class="task-card">
        <div><small>每日任务</small><h2>连续签到</h2><p>中断后重新从第 1 天开始，完成周期可获得额外奖励。</p></div>
        <button class="button button-primary" type="button" :disabled="acting" @click="checkin">今日签到</button>
      </article>
      <article class="ledger-card">
        <div v-for="entry in points.ledger" :key="entry.id" class="ledger-row">
          <div><strong>{{ entry.reason }}</strong><small>{{ entry.createdAt }}</small></div>
          <span :class="{ positive: entry.pointsDelta > 0 }">{{ entry.pointsDelta > 0 ? '+' : '' }}{{ entry.pointsDelta }}</span>
        </div>
        <p v-if="!points.ledger?.length">暂无积分变动。</p>
      </article>
    </div>

    <div v-else class="cart-layout">
      <div class="cart-list">
        <label class="cart-search"><Search aria-hidden="true" /><input v-model="cartQuery" type="search" placeholder="搜索购物车商品" aria-label="搜索购物车商品"></label>
        <article v-for="item in filteredCartItems" :key="item.id" class="cart-row">
          <div class="cart-item-copy">
            <strong>{{ item.productName }}</strong>
            <small>{{ skuDisplayName({ id: item.skuId, spec: item.spec, availableQuantity: item.availableQuantity }) }} · {{ purchaseModeTitle(item.purchaseMode) }}</small>
            <small>实时库存 {{ item.availableQuantity }}</small>
          </div>
          <div class="cart-item-actions">
            <div class="quantity-stepper" :aria-label="`${item.productName}数量`">
              <button type="button" :disabled="acting" :aria-label="`减少${item.productName}数量`" @click="changeCartQuantity(item, -1)"><Minus /></button>
              <span aria-live="polite">{{ item.quantity }}</span>
              <button type="button" :disabled="acting || item.quantity >= item.availableQuantity" :aria-label="`增加${item.productName}数量`" @click="changeCartQuantity(item, 1)"><Plus /></button>
            </div>
            <button class="cart-remove" type="button" :disabled="acting" :aria-label="`删除${item.productName}`" @click="removeCartItem(item)"><DeleteIcon />删除</button>
          </div>
        </article>
        <p v-if="!cart.items.length" class="cart-empty">购物车还是空的。选择商品和购买方式后即可加入。</p>
        <p v-else-if="!filteredCartItems.length" class="cart-empty">没有找到相关商品，请尝试其他关键词。</p>
      </div>
      <aside class="checkout-panel">
        <span>结算说明</span>
        <p>提交时重新校验共享库存和价格，并按购买方式与配送规则拆分订单。</p>
        <button class="button button-primary" type="button" :disabled="acting || !cart.items.length" @click="checkout">提交订单</button>
      </aside>
      <article v-if="lastOrder" class="order-result"><small>最近订单</small><strong>{{ lastOrder.orderNo }}</strong><span>{{ lastOrder.status }} · {{ lastOrder.subOrders?.length || 0 }} 个子订单</span></article>
    </div>

    <div v-if="selectedProduct" class="product-detail-overlay" @click.self="closeProduct()">
      <section ref="detailPanel" class="product-detail-panel" role="dialog" aria-modal="true" :aria-labelledby="`product-detail-${selectedProduct.id}`">
        <button ref="detailCloseButton" class="detail-close" type="button" aria-label="关闭商品详情" @click="closeProduct()"><Close /></button>
        <img class="detail-image" :src="mediaUrl(selectedSku?.imageUrl || selectedProduct.coverUrl, selectedProduct.id)" :alt="selectedProduct.name">
        <div class="detail-copy">
          <small>{{ selectedProduct.brandName || selectedProduct.categoryName }}</small>
          <h2 :id="`product-detail-${selectedProduct.id}`">{{ selectedProduct.name }}</h2>
          <p>{{ selectedProduct.description || '商品价格与库存以提交订单时的服务端校验为准。' }}</p>
          <fieldset class="sku-selector">
            <legend>选择规格</legend>
            <button v-for="(sku, index) in selectedProduct.skus" :key="sku.id" type="button" :class="{ active: selectedSkuId === sku.id }" :aria-pressed="selectedSkuId === sku.id" @click="selectSku(sku.id)">{{ skuDisplayName(sku, index) }}</button>
          </fieldset>
          <fieldset class="purchase-choice-list">
            <legend>购买方式</legend>
            <button v-for="option in purchaseOptions(selectedSku)" :key="option.mode" type="button" :class="{ active: selectedMode === option.mode }" role="radio" :aria-checked="selectedMode === option.mode" @click="selectedMode = option.mode">
              <span class="choice-indicator" aria-hidden="true" /><span><strong>{{ option.priceLabel }}</strong><small>{{ option.title }}</small></span>
            </button>
            <p v-if="!purchaseOptions(selectedSku).length">该规格暂未配置有效购买方式。</p>
          </fieldset>
          <div class="detail-purchase-bar">
            <span>{{ selectedSku && selectedSku.availableQuantity > 0 ? `库存 ${selectedSku.availableQuantity}` : '暂时缺货' }}</span>
            <button class="button button-primary" type="button" :disabled="acting || !selectedMode || !selectedSku || selectedSku.availableQuantity < 1" @click="buySelected">{{ purchaseActionLabel(selectedSku, selectedMode) }}</button>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.store-page{max-width:1180px;margin:0 auto;padding:42px 24px 80px}.store-notice{padding:12px 16px;background:#faf4f1;color:var(--text)}.store-hero{display:grid;grid-template-columns:1.5fr .7fr;gap:24px;align-items:end;padding:34px;border:1px solid var(--line);background:#fff}.store-hero h1{margin:0;font-size:clamp(36px,6vw,66px);font-weight:500;letter-spacing:-.035em}.store-hero p{max-width:620px;margin:12px 0 0;color:var(--text-muted)}.balance-card{min-height:170px;padding:24px;display:flex;flex-direction:column;justify-content:flex-end;background:#241b1e;color:#fff}.balance-card strong{font-size:42px;font-weight:500;font-variant-numeric:tabular-nums}.balance-card small{color:#d9c9ce}.store-tabs{display:flex;gap:8px;margin:26px 0}.store-tabs button{min-height:44px;padding:0 18px;border:1px solid var(--line);background:#fff;color:var(--text);cursor:pointer}.store-tabs button.active{border-color:#241b1e;background:#241b1e;color:#fff}.product-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px}.product-card{overflow:visible;border:1px solid var(--line);background:#fff}.product-visual{width:100%;height:200px;padding:18px;border:0;background:linear-gradient(145deg,#eee4df,#faf7f5);background-size:cover;background-position:center;text-align:left;cursor:pointer}.product-visual span{display:inline-flex;padding:6px 10px;background:rgba(255,255,255,.92);font-size:12px}.product-copy{padding:22px}.product-title{display:block;margin:4px 0 8px;padding:0;border:0;background:none;color:var(--text);font:inherit;font-size:24px;font-weight:650;text-align:left;cursor:pointer}.product-copy>p{min-height:44px;margin:0;color:var(--text-muted)}.product-price-row{display:flex;align-items:center;justify-content:space-between;gap:16px;padding-top:18px;margin-top:18px;border-top:1px solid var(--line)}.product-main-price{font-size:24px;font-weight:700;font-variant-numeric:tabular-nums;letter-spacing:-.02em}.product-unavailable{font-size:16px;color:var(--text-muted)}.purchase-mode-control{position:relative}.purchase-mode-trigger{min-height:36px;padding:0 12px;border:1px solid #dcc2ca;border-radius:999px;background:#fbf6f7;color:var(--primary);font-size:13px;cursor:pointer}.purchase-mode-popover{position:absolute;z-index:20;right:0;bottom:calc(100% + 10px);width:224px;padding:14px;background:#fff;box-shadow:0 12px 30px rgba(36,27,30,.16)}.purchase-mode-popover>strong{display:block;margin-bottom:8px}.purchase-mode-popover>button{display:flex;width:100%;align-items:flex-start;justify-content:space-between;gap:12px;padding:10px 4px;border:0;border-top:1px solid var(--line);background:#fff;color:var(--text);text-align:left;cursor:pointer}.purchase-mode-popover>button span{font-weight:650}.purchase-mode-popover>button small{color:var(--text-muted)}.product-meta-row{display:flex;align-items:center;justify-content:space-between;margin-top:14px;color:var(--text-muted);font-size:13px}.product-meta-row button{min-height:36px;padding:0;border:0;background:none;color:var(--primary);cursor:pointer}.points-layout,.cart-layout{display:grid;grid-template-columns:.8fr 1.2fr;gap:20px}.task-card,.ledger-card,.cart-list,.checkout-panel,.order-result{padding:24px;border:1px solid var(--line);background:#fff}.task-card{display:flex;flex-direction:column;justify-content:space-between;min-height:240px}.ledger-row{display:flex;justify-content:space-between;gap:18px;padding:14px 0;border-bottom:1px solid var(--line)}.ledger-row>div{display:flex;flex-direction:column}.ledger-row small,.cart-row small{color:var(--text-muted)}.ledger-row>span{color:var(--text-muted);font-variant-numeric:tabular-nums}.ledger-row>span.positive{color:#49745d}.cart-layout{grid-template-columns:1.15fr .85fr}.cart-search{display:flex;align-items:center;gap:10px;margin-bottom:12px;padding:0 12px;border:1px solid var(--line);background:#fff}.cart-search svg{width:18px;color:var(--text-muted)}.cart-search input{width:100%;min-height:46px;border:0;outline:0;background:transparent;color:var(--text);font:inherit}.cart-row{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:18px 0;border-bottom:1px solid var(--line)}.cart-item-copy{display:flex;min-width:0;flex-direction:column;gap:3px}.cart-item-actions{display:flex;align-items:center;gap:16px}.quantity-stepper{display:grid;grid-template-columns:38px 38px 38px;align-items:center;border:1px solid var(--line)}.quantity-stepper button{display:grid;width:38px;height:38px;place-items:center;border:0;background:#fff;color:var(--text);cursor:pointer}.quantity-stepper button:disabled{opacity:.4;cursor:not-allowed}.quantity-stepper svg{width:16px}.quantity-stepper span{text-align:center;font-variant-numeric:tabular-nums}.cart-remove{display:flex;min-height:38px;align-items:center;gap:5px;padding:0 4px;border:0;background:none;color:var(--text-muted);cursor:pointer}.cart-remove svg{width:16px}.cart-remove:hover{color:#a13e52}.cart-empty{padding:24px 0;color:var(--text-muted);text-align:center}.checkout-panel{align-self:start}.checkout-panel .button{width:100%;margin-top:16px}.order-result{grid-column:1/-1;display:flex;gap:18px;align-items:center}.store-empty{grid-column:1/-1;padding:54px;text-align:center;border:1px dashed var(--line);color:var(--text-muted)}.product-detail-overlay{position:fixed;z-index:1200;inset:0;display:grid;place-items:center;padding:24px;background:rgba(36,27,30,.5)}.product-detail-panel{position:relative;display:grid;width:min(880px,100%);max-height:min(760px,calc(100vh - 48px));grid-template-columns:.86fr 1.14fr;overflow:auto;background:#fff;box-shadow:0 24px 64px rgba(36,27,30,.24)}.detail-close{position:absolute;z-index:2;top:14px;right:14px;display:grid;width:44px;height:44px;place-items:center;border:0;border-radius:50%;background:#fff;color:var(--text);box-shadow:0 6px 18px rgba(36,27,30,.14);cursor:pointer}.detail-close svg{width:20px}.detail-image{display:block;width:100%;height:100%;min-height:100%;object-fit:cover;background:#eee4df}.detail-copy{padding:42px}.detail-copy>small{color:var(--primary)}.detail-copy h2{margin:5px 0 12px;font-size:34px;letter-spacing:-.025em}.detail-copy>p{margin:0 0 28px;color:var(--text-muted)}.sku-selector,.purchase-choice-list{padding:0;margin:0 0 26px;border:0}.sku-selector legend,.purchase-choice-list legend{margin-bottom:12px;font-weight:650}.sku-selector>button{min-height:42px;margin:0 8px 8px 0;padding:0 14px;border:1px solid var(--line);background:#fff;color:var(--text);cursor:pointer}.sku-selector>button.active{border-color:var(--primary);background:#fbf6f7;color:var(--primary)}.purchase-choice-list{display:grid;gap:8px}.purchase-choice-list>button{display:flex;align-items:center;gap:12px;padding:12px;border:1px solid var(--line);background:#fff;color:var(--text);text-align:left;cursor:pointer}.purchase-choice-list>button.active{border-color:var(--primary);background:#fbf6f7}.purchase-choice-list>button>span:last-child{display:flex;flex-direction:column}.purchase-choice-list small{margin-top:2px;color:var(--text-muted)}.choice-indicator{width:18px;height:18px;flex:0 0 auto;border:1px solid #a99da1;border-radius:50%}.purchase-choice-list>button.active .choice-indicator{border:5px solid var(--primary)}.detail-purchase-bar{display:grid;grid-template-columns:auto 1fr;align-items:center;gap:16px;padding-top:18px;border-top:1px solid var(--line)}.detail-purchase-bar>span{color:var(--text-muted);font-size:13px}.detail-purchase-bar .button{min-height:50px}.button:disabled{opacity:.45;cursor:not-allowed}.product-card button:focus-visible,.cart-list button:focus-visible,.product-detail-panel button:focus-visible,.store-tabs button:focus-visible{outline:3px solid rgba(168,79,100,.28);outline-offset:3px}
@media(max-width:760px){.store-page{padding:24px 16px 110px}.store-hero,.product-grid,.points-layout,.cart-layout{grid-template-columns:1fr}.store-hero{padding:24px}.balance-card{min-height:130px}.store-tabs{overflow-x:auto}.store-tabs button{white-space:nowrap}.product-visual{height:180px}.product-copy{padding:18px}.product-title{font-size:22px}.product-copy>p{min-height:0}.product-price-row{align-items:flex-end}.purchase-mode-popover{position:fixed;z-index:1300;right:12px;bottom:86px;left:12px;width:auto;padding:18px}.purchase-mode-popover>button{min-height:54px}.cart-list,.checkout-panel{padding:18px}.cart-row{align-items:flex-start;flex-direction:column}.cart-item-actions{width:100%;justify-content:space-between}.product-detail-overlay{align-items:end;padding:0}.product-detail-panel{width:100%;max-height:min(82vh,720px);grid-template-columns:1fr;border-radius:14px 14px 0 0}.detail-image{height:220px;min-height:220px}.detail-copy{padding:26px 20px calc(22px + env(safe-area-inset-bottom))}.detail-copy h2{font-size:28px}.detail-purchase-bar{position:sticky;bottom:0;margin:0 -20px -22px;padding:14px 20px calc(14px + env(safe-area-inset-bottom));background:#fff}.detail-purchase-bar .button{min-width:0}.order-result{grid-column:auto;align-items:flex-start;flex-direction:column;gap:5px}}
@media(max-height:560px) and (orientation:landscape){.product-detail-panel{max-height:100vh;grid-template-columns:.72fr 1.28fr;border-radius:0}.detail-image{height:100%;min-height:100%}.detail-copy{padding:24px}.purchase-mode-popover{bottom:12px;max-height:calc(100vh - 24px);overflow-y:auto}}
@media(prefers-reduced-motion:reduce){.product-card button,.product-detail-panel button{transition:none}}
</style>
