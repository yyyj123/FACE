<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import { useAuthStore } from '../stores/auth'
import type {
  AfterSaleCase, CustomerConfirmation, MallReturnRequest, ServiceReview,
} from '../types/domain'

const auth = useAuthStore()
const route = useRoute()
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const success = ref('')
const confirmations = ref<CustomerConfirmation[]>([])
const reviews = ref<ServiceReview[]>([])
const cases = ref<AfterSaleCase[]>([])
const returns = ref<MallReturnRequest[]>([])
type Section = 'fulfillment' | 'reviews' | 'aftersale' | 'returns'
const sectionQuery = String(route.query.section ?? '')
const activeSection = ref<Section>(
  ['fulfillment', 'reviews', 'aftersale', 'returns'].includes(sectionQuery)
    ? sectionQuery as Section
    : 'fulfillment',
)

const reviewForm = reactive({
  reviewId: undefined as number | undefined,
  serviceRecordId: undefined as number | undefined,
  version: undefined as number | undefined,
  staffRating: 5,
  effectRating: 5,
  environmentRating: 5,
  visibility: 'SHOP_ONLY' as 'PUBLIC' | 'SHOP_ONLY',
  content: '',
  wantsContact: false,
})

const returnForm = reactive({
  orderId: undefined as number | undefined,
  orderItemId: undefined as number | undefined,
  quantity: 1,
  reasonCode: 'QUALITY',
  reasonDetail: '',
})

const pendingConfirmations = computed(() =>
  confirmations.value.filter((item) => item.status === 'PENDING'),
)
const reviewable = computed(() => confirmations.value.filter((confirmation) =>
  ['CONFIRMED', 'SYSTEM_AUTO_CONFIRMED'].includes(confirmation.status)
  && !reviews.value.some((review) =>
    review.serviceRecordId === confirmation.serviceRecordId && !review.deletedAt,
  ),
))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [confirmationData, reviewData, casePage, returnData] = await Promise.all([
      api.confirmations(), api.myReviews(), api.afterSaleCases(auth.session!.shopId),
      api.mallReturns(auth.session!.shopId),
    ])
    confirmations.value = confirmationData
    reviews.value = reviewData
    cases.value = casePage.records
    returns.value = returnData
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '履约与售后数据加载失败'
  } finally {
    loading.value = false
  }
}

async function act(confirmation: CustomerConfirmation, action: 'CONFIRMED' | 'REJECTED') {
  const reason = action === 'REJECTED'
    ? window.prompt('请简要说明异议，门店会据此自动建立售后工单。')?.trim()
    : undefined
  if (action === 'REJECTED' && !reason) return
  busy.value = true; error.value = ''; success.value = ''
  try {
    await api.actOnConfirmation(confirmation.id, {
      action, reason, version: confirmation.version,
      idempotencyKey: crypto.randomUUID(),
    })
    success.value = action === 'CONFIRMED'
      ? '护理已确认，权益与服务积分仅结算一次。'
      : '异议已提交，系统已停止自动完成并建立售后处理。'
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '护理确认失败'
  } finally { busy.value = false }
}

function editReview(review: ServiceReview) {
  reviewForm.reviewId = review.id
  reviewForm.serviceRecordId = review.serviceRecordId
  reviewForm.version = review.version
  reviewForm.staffRating = review.staffRating
  reviewForm.effectRating = review.effectRating
  reviewForm.environmentRating = review.environmentRating
  reviewForm.visibility = review.visibility
  reviewForm.content = review.content ?? ''
  reviewForm.wantsContact = review.wantsContact
  activeSection.value = 'reviews'
}

function resetReview() {
  reviewForm.reviewId = undefined; reviewForm.version = undefined
  reviewForm.serviceRecordId = reviewable.value[0]?.serviceRecordId
  reviewForm.staffRating = 5; reviewForm.effectRating = 5; reviewForm.environmentRating = 5
  reviewForm.visibility = 'SHOP_ONLY'; reviewForm.content = ''; reviewForm.wantsContact = false
}

