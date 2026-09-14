<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Check, Lock } from '@element-plus/icons-vue'
import { api, type CheckoutQuote, type CheckoutResult, type ClientBenefitCard } from '../api/client'
import { money } from '../utils/format'

const route = useRoute()
const router = useRouter()
const quote = ref<CheckoutQuote>()
const benefits = ref<ClientBenefitCard[]>([])
const selectedOptionKey = ref('NONE:')
const paymentMethod = ref('')
const storedValueCardId = ref<number>()
const comboCardId = ref<number>()
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const result = ref<CheckoutResult>()
const lockReleased = ref(false)

const lockToken = computed(() => typeof route.query.lock === 'string' ? route.query.lock : undefined)
const packageProductId = computed(() => {
  const value = Number(route.query.card)
  return Number.isInteger(value) && value > 0 ? value : undefined
})
const selectedOption = computed(() => quote.value?.discountOptions.find(
  (option) => `${option.selectionType}:${option.referenceId ?? ''}` === selectedOptionKey.value,
))
const payable = computed(() => comboCardId.value ? 0 : Number(selectedOption.value?.payableAmount ?? quote.value?.item.subtotalAmount ?? 0))
const storedValueCards = computed(() => benefits.value.filter((card) => card.cardType === 'STORED_VALUE' && card.status === 'ACTIVE'))
const selectedChannel = computed(() => quote.value?.paymentChannels.find((channel) => channel.code === paymentMethod.value))
const canSubmit = computed(() => Boolean(
  quote.value && selectedOption.value && paymentMethod.value && selectedChannel.value?.configured
  && (paymentMethod.value !== 'STORED_VALUE' || storedValueCardId.value) && !submitting.value,
))

onMounted(load)

onBeforeUnmount(() => {
  if (lockToken.value && !result.value && !lockReleased.value) {
    lockReleased.value = true
    void api.releaseBookingLock(lockToken.value, 'CHECKOUT_ABANDONED')
  }
})

async function load() {
  if (!lockToken.value && !packageProductId.value) {
    error.value = '缺少待结算的预约或卡项'
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [quoteData, benefitData] = await Promise.all([
      api.checkoutQuote({ lock_token: lockToken.value, package_product_id: packageProductId.value }),
      api.clientBenefits(),
    ])
    quote.value = quoteData
    benefits.value = benefitData.cards
    selectedOptionKey.value = `${quoteData.defaultSelection}:`
    if (quoteData.item.subtotalAmount === 0) paymentMethod.value = 'ZERO_AMOUNT'
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '结算信息加载失败'
  } finally {
    loading.value = false
  }
}

function selectDiscount(selectionType: string, referenceId?: number) {
  comboCardId.value = undefined
  selectedOptionKey.value = `${selectionType}:${referenceId ?? ''}`
  if (Number(selectedOption.value?.payableAmount ?? 0) === 0) paymentMethod.value = 'ZERO_AMOUNT'
  else if (paymentMethod.value === 'ZERO_AMOUNT') paymentMethod.value = ''
}

function selectCombo(cardId?: number) {
  comboCardId.value = cardId
  selectedOptionKey.value = 'NONE:'
  paymentMethod.value = cardId ? 'ZERO_AMOUNT' : ''
}

async function submit() {
  if (!canSubmit.value || !quote.value || !selectedOption.value) return
  submitting.value = true
  error.value = ''
  try {
    result.value = await api.createCheckout({
      lock_token: lockToken.value,
      package_product_id: packageProductId.value,
      selection_type: selectedOption.value.selectionType,
      selection_reference_id: selectedOption.value.referenceId,
      payment_method: paymentMethod.value,
      stored_value_card_id: storedValueCardId.value,
      combo_card_id: comboCardId.value,
      member_note: typeof route.query.note === 'string' ? route.query.note : undefined,
    }, crypto.randomUUID())
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '订单提交失败'
  } finally {
    submitting.value = false
  }
}

function channelLabel(code: string) {
  return ({
    STORED_VALUE: '储值卡', DEMO_MOCK: 'DEMO 模拟支付', WECHAT: '微信支付',
    ALIPAY: '支付宝', AGGREGATOR: '聚合支付', ZERO_AMOUNT: '零元成单',
  } as Record<string, string>)[code] ?? code
}

function cardAvailable(card: ClientBenefitCard) {
  return Number(card.principalRemaining || 0) + Number(card.giftRemaining || 0)
    - Number(card.principalFrozen || 0) - Number(card.giftFrozen || 0)
}
</script>

