<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Clock,
  Coin,
  DocumentAdd,
  RefreshLeft,
  Search,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import {
  createOrder,
  decideOrderRefund,
  executeOrderRefund,
  getMemberAccountActivity,
  getTransactionResources,
  getTransactions,
  payOrder,
  requestOrderRefund,
  voidOrder,
  type MemberAccountActivity,
  type OrderStatus,
  type OrderSummary,
  type PaymentMethod,
  type RefundSummary,
  type TransactionResources,
} from '../services/api'

type EditorMode = 'create' | 'pay' | 'refund' | 'ledger'

const auth = useAuthStore()
const today = new Date()
const startDate = new Date()
startDate.setDate(startDate.getDate() - 30)

const query = reactive({
  shopId: undefined as number | undefined,
  dateRange: [localDateString(startDate), localDateString(today)] as [string, string],
  status: 'ALL' as OrderStatus | 'ALL',
  keyword: '',
  page: 1,
  pageSize: 30,
})

const records = ref<OrderSummary[]>([])
const total = ref(0)
const summary = ref<Partial<Record<OrderStatus, number>>>({})
const resources = ref<TransactionResources>()
const loading = ref(true)
const resourcesLoading = ref(false)
const saving = ref(false)
const error = ref('')
const actionId = ref<number>()
const editorMode = ref<EditorMode>()
const selectedOrder = ref<OrderSummary>()
const ledgerActivity = ref<MemberAccountActivity>()
const ledgerLoading = ref(false)

const createForm = reactive({
  memberId: undefined as number | undefined,
  appointmentId: undefined as number | undefined,
  serviceIds: [] as number[],
  productIds: [] as number[],
  notes: '',
})

const paymentForm = reactive({
  method: 'CASH' as Exclude<PaymentMethod, 'LEGACY'>,
  amount: 0,
  externalTransactionNo: '',
})

const refundForm = reactive({
  paymentId: undefined as number | undefined,
  amount: 0,
  reason: '',
})

const statusOptions: Array<{ value: OrderStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'UNPAID', label: '待收款' },
  { value: 'PARTIALLY_PAID', label: '部分收款' },
  { value: 'PAID', label: '已收款' },
  { value: 'PARTIALLY_REFUNDED', label: '部分退款' },
  { value: 'REFUNDED', label: '已退款' },
  { value: 'VOID', label: '已作废' },
]

const paymentMethods = [
  { label: '现金', value: 'CASH' },
  { label: '银行卡', value: 'CARD' },
  { label: '微信', value: 'WECHAT' },
  { label: '支付宝', value: 'ALIPAY' },
  { label: '会员余额', value: 'BALANCE' },
]

const selectedAppointment = computed(() =>
  resources.value?.appointments.find((item) => item.id === createForm.appointmentId),
)

const selectedMember = computed(() =>
  resources.value?.members.find((item) => item.id === createForm.memberId),
)

const selectedPayment = computed(() =>
  selectedOrder.value?.payments.find((item) => item.id === refundForm.paymentId),
)

const estimatedAmount = computed(() => {
  const appointmentAmount = selectedAppointment.value?.totalAmount ?? 0
  const serviceAmount = createForm.appointmentId
    ? 0
    : createForm.serviceIds.reduce(
        (sum, id) => sum + Number(resources.value?.services.find((item) => item.id === id)?.price ?? 0),
        0,
      )
  const productAmount = createForm.productIds.reduce(
    (sum, id) => sum + Number(resources.value?.products.find((item) => item.id === id)?.price ?? 0),
    0,
  )
  return appointmentAmount + serviceAmount + productAmount
})

const paymentOutstanding = computed(() => {
  if (!selectedOrder.value) return 0
  return Number(selectedOrder.value.payableAmount) - Number(selectedOrder.value.paidAmount)
})

const selectedPaymentRefundable = computed(() => {
  if (!selectedPayment.value) return 0
  return Number(selectedPayment.value.amount) - Number(selectedPayment.value.refundedAmount)
})

const selectedShopName = computed(
  () => auth.shops.find((shop) => shop.id === query.shopId)?.name ?? '当前门店',
)
const canRequestRefund = computed(() =>
  auth.context?.permissions?.includes('refund:request') ?? false,
)
const canApproveRefund = computed(() =>
  auth.context?.permissions?.includes('refund:approve') ?? false,
)
const canExecuteRefund = computed(() =>
  auth.context?.permissions?.includes('refund:execute') ?? false,
)

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  query.shopId = auth.context?.homeShopId ?? auth.shops[0]?.id
  await Promise.all([loadPage(), loadResources()])
})