async function saveReview() {
  if (!reviewForm.serviceRecordId) { error.value = '请选择已确认的护理记录'; return }
  busy.value = true; error.value = ''; success.value = ''
  try {
    await api.saveReview({
      service_record_id: reviewForm.serviceRecordId,
      version: reviewForm.version,
      staff_rating: reviewForm.staffRating,
      effect_rating: reviewForm.effectRating,
      environment_rating: reviewForm.environmentRating,
      visibility: reviewForm.visibility,
      content: reviewForm.content.trim() || undefined,
      wants_contact: reviewForm.wantsContact,
    }, reviewForm.reviewId)
    success.value = reviewForm.reviewId ? '评价新版本已保存。' : '评价已提交。'
    resetReview(); await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '评价保存失败'
  } finally { busy.value = false }
}

async function removeReview(review: ServiceReview) {
  if (!window.confirm('删除后公开内容会隐藏，但版本、审核和售后关联会保留。继续吗？')) return
  busy.value = true
  try {
    await api.deleteReview(review.id, review.version)
    success.value = '评价已逻辑删除，历史版本仍保留。'; await load()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '评价删除失败' }
  finally { busy.value = false }
}

async function respond(item: AfterSaleCase, accepted: boolean) {
  const reason = accepted ? undefined : window.prompt('请说明仍未解决的原因。')?.trim()
  if (!accepted && !reason) return
  busy.value = true
  try {
    await api.respondAfterSale(item.id, {
      shop_id: auth.session!.shopId, version: item.version, accepted, reason,
    })
    success.value = accepted ? '处理结果已确认，工单已关闭。' : '工单已重开一次。'
    await load()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '售后确认失败' }
  finally { busy.value = false }
}

async function submitReturn() {
  if (!returnForm.orderId || !returnForm.orderItemId || !returnForm.reasonDetail.trim()) {
    error.value = '请填写订单、商品行和退换货说明'; return
  }
  busy.value = true
  try {
    await api.createMallReturn({
      shop_id: auth.session!.shopId, mall_order_id: returnForm.orderId,
      reason_code: returnForm.reasonCode, reason_detail: returnForm.reasonDetail.trim(),
      items: [{ order_item_id: returnForm.orderItemId, quantity: returnForm.quantity }],
    })
    success.value = '退换货申请已提交，审核通过后再寄回商品。'
    returnForm.orderId = undefined; returnForm.orderItemId = undefined; returnForm.reasonDetail = ''
    await load()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '退换货申请失败' }
  finally { busy.value = false }
}

async function ship(item: MallReturnRequest) {
  const tracking = window.prompt('请输入真实退货物流单号。')?.trim()
  if (!tracking) return
  busy.value = true
  try {
    await api.shipMallReturn(item.id, {
      shop_id: auth.session!.shopId, version: item.version, tracking_no: tracking,
    })
    success.value = '退货已登记为待验货；验货通过前不会返还积分、款项或运费。'
    await load()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '退货寄回登记失败' }
  finally { busy.value = false }
}

