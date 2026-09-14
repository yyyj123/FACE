<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import {
  decideSc6AfterSale, getAfterSaleCases, getSc6MallReturns, getSc6Reviews,
  inspectSc6MallReturn, moderateSc6Review, reviewSc6MallReturn,
} from '../services/api'

type AfterSaleCaseSummary = any
type Sc6MallReturnSummary = any
type Sc6ReviewSummary = any

const auth = useAuthStore()
const shopId = ref<number>()
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const activeTab = ref('reviews')
const reviews = ref<Sc6ReviewSummary[]>([])
const cases = ref<AfterSaleCaseSummary[]>([])
const returns = ref<Sc6MallReturnSummary[]>([])
const selectedCaseId = ref<number>()
const solution = reactive({
  type: 'REDO_SERVICE' as 'REFUND' | 'REDO_SERVICE' | 'RESTORE_ENTITLEMENT' | 'COMPENSATION_COUPON' | 'REJECT',
  riskAmount: 0,
  note: '',
})

const selectedCase = computed(() => cases.value.find((item) => item.id === selectedCaseId.value))
const pendingReviews = computed(() => reviews.value.filter((item) => item.moderationStatus === 'PENDING').length)
const waitingCustomers = computed(() => cases.value.filter((item) => item.status === 'WAITING_CUSTOMER').length)
const pendingInspection = computed(() => returns.value.filter((item) => item.status === 'PENDING_INSPECTION').length)

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  shopId.value = auth.context?.homeShopId ?? auth.shops[0]?.id
  await reload()
})

async function reload() {
  if (!shopId.value) return
  loading.value = true; error.value = ''
  try {
    const [reviewData, casePage, returnData] = await Promise.all([
      getSc6Reviews(shopId.value, 'ALL'), getAfterSaleCases(shopId.value),
      getSc6MallReturns(shopId.value),
    ])
    reviews.value = reviewData; cases.value = casePage.records; returns.value = returnData
    if (!selectedCaseId.value && cases.value.length) selectedCaseId.value = cases.value[0]!.id
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '履约售后数据加载失败' }
  finally { loading.value = false }
}

async function moderate(item: any, action: 'APPROVE' | 'HIDE' | 'REPLY') {
  const note = action === 'APPROVE' ? undefined : window.prompt(
    action === 'HIDE' ? '请填写隐藏原因。' : '请输入公开回复内容。',
  )?.trim()
  if (action !== 'APPROVE' && !note) return
  saving.value = true
  try {
    await moderateSc6Review(item.id, {
      shop_id: shopId.value!, version: item.version, action, note,
    })
    ElMessage.success(action === 'APPROVE' ? '评价已通过审核' : action === 'HIDE' ? '评价已隐藏' : '回复已发布')
    await reload()
  } catch (cause) { ElMessage.error(cause instanceof Error ? cause.message : '评价操作失败') }
  finally { saving.value = false }
}

async function decide() {
  const item = selectedCase.value
  if (!item || !solution.note.trim()) { ElMessage.warning('请选择工单并填写处理结论'); return }
  saving.value = true
  try {
    await decideSc6AfterSale(item.id, {
      shop_id: shopId.value!, version: item.version, resolution_type: solution.type,
      risk_amount: solution.riskAmount, note: solution.note.trim(),
      evidence: { source: 'SC6_OPERATIONS', recorded_at: new Date().toISOString() },
    })
    ElMessage.success(solution.type === 'REFUND'
      ? '退款方案已记录，仍保持处理中，待真实退款完成后再通知会员确认'
      : '处理方案已发送会员，48 小时确认计时已开始')
    solution.note = ''; await reload()
  } catch (cause) { ElMessage.error(cause instanceof Error ? cause.message : '售后方案提交失败') }
  finally { saving.value = false }
}