async function loadPage() {
  if (!query.shopId) {
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    const page = await getTransactions({
      shopId: query.shopId,
      fromDate: query.dateRange[0],
      toDate: query.dateRange[1],
      status: query.status,
      keyword: query.keyword.trim() || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    records.value = page.records
    total.value = page.total
    summary.value = page.summary
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '交易中心加载失败'
  } finally {
    loading.value = false
  }
}

async function loadResources() {
  if (!query.shopId) return
  resourcesLoading.value = true
  try {
    resources.value = await getTransactionResources(query.shopId)
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '结算资源加载失败')
  } finally {
    resourcesLoading.value = false
  }
}

async function applyFilters() {
  query.page = 1
  closeEditor()
  await Promise.all([loadPage(), loadResources()])
}

async function resetFilters() {
  query.status = 'ALL'
  query.keyword = ''
  query.page = 1
  await loadPage()
}

async function setStatus(status: OrderStatus | 'ALL') {
  query.status = status
  query.page = 1
  await loadPage()
}

function openCreate() {
  selectedOrder.value = undefined
  Object.assign(createForm, {
    memberId: undefined,
    appointmentId: undefined,
    serviceIds: [],
    productIds: [],
    notes: '',
  })
  editorMode.value = 'create'
}

function handleAppointmentChange() {
  if (selectedAppointment.value) {
    createForm.memberId = selectedAppointment.value.memberId
    createForm.serviceIds = []
  }
}

function openPay(order: OrderSummary) {
  selectedOrder.value = order
  Object.assign(paymentForm, {
    method: 'CASH',
    amount: Number(order.payableAmount) - Number(order.paidAmount),
    externalTransactionNo: '',
  })
  editorMode.value = 'pay'
}

function openRefund(order: OrderSummary) {
  const payment = order.payments.find(
    (item) => item.status === 'SUCCESS' && Number(item.amount) > Number(item.refundedAmount),
  )
  if (!payment) {
    ElMessage.warning('该订单没有可退款的收款记录')
    return
  }
  selectedOrder.value = order
  Object.assign(refundForm, {
    paymentId: payment.id,
    amount: Number(payment.amount) - Number(payment.refundedAmount),
    reason: '',
  })
  editorMode.value = 'refund'
}

function handleRefundPaymentChange() {
  refundForm.amount = selectedPaymentRefundable.value
}

async function openLedger(order: OrderSummary) {
  selectedOrder.value = order
  editorMode.value = 'ledger'
  ledgerLoading.value = true
  ledgerActivity.value = undefined
  try {
    ledgerActivity.value = await getMemberAccountActivity(order.shopId, order.memberId)
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '会员账户流水加载失败')
  } finally {
    ledgerLoading.value = false
  }
}

function closeEditor() {
  editorMode.value = undefined
  selectedOrder.value = undefined
  ledgerActivity.value = undefined
}

async function submitCreate() {
  if (!query.shopId || !createForm.memberId) {
    ElMessage.warning('请选择会员')
    return
  }
  if (!createForm.appointmentId && !createForm.serviceIds.length && !createForm.productIds.length) {
    ElMessage.warning('请选择已完成预约、护理项目或零售产品')
    return
  }
  saving.value = true
  try {
    const items = [
      ...createForm.serviceIds.map((referenceId) => ({
        itemType: 'SERVICE' as const,
        referenceId,
        quantity: 1,
        discountAmount: 0,
      })),
      ...createForm.productIds.map((referenceId) => ({
        itemType: 'PRODUCT' as const,
        referenceId,
        quantity: 1,
        discountAmount: 0,
      })),
    ]
    const created = await createOrder({
      shopId: query.shopId,
      memberId: createForm.memberId,
      appointmentId: createForm.appointmentId,
      items,
      notes: createForm.notes.trim() || undefined,
    })
    ElMessage.success(`订单 ${created.orderNo} 已创建`)
    closeEditor()
    await Promise.all([loadPage(), loadResources()])
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '创建订单失败')
  } finally {
    saving.value = false
  }
}

async function submitPayment() {
  if (!selectedOrder.value || !query.shopId) return
  if (paymentForm.amount <= 0 || paymentForm.amount > paymentOutstanding.value) {
    ElMessage.warning('请填写不超过剩余应付金额的收款金额')
    return
  }
  if (
    paymentForm.method === 'BALANCE' &&
    Number(selectedMemberForOrder(selectedOrder.value)?.balance ?? 0) < paymentForm.amount
  ) {
    ElMessage.warning('会员余额不足，请更换收款方式')
    return
  }
  saving.value = true
  try {
    await payOrder(selectedOrder.value.id, {
      shopId: query.shopId,
      paymentMethod: paymentForm.method,
      amount: paymentForm.amount,
      version: selectedOrder.value.version,
      idempotencyKey: uniqueKey('payment'),
      externalTransactionNo: paymentForm.externalTransactionNo.trim() || undefined,
    })
    ElMessage.success('收款已记录')
    closeEditor()
    await Promise.all([loadPage(), loadResources()])
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '收款失败')
  } finally {
    saving.value = false
  }
}

async function submitRefund() {
  if (!selectedOrder.value || !query.shopId || !refundForm.paymentId) return
  if (refundForm.amount <= 0 || refundForm.amount > selectedPaymentRefundable.value) {
    ElMessage.warning('请填写不超过原收款可退余额的退款金额')
    return
  }
  if (!refundForm.reason.trim()) {
    ElMessage.warning('请填写退款原因')
    return
  }
  saving.value = true
  try {
    const refund = await requestOrderRefund(selectedOrder.value.id, {
      shopId: query.shopId,
      paymentId: refundForm.paymentId,
      amount: refundForm.amount,
      reason: refundForm.reason.trim(),
      idempotencyKey: uniqueKey('refund'),
    })
    ElMessage.success(`退款申请 ${refund.refundNo} 已提交审核`)
    closeEditor()
    await loadPage()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '退款申请提交失败')
  } finally {
    saving.value = false
  }
}