function formatDate(value?: string) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="care-feedback page-container page-section">
    <header class="page-heading">
      <div><span class="page-context">履约与保障</span><h1>护理确认、评价与售后</h1><p>从护理结束确认到评价、售后与退货验货，每一步都有状态和留痕。</p></div>
      <button class="button button-secondary button-small" type="button" :disabled="loading" @click="load">刷新</button>
    </header>

    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
    <p v-if="success" class="notice notice-success" role="status">{{ success }}</p>

    <nav class="section-tabs" aria-label="履约售后分类">
      <button v-for="item in [
        ['fulfillment', `待确认 ${pendingConfirmations.length}`],
        ['reviews', `评价 ${reviews.length}`],
        ['aftersale', `售后 ${cases.length}`],
        ['returns', `退换货 ${returns.length}`],
      ]" :key="item[0]" type="button" :class="{ active: activeSection === item[0] }"
        @click="activeSection = item[0] as typeof activeSection">{{ item[1] }}</button>
    </nav>

    <div v-if="loading" class="loading-list" aria-label="正在加载"><span v-for="n in 3" :key="n" class="skeleton" /></div>

    <section v-else-if="activeSection === 'fulfillment'" class="content-section" aria-labelledby="fulfillment-title">
      <header><h2 id="fulfillment-title">护理结果确认</h2><p>请在护理结束后 24 小时内确认；提出异议会停止自动完成。</p></header>
      <div v-if="pendingConfirmations.length" class="record-list">
        <article v-for="item in pendingConfirmations" :key="item.id" class="record-row">
          <div><strong>{{ item.serviceNames || item.recordNo }}</strong><span>{{ item.staffName }} · 截止 {{ formatDate(item.dueAt) }}</span><p>{{ item.serviceSummary }}</p></div>
          <div class="actions"><button class="button button-primary button-small" type="button" :disabled="busy" @click="act(item, 'CONFIRMED')">确认完成</button><button class="button button-secondary button-small" type="button" :disabled="busy" @click="act(item, 'REJECTED')">提出异议</button></div>
        </article>
      </div>
      <EmptyState v-else title="没有待确认护理" description="新的护理结束后，会在这里显示 24 小时确认任务。" />
    </section>

    <section v-else-if="activeSection === 'reviews'" class="content-section two-column" aria-labelledby="reviews-title">
      <div><header><h2 id="reviews-title">我的评价</h2><p>三项评分必填；公开评价修改后会重新审核。</p></header>
        <div v-if="reviews.length" class="record-list">
          <article v-for="item in reviews" :key="item.id" class="review-row" :class="{ muted: item.deletedAt }">
            <div><strong>{{ Number(item.averageRating).toFixed(1) }} 分</strong><span>{{ item.visibility === 'PUBLIC' ? '公开' : '仅门店' }} · {{ item.moderationStatus }}</span><p>{{ item.deletedAt ? '评价已删除，历史版本保留' : (item.content || '未填写文字评价') }}</p><small v-if="item.replyText">门店回复：{{ item.replyText }}</small></div>
            <div v-if="!item.deletedAt" class="actions"><button class="text-button" type="button" @click="editReview(item)">修改</button><button class="text-button danger" type="button" @click="removeReview(item)">删除</button></div>
          </article>
        </div><EmptyState v-else title="还没有评价" description="确认完成的护理可以在右侧提交评价。" />
      </div>
      <form class="editor" @submit.prevent="saveReview"><h3>{{ reviewForm.reviewId ? '修改评价' : '提交评价' }}</h3>
        <label>护理记录<select v-model="reviewForm.serviceRecordId" :disabled="Boolean(reviewForm.reviewId)"><option :value="undefined">请选择</option><option v-for="item in reviewable" :key="item.id" :value="item.serviceRecordId">{{ item.serviceNames || item.recordNo }}</option></select></label>
        <div class="rating-grid"><label>技师服务<select v-model="reviewForm.staffRating"><option v-for="n in 5" :key="n" :value="n">{{ n }} 分</option></select></label><label>护理效果<select v-model="reviewForm.effectRating"><option v-for="n in 5" :key="n" :value="n">{{ n }} 分</option></select></label><label>门店环境<select v-model="reviewForm.environmentRating"><option v-for="n in 5" :key="n" :value="n">{{ n }} 分</option></select></label></div>
        <label>可见范围<select v-model="reviewForm.visibility"><option value="SHOP_ONLY">仅门店可见</option><option value="PUBLIC">公开展示（需审核）</option></select></label>
        <label>评价内容<textarea v-model="reviewForm.content" maxlength="1000" rows="4" placeholder="可选，请勿填写敏感隐私" /></label>
        <label class="check"><input v-model="reviewForm.wantsContact" type="checkbox" />希望门店主动联系我</label>
        <div class="actions"><button class="button button-primary" type="submit" :disabled="busy">保存评价</button><button v-if="reviewForm.reviewId" class="button button-secondary" type="button" @click="resetReview">取消修改</button></div>
      </form>
    </section>

    <section v-else-if="activeSection === 'aftersale'" class="content-section" aria-labelledby="aftersale-title">
      <header><h2 id="aftersale-title">售后处理</h2><p>处理结果发出后有 48 小时确认期；在原服务售后期限内最多重开一次。</p></header>
      <div v-if="cases.length" class="record-list"><article v-for="item in cases" :key="item.id" class="record-row"><div><strong>{{ item.caseNo }} · {{ item.status }}</strong><span>{{ item.resolutionType || item.originType || item.category }}</span><p>{{ item.resolutionNote || item.summary }}</p><small v-if="item.customerResponseDueAt">确认截止 {{ formatDate(item.customerResponseDueAt) }}</small></div><div v-if="item.status === 'WAITING_CUSTOMER'" class="actions"><button class="button button-primary button-small" type="button" @click="respond(item, true)">确认解决</button><button class="button button-secondary button-small" type="button" @click="respond(item, false)">仍未解决</button></div></article></div>
      <EmptyState v-else title="还没有售后记录" description="护理异议、低分评价或希望联系都会自动关联售后。" />
    </section>

    <section v-else class="content-section two-column" aria-labelledby="returns-title">
      <div><header><h2 id="returns-title">实物退换货</h2><p>先申请，审核通过后寄回；验货通过前不返还资产。</p></header><div v-if="returns.length" class="record-list"><article v-for="item in returns" :key="item.id" class="record-row"><div><strong>{{ item.orderNo || `订单 ${item.mallOrderId}` }}</strong><span>{{ item.status }}</span><p>{{ item.reasonDetail }}</p><small v-if="item.inspectionReason">验货说明：{{ item.inspectionReason }}</small></div><button v-if="item.status === 'APPROVED'" class="button button-secondary button-small" type="button" @click="ship(item)">登记寄回</button></article></div><EmptyState v-else title="还没有退换货申请" description="商城已发货或已完成订单可在右侧发起申请。" /></div>
      <form class="editor" @submit.prevent="submitReturn"><h3>发起退换货</h3><p>订单详情中可查看母订单 ID 与商品行 ID。</p><label>母订单 ID<input v-model.number="returnForm.orderId" type="number" min="1" /></label><label>商品行 ID<input v-model.number="returnForm.orderItemId" type="number" min="1" /></label><label>数量<input v-model.number="returnForm.quantity" type="number" min="1" /></label><label>原因<select v-model="returnForm.reasonCode"><option value="QUALITY">质量问题</option><option value="WRONG_ITEM">错发商品</option><option value="DAMAGED">运输破损</option><option value="OTHER">其他</option></select></label><label>详细说明<textarea v-model="returnForm.reasonDetail" maxlength="500" rows="4" /></label><button class="button button-primary" type="submit" :disabled="busy">提交申请</button></form>
    </section>
  </div>
