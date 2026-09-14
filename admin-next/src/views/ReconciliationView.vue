<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  closeReconciliation,
  getReconciliationBatch,
  getReconciliationBatches,
  resolveReconciliation,
  runReconciliation,
  type ReconciliationBatch,
  type ReconciliationStatus,
} from '../services/api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const batches = ref<ReconciliationBatch[]>([])
const selected = ref<ReconciliationBatch>()
const panel = ref<'run' | 'detail' | undefined>()
const query = reactive({
  shopId: auth.shops[0]?.id,
  status: 'ALL' as ReconciliationStatus | 'ALL',
})
const runForm = reactive({
  channelCode: 'SANDBOX',
  accountingDate: new Date().toISOString().slice(0, 10),
  channelPaymentCount: 0,
  channelPaymentAmount: 0,
  channelRefundCount: 0,
  channelRefundAmount: 0,
})

const canManage = computed(() =>
  Boolean(auth.context?.permissions?.includes('reconciliation:manage')),
)
const matchedCount = computed(() =>
  batches.value.filter((item) => ['MATCHED', 'CLOSED'].includes(item.status)).length,
)
const differentCount = computed(() =>
  batches.value.filter((item) => item.status === 'DIFFERENT').length,
)
const resolvedCount = computed(() =>
  batches.value.filter((item) => item.status === 'RESOLVED').length,
)

onMounted(load)

async function load() {
  if (!query.shopId) return
  loading.value = true
  error.value = ''
  try {
    batches.value = await getReconciliationBatches(query.shopId, query.status)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '对账批次加载失败'
  } finally {
    loading.value = false
  }
}

async function openDetail(batchId: number) {
  if (!query.shopId) return
  panel.value = 'detail'
  selected.value = undefined
  try {
    selected.value = await getReconciliationBatch(query.shopId, batchId)
  } catch (reason) {
    panel.value = undefined
    ElMessage.error(reason instanceof Error ? reason.message : '对账详情加载失败')
  }
}

function openRun() {
  selected.value = undefined
  panel.value = 'run'
}

async function submitRun() {
  if (!query.shopId || !runForm.accountingDate) return
  saving.value = true
  try {
    selected.value = await runReconciliation({
      shop_id: query.shopId,
      channel_code: runForm.channelCode,
      accounting_date: runForm.accountingDate,
      channel_payment_count: Number(runForm.channelPaymentCount),
      channel_payment_amount: Number(runForm.channelPaymentAmount),
      channel_refund_count: Number(runForm.channelRefundCount),
      channel_refund_amount: Number(runForm.channelRefundAmount),
    })
    panel.value = 'detail'
    ElMessage.success(
      selected.value.status === 'MATCHED' ? '日终账务核对一致' : '已生成差异，请按记录处理',
    )
    await load()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '日终对账执行失败')
  } finally {
    saving.value = false
  }
}

async function resolveBatch() {
  if (!query.shopId || !selected.value) return
  try {
    const result = await ElMessageBox.prompt(
      '处理记录只追加说明和证据引用，不会直接修改支付、退款或订单。',
      '追加差异处理记录',
      {
        inputType: 'textarea',
        confirmButtonText: '确认记录',
        cancelButtonText: '返回',
        inputValidator: (value) => Boolean(value?.trim()) || '必须填写处理说明',
      },
    )
    selected.value = await resolveReconciliation(selected.value.id, {
      shop_id: query.shopId,
      version: selected.value.version,
      resolution_note: result.value.trim(),
    })
    ElMessage.success('差异处理记录已追加')
    await load()
  } catch (reasonOrCancel) {
    if (reasonOrCancel === 'cancel' || reasonOrCancel === 'close') return
    ElMessage.error(reasonOrCancel instanceof Error ? reasonOrCancel.message : '差异处理失败')
  }
}

async function closeBatch() {
  if (!query.shopId || !selected.value) return
  try {
    await ElMessageBox.confirm(
      '关闭后保留全部汇总、差异和处理记录，不会删除或抹平历史。',
      '关闭对账批次',
      { confirmButtonText: '确认关闭', cancelButtonText: '返回' },
    )
    selected.value = await closeReconciliation(
      selected.value.id,
      query.shopId,
      selected.value.version,
    )
    ElMessage.success('对账批次已关闭')
    await load()
  } catch (reasonOrCancel) {
    if (reasonOrCancel === 'cancel' || reasonOrCancel === 'close') return
    ElMessage.error(reasonOrCancel instanceof Error ? reasonOrCancel.message : '关闭失败')
  }
}