async function decideRefund(order: OrderSummary, refund: RefundSummary, action: 'APPROVE' | 'REJECT') {
  let decisionNote = ''
  try {
    if (action === 'REJECT') {
      const result = await ElMessageBox.prompt(
        '请填写拒绝退款的原因，前台可以在订单中查看审核结果。',
        '拒绝退款',
        {
          confirmButtonText: '确认拒绝',
          cancelButtonText: '返回',
          inputType: 'textarea',
          inputValidator: (value) => Boolean(value?.trim()) || '必须填写审核说明',
        },
      )
      decisionNote = result.value.trim()
    } else {
      await ElMessageBox.confirm(
        `确认批准退款 ${formatMoney(refund.amount)}？批准后仍需执行人员完成账务冲正。`,
        '批准退款',
        { confirmButtonText: '确认批准', cancelButtonText: '返回', type: 'warning' },
      )
    }
    actionId.value = refund.id
    await decideOrderRefund(refund.id, {
      shopId: order.shopId,
      action,
      version: refund.version,
      decisionNote: decisionNote || undefined,
      idempotencyKey: uniqueKey('refund-decision'),
    })
    ElMessage.success(action === 'APPROVE' ? '退款已批准，等待执行' : '退款申请已拒绝')
    await Promise.all([loadPage(), loadResources()])
  } catch (reasonOrCancel) {
    if (reasonOrCancel === 'cancel' || reasonOrCancel === 'close') return
    ElMessage.error(reasonOrCancel instanceof Error ? reasonOrCancel.message : '退款审核失败')
  } finally {
    actionId.value = undefined
  }
}

async function performVoid(order: OrderSummary) {
  try {
    const result = await ElMessageBox.prompt(
      '作废后订单不能再收款，请填写作废原因。',
      '作废订单',
      {
        confirmButtonText: '确认作废',
        cancelButtonText: '返回',
        inputType: 'textarea',
        inputValidator: (value) => Boolean(value?.trim()) || '必须填写作废原因',
      },
    )
    actionId.value = order.id
    await voidOrder(order.id, {
      shopId: order.shopId,
      version: order.version,
      reason: result.value.trim(),
    })
    ElMessage.success('订单已作废')
    await Promise.all([loadPage(), loadResources()])
  } catch (reasonOrCancel) {
    if (reasonOrCancel === 'cancel' || reasonOrCancel === 'close') return
    ElMessage.error(reasonOrCancel instanceof Error ? reasonOrCancel.message : '订单作废失败')
  } finally {
    actionId.value = undefined
  }
}

async function executeRefund(order: OrderSummary, refund: RefundSummary) {
  try {
    await ElMessageBox.confirm(
      `确认执行退款 ${formatMoney(refund.amount)}？执行后将更新订单和原收款，余额支付会同步退回会员账户。`,
      '执行退款',
      { confirmButtonText: '确认执行', cancelButtonText: '返回', type: 'warning' },
    )
    actionId.value = refund.id
    await executeOrderRefund(refund.id, {
      shopId: order.shopId,
      version: refund.version,
      idempotencyKey: uniqueKey('refund-execute'),
    })
    ElMessage.success('退款已执行并完成账务冲正')
    await Promise.all([loadPage(), loadResources()])
  } catch (reasonOrCancel) {
    if (reasonOrCancel === 'cancel' || reasonOrCancel === 'close') return
    ElMessage.error(reasonOrCancel instanceof Error ? reasonOrCancel.message : '退款执行失败')
  } finally {
    actionId.value = undefined
  }
}

function activeRefund(order: OrderSummary) {
  return order.refunds.find((item) =>
    ['PENDING', 'APPROVED', 'PROCESSING', 'FAILED'].includes(item.status),
  )
}

function canApproveRefundItem(refund: RefundSummary) {
  return canApproveRefund.value &&
    refund.status === 'PENDING' &&
    refund.createdBy !== auth.context?.accountId
}

function refundPaymentMethod(order: OrderSummary, refund: RefundSummary) {
  return order.payments.find((item) => item.id === refund.paymentId)?.paymentMethod
}

function isExternalRefund(order: OrderSummary, refund: RefundSummary) {
  return ['CARD', 'WECHAT', 'ALIPAY'].includes(refundPaymentMethod(order, refund) ?? '')
}

function canExecuteRefundItem(order: OrderSummary, refund: RefundSummary) {
  return canExecuteRefund.value &&
    ['APPROVED', 'FAILED'].includes(refund.status) &&
    !isExternalRefund(order, refund)
}

function refundStatusLabel(status: RefundSummary['status']) {
  return {
    PENDING: '退款待审核',
    APPROVED: '退款待执行',
    PROCESSING: '退款处理中',
    SUCCESS: '退款已完成',
    REJECTED: '退款已拒绝',
    FAILED: '退款执行失败',
  }[status]
}

function canRefund(order: OrderSummary) {
  return canRequestRefund.value &&
    !activeRefund(order) &&
    ['PAID', 'PARTIALLY_REFUNDED'].includes(order.status) &&
    order.payments.some(
      (item) => item.status === 'SUCCESS' && Number(item.amount) > Number(item.refundedAmount),
    )
}

function selectedMemberForOrder(order: OrderSummary) {
  return resources.value?.members.find((item) => item.id === order.memberId)
}

function statusLabel(status: OrderStatus) {
  return statusOptions.find((item) => item.value === status)?.label ?? status
}

function statusClass(status: OrderStatus) {
  return `transaction-status transaction-status--${status.toLowerCase().replace(/_/g, '-')}`
}

function summaryCount(status: OrderStatus | 'ALL') {
  if (status === 'ALL') {
    return Object.values(summary.value).reduce((sum, count) => sum + Number(count ?? 0), 0)
  }
  return Number(summary.value[status] ?? 0)
}

