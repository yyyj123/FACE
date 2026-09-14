<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type ClientBenefitCard, type ClientCoupon, type PurchasableCard } from '../api/client'
import EmptyState from '../components/EmptyState.vue'

const cards = ref<ClientBenefitCard[]>([])
const coupons = ref<ClientCoupon[]>([])
const products = ref<PurchasableCard[]>([])
const loading = ref(true)
const error = ref('')
const activeCards = computed(() => cards.value.filter((card) => ['ACTIVE', 'FROZEN'].includes(card.status)))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [benefits, productRows] = await Promise.all([api.clientBenefits(), api.purchasableCards()])
    cards.value = benefits.cards
    coupons.value = benefits.coupons
    products.value = productRows
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '会员权益加载失败'
  } finally {
    loading.value = false
  }
}

function cardTypeLabel(type: ClientBenefitCard['cardType'] | PurchasableCard['cardType']) {
  return { COMBO_TIMES: '次卡', STORED_VALUE: '储值卡', DISCOUNT: '折扣卡' }[type]
}

function statusLabel(status: string) {
  return ({ ACTIVE: '可使用', FROZEN: '已冻结', EXHAUSTED: '已用完', EXPIRED: '已过期', CANCELLED: '已取消', AVAILABLE: '可使用', LOCKED: '已锁定', USED: '已使用', RETURNED: '已返还' } as Record<string, string>)[status] ?? status
}

function sourceLabel(source: string) {
  return ({ ONLINE_PURCHASE: '在线购买', OFFLINE_SALE: '门店销售', GIFT: '赠送', REISSUE: '补发', ADMIN_DIRECT: '门店发放', ACTIVITY_CLAIM: '活动领取', AFTERSALE_COMPENSATION: '售后补偿' } as Record<string, string>)[source] ?? source
}

function formatMoney(value: unknown) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(Number(value || 0))
}

function formatDate(value: unknown) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(String(value)))
}

function storedAvailable(card: ClientBenefitCard) {
  return Number(card.principalRemaining || 0) + Number(card.giftRemaining || 0)
    - Number(card.principalFrozen || 0) - Number(card.giftFrozen || 0)
}
</script>