<template>
  <div class="checkout-page page-container">
    <button class="back-link" type="button" @click="router.back()"><ArrowLeft />返回</button>
    <header class="page-heading checkout-heading">
      <div><span class="eyebrow">SECURE CHECKOUT</span><h1>确认订单与支付</h1><p>金额和优惠由服务端重新计算；默认不使用任何优惠。</p></div>
      <span class="security-note"><Lock />统一支付与幂等保护</span>
    </header>

    <div v-if="loading" class="checkout-skeleton skeleton" aria-label="正在加载结算信息" />
    <p v-else-if="error && !quote" class="notice notice-error" role="alert">{{ error }}</p>

    <section v-else-if="result" class="result-panel panel" aria-live="polite">
      <span class="result-icon"><Check /></span>
      <span class="eyebrow">{{ result.payment.status === 'SUCCESS' ? 'ORDER CONFIRMED' : 'PAYMENT PENDING' }}</span>
      <h1>{{ result.payment.status === 'SUCCESS' ? '订单已确认' : '支付请求已创建' }}</h1>
      <p v-if="result.payment.status === 'SUCCESS'">订单 {{ result.orderNo }} 已完成支付；预约或卡项会显示在你的账户中。</p>
      <p v-else>订单 {{ result.orderNo }} 正在等待支付回调。重复回调不会重复发卡或创建预约。</p>
      <dl><div><dt>订单金额</dt><dd>{{ money(result.payableAmount) }}</dd></div><div><dt>支付方式</dt><dd>{{ channelLabel(result.payment.paymentMethod) }}</dd></div><div><dt>支付状态</dt><dd>{{ result.payment.status }}</dd></div></dl>
      <div class="result-actions"><router-link class="button button-primary" :to="result.fulfillment?.appointmentId ? '/appointments' : '/benefits'">查看结果</router-link><router-link class="button button-secondary" to="/">返回首页</router-link></div>
    </section>

    <div v-else-if="quote" class="checkout-layout">
      <main class="checkout-main">
        <section class="checkout-section panel">
          <span class="section-index">01</span><div class="section-copy"><h2>订单项目</h2><p>{{ quote.targetType === 'BOOKING' ? '单次护理预约' : '在线购买卡项' }}</p></div>
          <div class="order-line"><div><strong>{{ quote.item.name }}</strong><span>服务端价格快照</span></div><strong>{{ money(quote.item.subtotalAmount) }}</strong></div>
        </section>

        <section class="checkout-section panel">
          <span class="section-index">02</span><div class="section-copy"><h2>选择一种优惠</h2><p>优惠互斥，推荐项仅作提示，不会自动选中。</p></div>
          <div class="option-list" role="radiogroup" aria-label="订单优惠">
            <button v-for="option in quote.discountOptions" :key="`${option.selectionType}:${option.referenceId}`" type="button" :class="{ selected: `${option.selectionType}:${option.referenceId ?? ''}` === selectedOptionKey }" @click="selectDiscount(option.selectionType, option.referenceId)">
              <span><strong>{{ option.name }}</strong><small>{{ option.selectionType === 'NONE' ? '保持原价' : `优惠 ${money(option.discountAmount)}` }}</small></span><span v-if="option.recommended" class="recommend-label">可省最多</span><strong>{{ money(option.payableAmount) }}</strong>
            </button>
          </div>
          <p class="points-note">积分抵扣：{{ quote.points.reason }}</p>
        </section>

        <section class="checkout-section panel">
          <span class="section-index">03</span><div class="section-copy"><h2>选择支付方式</h2><p>一次订单只使用一张储值卡或一个在线支付渠道。</p></div>
          <div v-if="quote.comboCards.length" class="combo-card-list">
            <span>可用次卡权益</span>
            <button type="button" :class="{ selected: !comboCardId }" @click="selectCombo()">本次不使用次卡</button>
            <button v-for="card in quote.comboCards" :key="card.id" type="button" :class="{ selected: comboCardId === card.id }" @click="selectCombo(card.id)">
              <strong>{{ card.name }}</strong><small>可用 {{ card.availableQuantity }} 次 · 本次冻结 1 次</small>
            </button>
          </div>
          <div class="channel-list" role="radiogroup" aria-label="支付方式">
            <label v-for="channel in quote.paymentChannels.filter(item => payable === 0 ? item.code === 'ZERO_AMOUNT' : item.code !== 'ZERO_AMOUNT')" :key="channel.code" :class="{ disabled: !channel.configured, selected: paymentMethod === channel.code }">
              <input v-model="paymentMethod" type="radio" name="payment" :value="channel.code" :disabled="!channel.configured" />
              <span><strong>{{ channelLabel(channel.code) }}</strong><small>{{ channel.message }}</small></span>
            </label>
          </div>
          <label v-if="paymentMethod === 'STORED_VALUE'" class="stored-card-select">选择储值卡<select v-model.number="storedValueCardId" class="form-control"><option :value="undefined">请选择</option><option v-for="card in storedValueCards" :key="card.id" :value="card.id">{{ card.name }} · 可用 {{ money(cardAvailable(card)) }}</option></select></label>
        </section>
        <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
      </main>

      <aside class="checkout-summary panel">
        <span class="eyebrow">PAYMENT SUMMARY</span><h2>应付明细</h2>
        <dl><div><dt>项目金额</dt><dd>{{ money(quote.item.subtotalAmount) }}</dd></div><div><dt>优惠减免</dt><dd>- {{ money(selectedOption?.discountAmount || 0) }}</dd></div><div class="total"><dt>最终应付</dt><dd>{{ money(payable) }}</dd></div></dl>
        <button class="button button-primary" type="button" :disabled="!canSubmit" @click="submit">{{ submitting ? '正在安全提交…' : payable === 0 ? '确认零元订单' : '提交并支付' }}</button>
        <p>提交后不会自动更换优惠或支付通道。通道未开通时，订单、预约和权益均不会改变。</p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.checkout-page{padding-bottom:78px}.back-link{display:inline-flex;align-items:center;gap:7px;margin-bottom:22px;padding:0;border:0;background:none;color:var(--text-soft);cursor:pointer}.back-link svg,.security-note svg{width:16px}.checkout-heading{align-items:end}.security-note{display:flex;align-items:center;gap:8px;color:var(--text-muted);font-size:12px}.checkout-skeleton{min-height:520px}.checkout-layout{display:grid;grid-template-columns:minmax(0,1fr) 340px;gap:22px;align-items:start}.checkout-main{display:grid;gap:14px}.checkout-section{display:grid;grid-template-columns:34px 1fr;padding:26px}.section-index{color:var(--copper);font-size:12px;font-weight:700}.section-copy h2{margin:0;font-size:19px}.section-copy p{margin:5px 0 18px;color:var(--text-muted);font-size:12px}.order-line{grid-column:2;display:flex;justify-content:space-between;gap:20px;padding:18px;border:1px solid var(--line);border-radius:8px;background:var(--surface-strong)}.order-line div{display:grid;gap:5px}.order-line span{color:var(--text-muted);font-size:11px}.option-list,.channel-list{grid-column:2;display:grid;gap:9px}.option-list button{display:grid;grid-template-columns:1fr auto auto;align-items:center;gap:14px;padding:14px;border:1px solid var(--line);border-radius:8px;background:var(--surface);color:var(--text);text-align:left;cursor:pointer}.option-list button.selected,.channel-list label.selected,.combo-card-list button.selected{border-color:var(--rose);box-shadow:inset 0 0 0 1px var(--rose);background:rgba(168,79,100,.05)}.option-list button>span:first-child{display:grid;gap:4px}.option-list small,.channel-list small{color:var(--text-muted);font-size:11px}.recommend-label{padding:3px 7px;border-radius:99px;background:rgba(174,112,39,.1);color:var(--copper);font-size:10px}.points-note{grid-column:2;margin:12px 0 0;color:var(--text-muted);font-size:11px}.combo-card-list{grid-column:2;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;margin-bottom:14px}.combo-card-list>span{grid-column:1/-1;color:var(--text-soft);font-size:12px}.combo-card-list button{display:grid;gap:4px;padding:14px;border:1px solid var(--line);border-radius:8px;background:var(--surface);color:var(--text);text-align:left;cursor:pointer}.combo-card-list small{color:var(--text-muted);font-size:11px}.channel-list{grid-template-columns:repeat(2,minmax(0,1fr))}.channel-list label{display:flex;align-items:flex-start;gap:10px;padding:14px;border:1px solid var(--line);border-radius:8px;cursor:pointer}.channel-list label.disabled{opacity:.5;cursor:not-allowed}.channel-list label span{display:grid;gap:4px}.stored-card-select{grid-column:2;display:grid;gap:7px;margin-top:14px;color:var(--text-soft);font-size:12px}.checkout-summary{position:sticky;top:calc(var(--header-height) + 20px);padding:27px}.checkout-summary h2{margin:9px 0 18px}.checkout-summary dl{display:grid;margin:0 0 20px}.checkout-summary dl>div{display:flex;justify-content:space-between;padding:12px 0;border-bottom:1px solid var(--line);font-size:13px}.checkout-summary .total{padding-top:18px;border:0;font-size:16px}.checkout-summary .total dd{color:var(--rose-strong);font-size:24px;font-weight:750}.checkout-summary dd{margin:0}.checkout-summary .button{width:100%}.checkout-summary>p{margin:14px 0 0;color:var(--text-muted);font-size:11px;line-height:1.7}.result-panel{max-width:720px;margin:28px auto;padding:48px;text-align:center}.result-icon{display:grid;width:54px;height:54px;margin:0 auto 18px;place-items:center;border-radius:50%;background:rgba(49,125,91,.1);color:#317d5b}.result-icon svg{width:24px}.result-panel h1{margin:9px 0}.result-panel>p{color:var(--text-muted);line-height:1.7}.result-panel dl{max-width:440px;margin:26px auto;text-align:left}.result-panel dl>div{display:flex;justify-content:space-between;padding:11px 0;border-bottom:1px solid var(--line)}.result-panel dd{margin:0}.result-actions{display:flex;justify-content:center;gap:10px}@media(max-width:900px){.checkout-layout{grid-template-columns:1fr}.checkout-summary{position:static;grid-row:1}}@media(max-width:640px){.checkout-heading{align-items:flex-start}.security-note{display:none}.checkout-section{grid-template-columns:26px 1fr;padding:20px 15px}.order-line{align-items:flex-start;flex-direction:column}.option-list button{grid-template-columns:1fr auto}.recommend-label{grid-column:2}.channel-list,.combo-card-list{grid-template-columns:1fr}.checkout-summary{padding:22px}.result-panel{padding:32px 18px}.result-actions{flex-direction:column}}@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto}}
</style>