function paymentMethodLabel(method?: string) {
  const labels: Record<string, string> = {
    CASH: '现金',
    CARD: '银行卡',
    WECHAT: '微信',
    ALIPAY: '支付宝',
    BALANCE: '会员余额',
    MIXED: '组合收款',
    LEGACY: '历史支付',
  }
  return method ? labels[method] ?? method : '未收款'
}

function accountTypeLabel(value: string) {
  return { BALANCE: '储值余额', GIFT_BALANCE: '赠送余额', POINTS: '积分' }[value] ?? value
}

function ledgerEntryLabel(value: string) {
  return {
    OPENING_BALANCE: '期初余额',
    ORDER_PAYMENT: '订单支付',
    ORDER_REFUND: '订单退款',
  }[value] ?? value
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
  }).format(Number(value ?? 0))
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function localDateString(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function uniqueKey(prefix: string) {
  const value = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
  return `${prefix}-${value}`.slice(0, 80)
}
</script>

<template>
  <section class="page-heading transaction-page-heading">
    <div>
      <p class="environment-label">订单与资金闭环</p>
      <h1>交易与结算</h1>
      <p>从已完成预约或现场消费生成订单，记录收款、退款审核和会员余额变动。</p>
    </div>
    <el-button type="primary" :disabled="!query.shopId" @click="openCreate">
      <el-icon><DocumentAdd /></el-icon>
      新建订单
    </el-button>
  </section>

  <section class="content-section transaction-toolbar" aria-label="交易筛选">
    <div class="transaction-date-range">
      <el-date-picker
        v-model="query.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        @change="applyFilters"
      />
    </div>
    <div class="transaction-filters">
      <el-select v-model="query.shopId" aria-label="交易门店" @change="applyFilters">
        <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
      </el-select>
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="订单号、会员或预约号"
        aria-label="搜索交易"
        @keyup.enter="applyFilters"
        @clear="applyFilters"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button :loading="loading" @click="applyFilters">查询</el-button>
      <el-button text @click="resetFilters">
        <el-icon><RefreshLeft /></el-icon>
        重置
      </el-button>
    </div>
  </section>

  <nav class="transaction-status-tabs" aria-label="订单状态筛选">
    <button
      v-for="item in statusOptions"
      :key="item.value"
      type="button"
      :class="{ active: query.status === item.value }"
      @click="setStatus(item.value)"
    >
      <span>{{ item.label }}</span>
      <strong>{{ summaryCount(item.value) }}</strong>
    </button>
  </nav>

  <div class="transaction-scope-note">
    <el-icon><Coin /></el-icon>
    <span>{{ selectedShopName }}</span>
    <span>共 {{ total }} 笔订单</span>
    <span>{{ resources?.appointments.length ?? 0 }} 个已完成预约待结算</span>
  </div>

  <el-alert
    v-if="error"
    class="transaction-error"
    type="error"
    :title="error"
    :closable="false"
    show-icon
  >
    <template #default>
      <el-button size="small" @click="loadPage">重新加载</el-button>
    </template>
  </el-alert>

  <div
    class="transaction-page-layout"
    :class="{ 'transaction-page-layout--editing': editorMode }"
  >
    <section class="content-section transaction-results">
      <div v-if="loading" class="transaction-loading" aria-busy="true">
        <el-skeleton :rows="7" animated />
      </div>

      <template v-else-if="records.length">
        <div class="transaction-table-wrap">
          <table class="transaction-table">
            <thead>
              <tr>
                <th>订单</th>
                <th>会员与内容</th>
                <th>金额</th>
                <th>收款</th>
                <th>状态</th>
                <th><span class="visually-hidden">操作</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in records" :key="order.id">
                <td>
                  <div class="transaction-order-cell">
                    <strong>{{ order.orderNo }}</strong>
                    <span>{{ order.businessDate }}</span>
                    <code v-if="order.appointmentNo">{{ order.appointmentNo }}</code>
                  </div>
                </td>
                <td>
                  <div class="transaction-member-cell">
                    <strong>{{ order.memberName }}</strong>
                    <span>{{ order.memberPhone }}</span>
                    <p>{{ order.itemNames }}</p>
                  </div>
                </td>
                <td>
                  <div class="transaction-amount-cell">
                    <strong>{{ formatMoney(order.payableAmount) }}</strong>
                    <span v-if="Number(order.discountAmount)">
                      已优惠 {{ formatMoney(order.discountAmount) }}
                    </span>
                    <span v-if="Number(order.refundedAmount)" class="refund-amount">
                      已退 {{ formatMoney(order.refundedAmount) }}
                    </span>
                  </div>
                </td>
                <td>
                  <div class="transaction-payment-cell">
                    <strong>{{ formatMoney(order.paidAmount) }}</strong>
                    <span>{{ paymentMethodLabel(order.paymentMethod) }}</span>
                    <span v-if="activeRefund(order)" class="pending-review">
                      {{ refundStatusLabel(activeRefund(order)!.status) }}
                    </span>
                  </div>
                </td>
                <td>
                  <span :class="statusClass(order.status)">{{ statusLabel(order.status) }}</span>
                </td>
                <td>
                  <div class="transaction-actions">
                    <el-button
                      v-if="['UNPAID', 'PARTIALLY_PAID'].includes(order.status)"
                      type="primary"
                      size="small"
                      @click="openPay(order)"
                    >
                      收款
                    </el-button>
                    <el-button v-if="canRefund(order)" text size="small" @click="openRefund(order)">
                      退款
                    </el-button>
                    <template v-if="activeRefund(order)">
                      <el-button
                        v-if="canApproveRefundItem(activeRefund(order)!)"
                        type="primary"
                        size="small"
                        :loading="actionId === activeRefund(order)?.id"
                        @click="decideRefund(order, activeRefund(order)!, 'APPROVE')"
                      >
                        批准
                      </el-button>
                      <el-button
                        v-if="canApproveRefundItem(activeRefund(order)!)"
                        text
                        size="small"
                        type="danger"
                        :disabled="actionId === activeRefund(order)?.id"
                        @click="decideRefund(order, activeRefund(order)!, 'REJECT')"
                      >
                        拒绝
                      </el-button>
                      <el-button
                        v-if="canExecuteRefundItem(order, activeRefund(order)!)"
                        type="primary"
                        size="small"
                        :loading="actionId === activeRefund(order)?.id"
                        @click="executeRefund(order, activeRefund(order)!)"
                      >
                        执行退款
                      </el-button>
                      <span
                        v-if="
                          activeRefund(order)?.status === 'PENDING'
                            && !canApproveRefundItem(activeRefund(order)!)
                        "
                        class="transaction-action-hint"
                      >
                        等待其他审批人
                      </span>
                      <span
                        v-else-if="
                          activeRefund(order)?.status === 'APPROVED'
                            && isExternalRefund(order, activeRefund(order)!)
                        "
                        class="transaction-action-hint"
                      >
                        外部通道待配置
                      </span>
                      <span
                        v-else-if="activeRefund(order)?.status === 'PROCESSING'"
                        class="transaction-action-hint"
                      >
                        通道处理中
                      </span>
                    </template>
                    <el-button text size="small" @click="openLedger(order)">账户</el-button>
                    <el-button
                      v-if="order.status === 'UNPAID'"
                      text
                      type="danger"
                      size="small"
                      @click="performVoid(order)"
                    >
                      作废
                    </el-button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="transaction-mobile-list">
          <article v-for="order in records" :key="`mobile-${order.id}`" class="transaction-mobile-item">
            <div class="transaction-mobile-heading">
              <div>
                <strong>{{ order.orderNo }}</strong>
                <span>{{ order.businessDate }}</span>
              </div>
              <span :class="statusClass(order.status)">{{ statusLabel(order.status) }}</span>
            </div>
            <h2>{{ order.memberName }} · {{ order.itemNames }}</h2>
            <p>{{ formatMoney(order.paidAmount) }} / 应付 {{ formatMoney(order.payableAmount) }}</p>
            <div class="transaction-actions">
              <el-button
                v-if="['UNPAID', 'PARTIALLY_PAID'].includes(order.status)"
                type="primary"
                size="small"
                @click="openPay(order)"
              >
                收款
              </el-button>
              <el-button v-if="canRefund(order)" text size="small" @click="openRefund(order)">
                退款
              </el-button>
              <template v-if="activeRefund(order)">
                <el-button
                  v-if="canApproveRefundItem(activeRefund(order)!)"
                  type="primary"
                  size="small"
                  :loading="actionId === activeRefund(order)?.id"
                  @click="decideRefund(order, activeRefund(order)!, 'APPROVE')"
                >
                  批准
                </el-button>
                <el-button
                  v-if="canApproveRefundItem(activeRefund(order)!)"
                  text
                  type="danger"
                  size="small"
                  @click="decideRefund(order, activeRefund(order)!, 'REJECT')"
                >
                  拒绝
                </el-button>
                <el-button
                  v-if="canExecuteRefundItem(order, activeRefund(order)!)"
                  type="primary"
                  size="small"
                  :loading="actionId === activeRefund(order)?.id"
                  @click="executeRefund(order, activeRefund(order)!)"
                >
                  执行退款
                </el-button>
                <span
                  v-if="
                    activeRefund(order)?.status === 'PENDING'
                      && !canApproveRefundItem(activeRefund(order)!)
                  "
                  class="transaction-action-hint"
                >
                  等待其他审批人
                </span>
                <span
                  v-else-if="
                    activeRefund(order)?.status === 'APPROVED'
                      && isExternalRefund(order, activeRefund(order)!)
                  "
                  class="transaction-action-hint"
                >
                  外部通道待配置
                </span>
              </template>
              <el-button text size="small" @click="openLedger(order)">账户</el-button>
            </div>
          </article>
        </div>

        <div v-if="total > query.pageSize" class="transaction-pagination">
          <span>共 {{ total }} 笔</span>
          <el-pagination
            v-model:current-page="query.page"
            :page-size="query.pageSize"
            layout="prev, pager, next"
            :total="total"
            @current-change="loadPage"
          />
        </div>
      </template>

      <div v-else class="empty-state transaction-empty">
        <el-icon><Clock /></el-icon>
        <h2>{{ query.keyword ? '没有找到匹配订单' : '当前范围内没有订单' }}</h2>
        <p>
          {{
            query.keyword
              ? '请检查订单号、会员姓名、手机号或预约号。'
              : '可以从已完成预约结算，也可以建立现场消费订单。'
          }}
        </p>
        <el-button v-if="query.keyword" @click="resetFilters">清除筛选</el-button>
        <el-button v-else type="primary" @click="openCreate">新建订单</el-button>
      </div>
    </section>

    <aside v-if="editorMode" class="transaction-editor" aria-label="交易操作">
      <div class="transaction-editor-heading">
        <div>
          <span class="environment-label">
            {{
              {
                create: '建立消费单',
                pay: '完成收款',
                refund: '提交退款',
                ledger: '会员资金明细',
              }[editorMode]
            }}
          </span>
          <h2>
            {{
              {
                create: '新建订单',
                pay: `收款 · ${selectedOrder?.orderNo ?? ''}`,
                refund: `退款 · ${selectedOrder?.orderNo ?? ''}`,
                ledger: `${selectedOrder?.memberName ?? ''}的账户`,
              }[editorMode]
            }}
          </h2>
        </div>
        <el-button text aria-label="关闭交易操作" @click="closeEditor">关闭</el-button>
      </div>

      <div v-if="editorMode === 'create'" v-loading="resourcesLoading">
        <el-form label-position="top">
          <el-form-item label="已完成预约（可选）">
            <el-select
              v-model="createForm.appointmentId"
              clearable
              filterable
              placeholder="选择后自动带入会员和护理项目"
              @change="handleAppointmentChange"
            >
              <el-option
                v-for="appointment in resources?.appointments ?? []"
                :key="appointment.id"
                :label="`${appointment.appointmentNo} · ${appointment.memberName} · ${appointment.serviceNames}`"
                :value="appointment.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="会员">
            <el-select
              v-model="createForm.memberId"
              filterable
              :disabled="Boolean(createForm.appointmentId)"
              placeholder="搜索姓名或手机号"
            >
              <el-option
                v-for="member in resources?.members ?? []"
                :key="member.id"
                :label="`${member.name} · ${member.phone}`"
                :value="member.id"
              />
            </el-select>
            <p v-if="selectedMember" class="form-helper">
              储值余额 {{ formatMoney(selectedMember.balance) }}，积分 {{ selectedMember.points }}
            </p>
          </el-form-item>
          <el-form-item v-if="selectedAppointment" label="预约护理项目">
            <div class="transaction-source-summary">
              <strong>{{ selectedAppointment.serviceNames }}</strong>
              <span>{{ formatMoney(selectedAppointment.totalAmount) }}</span>
            </div>
          </el-form-item>
          <el-form-item v-else label="护理项目">
            <el-select
              v-model="createForm.serviceIds"
              multiple
              filterable
              placeholder="可选择多个护理项目"
            >
              <el-option
                v-for="service in resources?.services ?? []"
                :key="service.id"
                :label="`${service.name} · ${formatMoney(service.price)}`"
                :value="service.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="零售产品（可选）">
            <el-select
              v-model="createForm.productIds"
              multiple
              filterable
              placeholder="可附加居家护理产品"
            >
              <el-option
                v-for="product in resources?.products ?? []"
                :key="product.id"
                :label="`${product.name} · ${formatMoney(product.price)}`"
                :value="product.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="订单备注">
            <el-input
              v-model="createForm.notes"
              type="textarea"
              :rows="3"
              maxlength="500"
              placeholder="例如：会员需要纸质小票"
            />
          </el-form-item>
        </el-form>
        <div class="transaction-total-line">
          <span>预计应付</span>
          <strong>{{ formatMoney(estimatedAmount) }}</strong>
        </div>
        <div class="transaction-editor-actions">
          <el-button @click="closeEditor">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitCreate">创建订单</el-button>
        </div>
      </div>

      <div v-else-if="editorMode === 'pay' && selectedOrder">
        <div class="transaction-bill-summary">
          <span>剩余应付</span>
          <strong>{{ formatMoney(paymentOutstanding) }}</strong>
          <p>{{ selectedOrder.memberName }} · {{ selectedOrder.itemNames }}</p>
        </div>
        <el-form label-position="top">
          <el-form-item label="收款方式">
            <el-segmented v-model="paymentForm.method" :options="paymentMethods" />
          </el-form-item>
          <el-form-item label="本次收款金额">
            <el-input-number
              v-model="paymentForm.amount"
              :min="0.01"
              :max="paymentOutstanding"
              :precision="2"
              :step="50"
              controls-position="right"
            />
            <p v-if="paymentForm.method === 'BALANCE'" class="form-helper">
              会员当前余额
              {{ formatMoney(selectedMemberForOrder(selectedOrder)?.balance ?? 0) }}
            </p>
          </el-form-item>
          <el-form-item v-if="['CARD', 'WECHAT', 'ALIPAY'].includes(paymentForm.method)" label="外部交易号（可选）">
            <el-input
              v-model="paymentForm.externalTransactionNo"
              maxlength="100"
              placeholder="支付平台或POS交易号"
            />
          </el-form-item>
        </el-form>
        <div class="transaction-editor-actions">
          <el-button @click="closeEditor">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitPayment">确认收款</el-button>
        </div>
      </div>

      <div v-else-if="editorMode === 'refund' && selectedOrder">
        <div class="transaction-bill-summary">
          <span>订单已收</span>
          <strong>{{ formatMoney(selectedOrder.paidAmount) }}</strong>
          <p>已退款 {{ formatMoney(selectedOrder.refundedAmount) }}</p>
        </div>
        <el-form label-position="top">
          <el-form-item label="原收款记录">
            <el-select v-model="refundForm.paymentId" @change="handleRefundPaymentChange">
              <el-option
                v-for="payment in selectedOrder.payments.filter(
                  (item) => item.status === 'SUCCESS' && Number(item.amount) > Number(item.refundedAmount),
                )"
                :key="payment.id"
                :label="`${paymentMethodLabel(payment.paymentMethod)} · 可退 ${formatMoney(Number(payment.amount) - Number(payment.refundedAmount))}`"
                :value="payment.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="退款金额">
            <el-input-number
              v-model="refundForm.amount"
              :min="0.01"
              :max="selectedPaymentRefundable"
              :precision="2"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="退款原因">
            <el-input
              v-model="refundForm.reason"
              type="textarea"
              :rows="3"
              maxlength="500"
              placeholder="请记录退款商品、服务问题或协商结果"
            />
          </el-form-item>
        </el-form>
        <p class="transaction-review-note">
          申请、审批和执行相互分离。余额退款在执行成功后回到账户；银行卡、微信和支付宝在通道接入前不会标记成功。
        </p>
        <div class="transaction-editor-actions">
          <el-button @click="closeEditor">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitRefund">提交审核</el-button>
        </div>
      </div>

      <div v-else-if="editorMode === 'ledger' && selectedOrder" v-loading="ledgerLoading">
        <div class="account-balance-strip">
          <div v-for="account in ledgerActivity?.accounts ?? []" :key="account.id">
            <span>{{ accountTypeLabel(account.accountType) }}</span>
            <strong>
              {{
                account.accountType === 'POINTS'
                  ? Number(account.balance)
                  : formatMoney(account.balance)
              }}
            </strong>
          </div>
        </div>
        <div v-if="ledgerActivity?.ledger.length" class="ledger-list">
          <article v-for="entry in ledgerActivity.ledger" :key="entry.id">
            <div>
              <strong>{{ ledgerEntryLabel(entry.entryType) }}</strong>
              <span>{{ formatDateTime(entry.createdAt) }}</span>
            </div>
            <div>
              <strong :class="{ positive: Number(entry.amountDelta) > 0 }">
                {{ Number(entry.amountDelta) > 0 ? '+' : '' }}{{ entry.amountDelta }}
              </strong>
              <span>余额 {{ entry.balanceAfter }}</span>
            </div>
            <p v-if="entry.remark">{{ entry.remark }}</p>
          </article>
        </div>
        <div v-else-if="!ledgerLoading" class="transaction-ledger-empty">
          当前会员还没有账户变动记录。
        </div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.transaction-page-heading .el-button {
  min-height: 42px;
}