function statusLabel(status: ReconciliationStatus) {
  return {
    PENDING: '待运行',
    RUNNING: '运行中',
    MATCHED: '账务一致',
    DIFFERENT: '存在差异',
    RESOLVED: '差异已处理',
    CLOSED: '已关闭',
  }[status]
}

function money(value?: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
  }).format(Number(value ?? 0))
}

function differenceLabel(value: string) {
  return {
    MISSING_CHANNEL: '通道缺单',
    MISSING_SYSTEM: '系统缺单',
    AMOUNT_MISMATCH: '金额不一致',
    STATUS_MISMATCH: '状态不一致',
  }[value] ?? value
}
</script>

<template>
  <section>
    <header class="page-heading reconciliation-heading">
      <div>
        <p class="environment-label">M4 · 资金安全</p>
        <h1>支付与对账</h1>
        <p>核对系统支付、退款与通道账；差异只能留痕处理，不能在这里直接改账。</p>
      </div>
      <el-button v-if="canManage" type="primary" @click="openRun">执行日终对账</el-button>
    </header>

    <section class="metric-strip" aria-label="对账概览">
      <div><span>当前结果</span><strong>{{ batches.length }}</strong><small>筛选内批次</small></div>
      <div><span>账务一致</span><strong>{{ matchedCount }}</strong><small>含已关闭批次</small></div>
      <div><span>待处理差异</span><strong>{{ differentCount }}</strong><small>不能直接抹平</small></div>
      <div><span>已处理</span><strong>{{ resolvedCount }}</strong><small>等待复核关闭</small></div>
    </section>

    <section class="reconciliation-toolbar content-section">
      <div>
        <el-select v-model="query.shopId" aria-label="门店" @change="load">
          <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
        </el-select>
        <el-select v-model="query.status" aria-label="对账状态" @change="load">
          <el-option label="全部状态" value="ALL" />
          <el-option label="账务一致" value="MATCHED" />
          <el-option label="存在差异" value="DIFFERENT" />
          <el-option label="差异已处理" value="RESOLVED" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>
      <span>SANDBOX 仅用于隔离验收；生产通道未配置时不会记账成功。</span>
    </section>

    <el-alert v-if="error" class="reconciliation-error" type="error" :closable="false">
      {{ error }}
    </el-alert>

    <div :class="['reconciliation-layout', { 'reconciliation-layout--panel': panel }]">
      <section class="content-section reconciliation-results" :aria-busy="loading">
        <div v-if="loading" class="reconciliation-loading">
          <el-skeleton :rows="7" animated />
        </div>
        <div v-else-if="!batches.length" class="empty-state reconciliation-empty">
          <h2>还没有对账批次</h2>
          <p>选择门店后执行首个日终对账，系统会保存汇总和所有差异证据。</p>
          <el-button v-if="canManage" type="primary" @click="openRun">执行日终对账</el-button>
        </div>
        <div v-else class="reconciliation-table-wrap">
          <table class="reconciliation-table">
            <thead>
              <tr>
                <th>账务日期 / 通道</th>
                <th>系统支付</th>
                <th>通道支付</th>
                <th>系统退款</th>
                <th>通道退款</th>
                <th>结果</th>
                <th aria-label="操作"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="batch in batches" :key="batch.id">
                <td><strong>{{ batch.accountingDate }}</strong><code>{{ batch.channelCode }}</code></td>
                <td>{{ batch.systemPaymentCount }} 笔 · {{ money(batch.systemPaymentAmount) }}</td>
                <td>{{ batch.channelPaymentCount }} 笔 · {{ money(batch.channelPaymentAmount) }}</td>
                <td>{{ batch.systemRefundCount }} 笔 · {{ money(batch.systemRefundAmount) }}</td>
                <td>{{ batch.channelRefundCount }} 笔 · {{ money(batch.channelRefundAmount) }}</td>
                <td>
                  <span :class="['reconciliation-status', `is-${batch.status.toLowerCase()}`]">
                    {{ statusLabel(batch.status) }}
                  </span>
                </td>
                <td><el-button link type="primary" @click="openDetail(batch.id)">查看</el-button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <aside v-if="panel === 'run'" class="reconciliation-panel">
        <header>
          <div><span>创建批次</span><h2>执行日终对账</h2></div>
          <el-button text aria-label="关闭" @click="panel = undefined">关闭</el-button>
        </header>
        <el-alert type="info" :closable="false">
          当前由财务录入通道日结汇总；系统账由后端实时汇总，不能手工覆盖。
        </el-alert>
        <el-form label-position="top">
          <el-form-item label="支付通道">
            <el-select v-model="runForm.channelCode">
              <el-option label="SANDBOX（隔离验收）" value="SANDBOX" />
              <el-option label="微信支付" value="WECHAT" />
              <el-option label="支付宝" value="ALIPAY" />
              <el-option label="银行卡" value="CARD" />
              <el-option label="现金" value="CASH" />
            </el-select>
          </el-form-item>
          <el-form-item label="账务日期">
            <el-date-picker v-model="runForm.accountingDate" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
          <div class="reconciliation-form-grid">
            <el-form-item label="通道支付笔数"><el-input-number v-model="runForm.channelPaymentCount" :min="0" /></el-form-item>
            <el-form-item label="通道支付金额"><el-input-number v-model="runForm.channelPaymentAmount" :min="0" :precision="2" /></el-form-item>
            <el-form-item label="通道退款笔数"><el-input-number v-model="runForm.channelRefundCount" :min="0" /></el-form-item>
            <el-form-item label="通道退款金额"><el-input-number v-model="runForm.channelRefundAmount" :min="0" :precision="2" /></el-form-item>
          </div>
        </el-form>
        <footer>
          <el-button @click="panel = undefined">返回</el-button>
          <el-button type="primary" :loading="saving" @click="submitRun">运行并保存结果</el-button>
        </footer>
      </aside>

      <aside v-else-if="panel === 'detail'" class="reconciliation-panel">
        <header>
          <div><span>批次详情</span><h2>{{ selected?.accountingDate ?? '正在加载' }}</h2></div>
          <el-button text aria-label="关闭" @click="panel = undefined">关闭</el-button>
        </header>
        <template v-if="selected">
          <dl class="reconciliation-summary">
            <div><dt>通道</dt><dd>{{ selected.channelCode }}</dd></div>
            <div><dt>状态</dt><dd>{{ statusLabel(selected.status) }}</dd></div>
            <div><dt>系统支付</dt><dd>{{ selected.systemPaymentCount }} 笔 · {{ money(selected.systemPaymentAmount) }}</dd></div>
            <div><dt>通道支付</dt><dd>{{ selected.channelPaymentCount }} 笔 · {{ money(selected.channelPaymentAmount) }}</dd></div>
            <div><dt>系统退款</dt><dd>{{ selected.systemRefundCount }} 笔 · {{ money(selected.systemRefundAmount) }}</dd></div>
            <div><dt>通道退款</dt><dd>{{ selected.channelRefundCount }} 笔 · {{ money(selected.channelRefundAmount) }}</dd></div>
          </dl>
          <section class="reconciliation-detail-section">
            <h3>差异记录</h3>
            <p v-if="!selected.items?.length">支付、退款笔数及金额均一致。</p>
            <div v-for="item in selected.items" :key="item.id" class="difference-row">
              <strong>{{ item.businessType === 'PAYMENT' ? '支付' : '退款' }} · {{ differenceLabel(item.differenceType) }}</strong>
              <span>系统 {{ money(item.systemAmount) }} / 通道 {{ money(item.channelAmount) }}</span>
            </div>
          </section>
          <section class="reconciliation-detail-section">
            <h3>处理记录</h3>
            <p v-if="!selected.resolutions?.length">尚未追加处理记录。</p>
            <div v-for="resolution in selected.resolutions" :key="resolution.id" class="resolution-row">
              <strong>{{ resolution.createdByName }}</strong>
              <p>{{ resolution.resolutionNote }}</p>
            </div>
          </section>
          <footer v-if="canManage">
            <el-button v-if="selected.status === 'DIFFERENT'" @click="resolveBatch">追加处理记录</el-button>
            <el-button
              v-if="['MATCHED', 'RESOLVED'].includes(selected.status)"
              type="primary"
              @click="closeBatch"
            >
              关闭批次
            </el-button>
          </footer>
        </template>
        <el-skeleton v-else :rows="8" animated />
      </aside>
    </div>
  </section>