async function reviewReturn(item: any, approved: boolean) {
  const reason = approved ? undefined : window.prompt('请填写驳回原因。')?.trim()
  if (!approved && !reason) return
  saving.value = true
  try {
    await reviewSc6MallReturn(item.id, {
      shop_id: shopId.value!, version: item.version, approved, reason,
    })
    ElMessage.success(approved ? '退货申请已通过，等待会员寄回' : '退货申请已驳回')
    await reload()
  } catch (cause) { ElMessage.error(cause instanceof Error ? cause.message : '退货审核失败') }
  finally { saving.value = false }
}

async function inspect(item: any, passed: boolean) {
  const reason = window.prompt(passed ? '请输入验货通过说明。' : '请输入验货不通过原因。')?.trim()
  if (!reason) return
  const disposition = passed
    ? (window.confirm('商品可重新销售吗？确定=恢复可售库存，取消=计入残损库存') ? 'RESTORE' : 'DAMAGED')
    : undefined
  saving.value = true
  try {
    await inspectSc6MallReturn(item.id, {
      shop_id: shopId.value!, version: item.version, passed,
      stock_disposition: disposition, reason,
      evidence: { operator: auth.context?.username, inspected_at: new Date().toISOString() },
    })
    ElMessage.success(passed ? '验货通过，资产返还和库存处理已按唯一键入账' : '验货不通过，未返还任何资产')
    await reload()
  } catch (cause) { ElMessage.error(cause instanceof Error ? cause.message : '验货失败') }
  finally { saving.value = false }
}

</script>