.transaction-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 18px;
  margin-bottom: 14px;
  overflow: visible;
}

.transaction-date-range {
  flex: 0 0 auto;
}

.transaction-filters {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.transaction-filters > .el-select {
  width: 180px;
}

.transaction-filters > .el-input {
  width: min(100%, 280px);
}

.transaction-status-tabs {
  display: flex;
  gap: 2px;
  margin-bottom: 14px;
  overflow-x: auto;
  border-bottom: 1px solid var(--oc-border);
}

.transaction-status-tabs button {
  position: relative;
  display: flex;
  min-width: max-content;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border: 0;
  background: transparent;
  color: var(--oc-text-muted);
  cursor: pointer;
}

.transaction-status-tabs button::after {
  position: absolute;
  right: 14px;
  bottom: -1px;
  left: 14px;
  height: 2px;
  background: transparent;
  content: "";
}

.transaction-status-tabs button:hover,
.transaction-status-tabs button.active {
  color: var(--oc-text);
}

.transaction-status-tabs button.active::after {
  background: var(--oc-accent);
}

.transaction-status-tabs strong {
  display: grid;
  min-width: 22px;
  height: 22px;
  place-items: center;
  padding: 0 6px;
  border-radius: 6px;
  background: var(--oc-surface-2);
  color: var(--oc-text-soft);
  font-size: 11px;
}

.transaction-scope-note {
  display: flex;
  align-items: center;
  gap: 10px 18px;
  padding: 0 4px;
  margin-bottom: 16px;
  color: var(--oc-text-soft);
  font-size: 12px;
}

.transaction-scope-note .el-icon,
.transaction-scope-note span:first-of-type {
  color: var(--oc-copper);
}

.transaction-error {
  margin-bottom: 16px;
}

.transaction-page-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.transaction-page-layout--editing {
  grid-template-columns: minmax(0, 1fr) minmax(390px, 450px);
}

.transaction-results {
  min-width: 0;
  min-height: 440px;
}

.transaction-loading {
  padding: 28px 24px;
}

.transaction-table-wrap {
  overflow-x: auto;
}

.transaction-table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
  text-align: left;
}