<template>
  <div class="benefits-page page-container">
    <header class="page-heading">
      <div><span class="page-context">MEMBER BENEFITS</span><h1>我的卡项与优惠券</h1><p>次数、储值、折扣和护理优惠券均显示可用、冻结与来源状态。</p></div>
      <button class="button button-secondary button-small" type="button" :disabled="loading" @click="load">刷新权益</button>
    </header>
    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="asset-skeletons" aria-label="正在加载会员权益"><span v-for="index in 4" :key="index" class="skeleton" /></div>

    <template v-else>
      <section class="benefit-summary" aria-label="权益摘要">
        <div><span>可用卡项</span><strong>{{ activeCards.length }}</strong></div>
        <div><span>可用优惠券</span><strong>{{ coupons.filter(item => ['AVAILABLE','RETURNED'].includes(item.status)).length }}</strong></div>
        <div><span>冻结权益</span><strong>{{ cards.filter(item => item.status === 'FROZEN').length + coupons.filter(item => item.status === 'LOCKED').length }}</strong></div>
      </section>

      <section class="asset-section" aria-labelledby="cards-heading">
        <div class="section-heading"><div><h2 id="cards-heading">我的卡项</h2><p>付款与优惠分开选择，不支持多卡拆分支付。</p></div></div>
        <div v-if="cards.length" class="card-grid">
          <article v-for="card in cards" :key="card.id" class="benefit-card">
            <div class="card-top"><span>{{ cardTypeLabel(card.cardType) }}</span><span :class="['status-chip', `status-chip--${card.status.toLowerCase()}`]">{{ statusLabel(card.status) }}</span></div>
            <h3>{{ card.name }}</h3><p>{{ card.instanceNo }} · {{ sourceLabel(card.sourceType) }}</p>
            <strong v-if="card.cardType === 'COMBO_TIMES'" class="card-value">{{ Number(card.remainingQuantity).toFixed(1) }} <small>/ {{ Number(card.totalQuantity).toFixed(1) }} 次</small></strong>
            <strong v-else-if="card.cardType === 'STORED_VALUE'" class="card-value">{{ formatMoney(storedAvailable(card)) }} <small>可用余额</small></strong>
            <strong v-else class="card-value">{{ Number(card.discountPercent || 100).toFixed(0) }}% <small>支付折扣</small></strong>
            <div class="card-meta"><span>有效至 {{ formatDate(card.validUntil) }}</span><span v-if="Number(card.frozenQuantity || 0)">冻结 {{ card.frozenQuantity }} 次</span><span v-if="card.cardType === 'STORED_VALUE'">本金与赠送金分账记录</span></div>
          </article>
        </div>
        <EmptyState v-else title="还没有可用卡项" description="可从下方选择卡项在线购买，支付成功后只发放一次。" />
      </section>

      <section class="asset-section" aria-labelledby="coupons-heading">
        <div class="section-heading"><div><h2 id="coupons-heading">护理优惠券</h2><p>体验券和金额优惠券在结算时只能选择一张。</p></div></div>
        <div v-if="coupons.length" class="coupon-list">
          <article v-for="coupon in coupons" :key="coupon.id"><div><span>{{ coupon.couponType === 'SERVICE_EXPERIENCE' ? '项目体验' : '护理优惠' }}</span><h3>{{ coupon.name }}</h3><p>{{ coupon.couponNo }} · {{ sourceLabel(coupon.sourceType) }}</p></div><div><strong>{{ coupon.couponType === 'DISCOUNT' ? `${coupon.benefitValue}%` : formatMoney(coupon.benefitValue) }}</strong><span>{{ statusLabel(coupon.status) }} · 至 {{ formatDate(coupon.validUntil) }}</span></div></article>
        </div>
        <EmptyState v-else title="暂无护理优惠券" description="活动领取、门店发放或售后补偿的优惠券会显示在这里。" />
      </section>

      <section class="asset-section" aria-labelledby="shop-heading">
        <div class="section-heading"><div><h2 id="shop-heading">在线购买卡项</h2><p>使用同一订单与支付主线，支付成功后自动发卡。</p></div></div>
        <div v-if="products.length" class="product-list">
          <article v-for="product in products" :key="product.id"><div><span>{{ cardTypeLabel(product.cardType) }}</span><h3>{{ product.name }}</h3><p>{{ product.description || `有效期 ${product.validityDays} 天` }}</p></div><div><strong>{{ formatMoney(product.salePrice) }}</strong><router-link class="button button-primary button-small" :to="{ path: '/checkout', query: { card: product.id } }">立即购买</router-link></div></article>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.benefits-page{padding-bottom:76px}.page-context{color:var(--copper);font-size:12px;font-weight:700;letter-spacing:.08em}.asset-skeletons{display:grid;gap:12px;margin-top:28px}.asset-skeletons .skeleton{min-height:130px}.benefit-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:1px;margin-top:26px;border:1px solid var(--line);background:var(--line)}.benefit-summary div{display:grid;gap:7px;padding:20px;background:var(--surface)}.benefit-summary span{color:var(--text-muted);font-size:12px}.benefit-summary strong{font-size:26px}.asset-section{margin-top:38px;border-top:1px solid var(--line-strong)}.section-heading{display:flex;align-items:end;justify-content:space-between;padding:24px 0 16px}.section-heading h2,.benefit-card h3,.coupon-list h3,.product-list h3{margin:0}.section-heading p{margin:6px 0 0;color:var(--text-muted)}.card-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.benefit-card{display:grid;gap:10px;min-width:0;padding:20px;border:1px solid var(--line);border-radius:9px;background:var(--surface)}.card-top{display:flex;justify-content:space-between;gap:10px;color:var(--copper);font-size:11px;font-weight:700}.benefit-card>p,.coupon-list p,.product-list p{margin:0;color:var(--text-muted);font-size:12px;line-height:1.6}.card-value{margin:8px 0;color:var(--rose-strong);font-size:26px}.card-value small{color:var(--text-muted);font-size:11px}.card-meta{display:grid;gap:4px;padding-top:10px;border-top:1px solid var(--line);color:var(--text-muted);font-size:11px}.coupon-list,.product-list{border-bottom:1px solid var(--line)}.coupon-list article,.product-list article{display:flex;align-items:center;justify-content:space-between;gap:22px;padding:19px 0;border-top:1px solid var(--line)}.coupon-list article>div,.product-list article>div{display:grid;gap:6px}.coupon-list article>div:last-child,.product-list article>div:last-child{text-align:right}.coupon-list article>div:last-child span{color:var(--text-muted);font-size:11px}.coupon-list article>div:first-child>span,.product-list article>div:first-child>span{color:var(--copper);font-size:10px;font-weight:700}.product-list article>div:last-child{justify-items:end}@media(max-width:900px){.card-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.benefit-summary,.card-grid{grid-template-columns:1fr}.coupon-list article,.product-list article{align-items:flex-start;flex-direction:column}.coupon-list article>div:last-child,.product-list article>div:last-child{justify-items:start;text-align:left}}
</style>