<template>
  <section class="fulfillment-page">
    <header class="page-heading">
      <div><span class="section-kicker">FULFILLMENT &amp; CARE</span><h1>履约、评价与售后</h1><p>确认时限、评价审核、方案风险和实物验货集中处理；管理员不得修改顾客原文和评分，所有资产动作保留不可变流水。</p></div>
      <div class="heading-actions"><el-select v-model="shopId" aria-label="选择门店" @change="reload"><el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" /></el-select><el-button :icon="Refresh" :loading="loading" @click="reload">刷新</el-button></div>
    </header>
    <div class="summary-grid"><article><span>待审核公开评价</span><strong>{{ pendingReviews }}</strong><small>修改后重新进入审核</small></article><article><span>待会员确认</span><strong>{{ waitingCustomers }}</strong><small>48 小时未操作自动关单</small></article><article><span>待验货退货</span><strong>{{ pendingInspection }}</strong><small>验货前禁止返还资产</small></article></div>
    <el-alert v-if="error" type="error" :closable="false" :title="error" show-icon />

    <section class="workspace" v-loading="loading"><el-tabs v-model="activeTab">
      <el-tab-pane name="reviews" label="评价审核"><el-table :data="reviews" empty-text="暂无评价"><el-table-column prop="memberName" label="会员" width="120" /><el-table-column label="评分" width="110"><template #default="{ row }"><strong>{{ Number(row.averageRating).toFixed(1) }}</strong><small class="rating-detail">{{ row.staffRating }}/{{ row.effectRating }}/{{ row.environmentRating }}</small></template></el-table-column><el-table-column prop="content" label="评价内容" min-width="240" show-overflow-tooltip /><el-table-column prop="moderationStatus" label="审核状态" width="120" /><el-table-column label="低分联动" width="120"><template #default="{ row }">{{ row.afterSaleCaseId ? `工单 ${row.afterSaleCaseId}` : '—' }}</template></el-table-column><el-table-column label="操作" min-width="230" fixed="right"><template #default="{ row }"><el-button size="small" :disabled="row.deletedAt || saving" @click="moderate(row, 'APPROVE')">通过</el-button><el-button size="small" :disabled="row.deletedAt || saving" @click="moderate(row, 'REPLY')">回复</el-button><el-button size="small" type="danger" plain :disabled="row.deletedAt || saving" @click="moderate(row, 'HIDE')">隐藏</el-button></template></el-table-column></el-table></el-tab-pane>

      <el-tab-pane name="aftersale" label="售后方案"><div class="split-layout"><el-table :data="cases" highlight-current-row @current-change="(row: AfterSaleCaseSummary) => selectedCaseId = row?.id"><el-table-column prop="caseNo" label="工单" min-width="170" /><el-table-column prop="summary" label="问题" min-width="220" show-overflow-tooltip /><el-table-column prop="originType" label="来源" width="140" /><el-table-column prop="status" label="状态" width="150" /><el-table-column prop="reopenCount" label="重开" width="70" /></el-table><el-form class="decision-panel" label-position="top" @submit.prevent="decide"><h2>给出处理方案</h2><p>普通管理员单次资产影响上限 500 元；超限由超级管理员处理。</p><el-form-item label="当前工单"><el-select v-model="selectedCaseId"><el-option v-for="item in cases" :key="item.id" :label="`${item.caseNo} · ${item.status}`" :value="item.id" /></el-select></el-form-item><el-form-item label="方案"><el-select v-model="solution.type"><el-option label="重做服务" value="REDO_SERVICE" /><el-option label="恢复权益" value="RESTORE_ENTITLEMENT" /><el-option label="补偿优惠券" value="COMPENSATION_COUPON" /><el-option label="退款" value="REFUND" /><el-option label="驳回" value="REJECT" /></el-select></el-form-item><el-form-item label="风险金额"><el-input-number v-model="solution.riskAmount" :min="0" :precision="2" /></el-form-item><el-form-item label="处理结论"><el-input v-model="solution.note" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item><el-button native-type="submit" type="primary" :loading="saving">提交方案</el-button></el-form></div></el-tab-pane>

      <el-tab-pane name="returns" label="实物退换货"><el-table :data="returns" empty-text="暂无退换货申请"><el-table-column prop="orderNo" label="商城订单" min-width="180" /><el-table-column prop="reasonDetail" label="申请说明" min-width="230" show-overflow-tooltip /><el-table-column prop="returnTrackingNo" label="退货物流" min-width="150" /><el-table-column prop="status" label="状态" width="160" /><el-table-column label="操作" min-width="270" fixed="right"><template #default="{ row }"><template v-if="row.status === 'SUBMITTED'"><el-button size="small" type="primary" plain @click="reviewReturn(row, true)">通过申请</el-button><el-button size="small" type="danger" plain @click="reviewReturn(row, false)">驳回</el-button></template><template v-else-if="row.status === 'PENDING_INSPECTION'"><el-button size="small" type="primary" @click="inspect(row, true)">验货通过</el-button><el-button size="small" type="danger" plain @click="inspect(row, false)">验货不通过</el-button></template><span v-else class="muted">{{ row.inspectionReason || '等待下一节点' }}</span></template></el-table-column></el-table><el-alert class="inspection-note" type="info" :closable="false" title="积分、现金、运费与库存只会在验货通过动作中处理；验货不通过不会产生返还流水。" show-icon /></el-tab-pane>
    </el-tabs></section>
  </section>
</template>

<style scoped>
.fulfillment-page{display:grid;gap:22px}.summary-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.summary-grid article{display:flex;flex-direction:column;padding:20px;border:1px solid var(--line);background:var(--surface)}.summary-grid strong{margin:8px 0;font-size:30px}.summary-grid span,.summary-grid small,.decision-panel>p,.muted{color:var(--text-muted)}.workspace,.decision-panel{border:1px solid var(--line);background:var(--surface)}.workspace{padding:8px 22px 24px}.split-layout{display:grid;grid-template-columns:minmax(0,1.5fr) minmax(300px,.6fr);gap:18px}.decision-panel{padding:22px}.decision-panel h2{margin:0 0 6px}.decision-panel>p{margin:0 0 18px;line-height:1.6}.rating-detail{display:block;color:var(--text-muted)}.inspection-note{margin-top:18px}
@media(max-width:900px){.summary-grid,.split-layout{grid-template-columns:1fr}.workspace{padding:6px 12px 18px}.decision-panel{padding:16px}}
</style>