.transaction-table th,
.transaction-table td {
  padding: 15px 16px;
  border-bottom: 1px solid var(--oc-border);
  vertical-align: middle;
}

.transaction-table th {
  background: var(--oc-surface-2);
  color: var(--oc-text-muted);
  font-size: 12px;
  font-weight: 620;
  white-space: nowrap;
}

.transaction-table tbody tr:hover {
  background: rgba(200, 111, 138, 0.045);
}

.transaction-order-cell,
.transaction-member-cell,
.transaction-amount-cell,
.transaction-payment-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.transaction-order-cell span,
.transaction-member-cell span,
.transaction-amount-cell span,
.transaction-payment-cell span {
  color: var(--oc-text-soft);
  font-size: 12px;
}

.transaction-order-cell code {
  width: fit-content;
  margin-top: 3px;
  color: var(--oc-copper);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
}

.transaction-member-cell p {
  max-width: 270px;
  margin: 4px 0 0;
  overflow: hidden;
  color: var(--oc-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.transaction-amount-cell .refund-amount,
.transaction-payment-cell .pending-review {
  color: #8a5a16;
}

.transaction-status {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 9px;
  border: 1px solid var(--oc-border);
  border-radius: 6px;
  background: var(--oc-surface-2);
  color: var(--oc-text-muted);
  font-size: 11px;
  font-weight: 650;
  white-space: nowrap;
}

.transaction-status--unpaid,
.transaction-status--partially-paid {
  border-color: rgba(225, 174, 112, 0.38);
  color: #8a5a16;
}

.transaction-status--paid {
  border-color: rgba(101, 184, 143, 0.38);
  color: var(--oc-success);
}

.transaction-status--partially-refunded {
  border-color: rgba(107, 171, 206, 0.38);
  color: #2f6789;
}

.transaction-status--refunded,
.transaction-status--void {
  color: var(--oc-text-soft);
}

.transaction-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  white-space: nowrap;
}

.transaction-action-hint {
  max-width: 112px;
  color: var(--oc-text-soft);
  font-size: 11px;
  line-height: 1.35;
  text-align: right;
  white-space: normal;
}

.transaction-mobile-list {
  display: none;
}

.transaction-pagination {
  display: flex;
  min-height: 66px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 12px 18px;
  color: var(--oc-text-soft);
  font-size: 12px;
}

.transaction-empty {
  min-height: 438px;
}

.transaction-empty > .el-icon {
  margin-bottom: 16px;
  color: var(--oc-text-soft);
  font-size: 34px;
}

.transaction-empty .el-button {
  margin-top: 20px;
}

.transaction-editor {
  position: sticky;
  top: 94px;
  padding: 22px;
  background: var(--oc-surface-1);
  border: 1px solid var(--oc-border-strong);
  border-radius: var(--oc-radius-md);
}

.transaction-editor-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--oc-border);
}