</template>

<style scoped>
.care-feedback{padding-bottom:92px}.page-context{color:var(--copper);font-size:13px;font-weight:700}.section-tabs{display:flex;gap:4px;margin:24px 0;border-bottom:1px solid var(--line-strong);overflow-x:auto}.section-tabs button{min-height:44px;padding:0 18px;border:0;border-bottom:2px solid transparent;background:transparent;color:var(--text-soft);white-space:nowrap}.section-tabs button.active{border-color:var(--rose);color:var(--text);font-weight:700}.content-section{display:grid;gap:20px}.content-section>header h2,.editor h3{margin:0}.content-section>header p,.editor>p{margin:6px 0 0;color:var(--text-muted)}.two-column{grid-template-columns:minmax(0,1.45fr) minmax(290px,.65fr);align-items:start;gap:24px}.record-list{border-top:1px solid var(--line-strong)}.record-row,.review-row{display:flex;justify-content:space-between;gap:24px;padding:20px 0;border-bottom:1px solid var(--line)}.record-row>div:first-child,.review-row>div:first-child{display:grid;gap:6px}.record-row span,.review-row span,.record-row small,.review-row small{color:var(--text-muted);font-size:12px}.record-row p,.review-row p{margin:0;color:var(--text-soft);line-height:1.6}.actions{display:flex;flex-wrap:wrap;align-items:center;gap:8px}.editor{display:grid;gap:14px;padding:22px;border:1px solid var(--line-strong);background:var(--surface)}.editor label{display:grid;gap:7px;color:var(--text-soft);font-size:13px;font-weight:700}.editor select,.editor input[type=number],.editor textarea{width:100%;min-height:44px;padding:9px 11px;border:1px solid var(--line-strong);border-radius:8px;background:var(--surface);color:var(--text)}.editor textarea{resize:vertical;line-height:1.55}.rating-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.editor .check{display:flex;grid-template-columns:auto 1fr;align-items:center}.loading-list{display:grid;gap:12px}.loading-list .skeleton{min-height:92px}.muted{opacity:.65}.danger{color:var(--danger)}
@media(max-width:800px){.two-column{grid-template-columns:1fr}.record-row,.review-row{align-items:flex-start;flex-direction:column}.rating-grid{grid-template-columns:1fr}.editor{padding:16px}}
</style>