</template>

<style scoped>
.reconciliation-heading { margin-bottom: 20px; }
.reconciliation-heading .el-button { min-height: 42px; }
.reconciliation-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 14px 18px; margin-bottom: 16px; overflow: visible; }
.reconciliation-toolbar > div { display: flex; flex-wrap: wrap; gap: 10px; }
.reconciliation-toolbar .el-select { width: 190px; }
.reconciliation-toolbar > span { max-width: 55ch; color: var(--oc-text-soft); font-size: 12px; text-align: right; }
.reconciliation-error { margin-bottom: 16px; }
.reconciliation-layout { display: grid; grid-template-columns: minmax(0, 1fr); gap: 22px; align-items: start; }
.reconciliation-layout--panel { grid-template-columns: minmax(0, 1fr) minmax(380px, 440px); }
.reconciliation-results { min-width: 0; min-height: 430px; }
.reconciliation-loading { padding: 28px 24px; }
.reconciliation-empty { min-height: 426px; }
.reconciliation-empty .el-button { margin-top: 18px; }
.reconciliation-table-wrap { overflow-x: auto; }
.reconciliation-table { width: 100%; min-width: 1080px; border-collapse: collapse; text-align: left; }
.reconciliation-table th, .reconciliation-table td { padding: 15px 16px; border-bottom: 1px solid var(--oc-border); vertical-align: middle; white-space: nowrap; }
.reconciliation-table th { background: var(--oc-surface-2); color: var(--oc-text-muted); font-size: 12px; font-weight: 620; }
.reconciliation-table tbody tr:hover { background: rgba(168, 79, 100, 0.045); }
.reconciliation-table td:first-child { display: flex; flex-direction: column; gap: 2px; }
.reconciliation-table code { color: var(--oc-copper); font-family: "SFMono-Regular", Consolas, monospace; font-size: 10px; }
.reconciliation-status { display: inline-flex; min-height: 26px; align-items: center; padding: 0 9px; border: 1px solid var(--oc-border); border-radius: 6px; color: var(--oc-text-soft); font-size: 11px; font-weight: 650; }
.reconciliation-status.is-matched, .reconciliation-status.is-closed { border-color: rgba(53,112,75,.34); color: var(--oc-success); }
.reconciliation-status.is-different { border-color: rgba(183,64,75,.34); color: var(--oc-danger); }
.reconciliation-status.is-resolved { border-color: rgba(149,97,77,.34); color: var(--oc-copper); }
.reconciliation-panel { position: sticky; top: 94px; padding: 22px; background: var(--oc-surface-1); border: 1px solid var(--oc-border-strong); border-radius: var(--oc-radius-md); }
.reconciliation-panel > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 18px; margin-bottom: 18px; border-bottom: 1px solid var(--oc-border); }
.reconciliation-panel > header span { color: var(--oc-copper); font-size: 11px; }
.reconciliation-panel h2 { margin: 3px 0 0; font-size: 21px; }
.reconciliation-panel .el-alert { margin-bottom: 18px; }
.reconciliation-panel .el-select, .reconciliation-panel .el-date-editor, .reconciliation-panel .el-input-number { width: 100%; }
.reconciliation-form-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 0 14px; }
.reconciliation-panel > footer { display: flex; justify-content: flex-end; gap: 10px; padding-top: 18px; margin-top: 18px; border-top: 1px solid var(--oc-border); }
.reconciliation-summary { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; margin: 0; }
.reconciliation-summary div { padding: 12px; background: var(--oc-surface-2); border-radius: var(--oc-radius-sm); }
.reconciliation-summary dt { color: var(--oc-text-soft); font-size: 11px; }
.reconciliation-summary dd { margin: 3px 0 0; font-size: 12px; font-weight: 620; }
.reconciliation-detail-section { padding-top: 18px; margin-top: 18px; border-top: 1px solid var(--oc-border); }
.reconciliation-detail-section h3 { margin: 0 0 10px; font-size: 14px; }
.reconciliation-detail-section > p { margin: 0; color: var(--oc-text-soft); font-size: 12px; }
.difference-row, .resolution-row { display: flex; flex-direction: column; gap: 3px; padding: 10px 0; }
.difference-row + .difference-row, .resolution-row + .resolution-row { border-top: 1px solid var(--oc-border); }
.difference-row span, .resolution-row p { margin: 0; color: var(--oc-text-soft); font-size: 12px; }
@media (max-width: 1120px) { .reconciliation-layout--panel { grid-template-columns: 1fr; } .reconciliation-panel { position: static; grid-row: 1; } }
@media (max-width: 720px) { .reconciliation-heading .el-button { width: 100%; } .reconciliation-toolbar { align-items: stretch; flex-direction: column; } .reconciliation-toolbar > div, .reconciliation-toolbar .el-select { width: 100%; } .reconciliation-toolbar > span { text-align: left; } .reconciliation-form-grid, .reconciliation-summary { grid-template-columns: 1fr; } .reconciliation-panel { padding: 18px; } }
</style>