.transaction-editor-heading h2 {
  margin: 5px 0 0;
  font-size: 21px;
  letter-spacing: -0.02em;
}

.transaction-editor .el-select,
.transaction-editor .el-segmented,
.transaction-editor .el-input-number {
  width: 100%;
}

.transaction-source-summary,
.transaction-total-line,
.transaction-bill-summary {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 14px;
  background: var(--oc-surface-2);
}

.transaction-source-summary span {
  color: var(--oc-copper);
  white-space: nowrap;
}

.transaction-total-line {
  margin-top: 8px;
}

.transaction-total-line strong,
.transaction-bill-summary > strong {
  font-size: 24px;
}

.transaction-bill-summary {
  align-items: flex-start;
  flex-direction: column;
  gap: 3px;
  margin-bottom: 20px;
}

.transaction-bill-summary > span,
.transaction-bill-summary p {
  margin: 0;
  color: var(--oc-text-soft);
  font-size: 12px;
}

.transaction-review-note {
  padding: 12px 14px;
  margin: 0;
  background: var(--oc-surface-2);
  color: var(--oc-text-soft);
  font-size: 12px;
}

.transaction-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 18px;
  margin-top: 18px;
  border-top: 1px solid var(--oc-border);
}

.transaction-editor-actions .el-button {
  min-width: 92px;
}

.account-balance-strip {
  display: flex;
  gap: 1px;
  margin-bottom: 20px;
  background: var(--oc-border);
}

.account-balance-strip > div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  padding: 13px 12px;
  background: var(--oc-surface-2);
}

.account-balance-strip span {
  color: var(--oc-text-soft);
  font-size: 11px;
}

.ledger-list article {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 3px 16px;
  padding: 13px 2px;
  border-bottom: 1px solid var(--oc-border);
}

.ledger-list article > div {
  display: flex;
  flex-direction: column;
}

.ledger-list article > div:nth-child(2) {
  align-items: flex-end;
  text-align: right;
}

.ledger-list span {
  color: var(--oc-text-soft);
  font-size: 11px;
}

.ledger-list .positive {
  color: var(--oc-success);
}

.ledger-list p {
  grid-column: 1 / -1;
  margin: 4px 0 0;
  color: var(--oc-text-muted);
  font-size: 12px;
}

.transaction-ledger-empty {
  padding: 54px 18px;
  color: var(--oc-text-soft);
  text-align: center;
}

@media (max-width: 960px) {
  .transaction-page-layout--editing {
    grid-template-columns: minmax(0, 1fr);
  }

  .transaction-editor {
    position: static;
    grid-row: 1;
  }

  .transaction-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .transaction-filters {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .transaction-page-heading .el-button {
    width: 100%;
  }

  .transaction-date-range,
  .transaction-date-range :deep(.el-date-editor) {
    width: 100%;
  }

  .transaction-filters {
    align-items: stretch;
    flex-direction: column;
  }

  .transaction-filters > .el-select,
  .transaction-filters > .el-input {
    width: 100%;
  }

  .transaction-scope-note {
    align-items: flex-start;
    flex-direction: column;
  }

  .transaction-table-wrap {
    display: none;
  }

  .transaction-mobile-list {
    display: flex;
    flex-direction: column;
  }

  .transaction-mobile-item {
    padding: 18px;
    border-bottom: 1px solid var(--oc-border);
  }

  .transaction-mobile-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 14px;
  }

  .transaction-mobile-heading > div {
    display: flex;
    flex-direction: column;
  }

  .transaction-mobile-heading > div span {
    color: var(--oc-text-soft);
    font-size: 11px;
  }

  .transaction-mobile-item h2 {
    margin: 16px 0 4px;
    font-size: 15px;
  }

  .transaction-mobile-item > p {
    margin: 0;
    color: var(--oc-text-soft);
    font-size: 12px;
  }

  .transaction-mobile-item .transaction-actions {
    justify-content: flex-start;
    padding-top: 14px;
    margin-top: 14px;
    border-top: 1px solid var(--oc-border);
  }

  .transaction-editor {
    padding: 18px;
  }

  .account-balance-strip {
    flex-direction: column;
  }
}
</style>
