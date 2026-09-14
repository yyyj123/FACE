<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api/client'
import AppointmentCard from '../components/AppointmentCard.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import { useAuthStore } from '../stores/auth'
import { dateTime, money } from '../utils/format'
import type {
  Appointment,
  CustomerConfirmation,
  MemberRefund,
  PackageInstance,
  ServiceRecordDetail,
  ServiceResources,
} from '../types/domain'

interface ConsumptionLine {
  key: string
  optionKey: string
  quantity: number
}

const auth = useAuthStore()
const appointments = ref<Appointment[]>([])
const confirmations = ref<CustomerConfirmation[]>([])
const refunds = ref<MemberRefund[]>([])
const selectedStatus = ref('')
const loading = ref(true)
const busyId = ref<number | null>(null)
const confirmationBusyId = ref<number | null>(null)
const rejectingId = ref<number | null>(null)
const rejectionReason = ref('')
const error = ref('')
const selectedRecord = ref<ServiceRecordDetail>()
const resources = ref<ServiceResources>()
const consumptionLines = ref<ConsumptionLine[]>([])
const memberPackages = ref<PackageInstance[]>([])
const packageWriteOffBusy = ref(false)
const packageWriteOffSelection = ref('')
const packageWriteOffQuantity = ref(1)
const packageWriteOffReason = ref('')

const careForm = reactive({
  serviceSummary: '',
  nextVisitRecommendation: '',
  skinType: '',
  concernsText: '',
  observations: '',
  homeCareAdvice: '',
  nextRecommendedAt: '',
})

const filtered = computed(() => selectedStatus.value
  ? appointments.value.filter((item) => item.status === selectedStatus.value)
  : appointments.value)

const pendingConfirmations = computed(() =>
  confirmations.value.filter((item) => item.status === 'PENDING'),
)
const recentRefunds = computed(() => refunds.value.slice(0, 5))
const availableConsumables = computed(() =>
  (resources.value?.consumables ?? []).filter((item) => Number(item.quantityAvailable) > 0),
)

const writeOffOptions = computed(() => {
  const serviceIds = new Set(selectedRecord.value?.items?.map((item) => item.serviceId) ?? [])
  return memberPackages.value.flatMap((instance) =>
    instance.items
      .filter((item) =>
        instance.status === 'ACTIVE'
        && Number(item.remainingQuantity) > 0
        && (!serviceIds.size || serviceIds.has(item.serviceId)),
      )
      .map((item) => ({
        key: `${instance.id}:${item.serviceId}`,
        instance,
        item,
      })),
  )
})

const tabs = computed(() => auth.isTechnician
  ? [
      { value: '', label: '全部' },
      { value: 'PENDING', label: '待确认' },
      { value: 'CONFIRMED', label: '待到店' },
      { value: 'CHECKED_IN', label: '已到店' },
      { value: 'IN_SERVICE', label: '护理中' },
      { value: 'COMPLETED', label: '已完成' },
    ]
  : [
      { value: '', label: '全部' },
      { value: 'PENDING', label: '待确认' },
      { value: 'CONFIRMED', label: '待到店' },
      { value: 'COMPLETED', label: '已完成' },
      { value: 'CANCELLED', label: '已取消' },
    ])

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (auth.isTechnician) {
      appointments.value = await api.appointments()
    } else {
      const memberId = auth.session?.memberId
      const shopId = auth.session?.shopId
      const [appointmentData, confirmationData, refundData] = await Promise.all([
        api.appointments(),
        api.confirmations(),
        memberId && shopId ? api.memberRefunds(memberId, shopId) : Promise.resolve([]),
      ])
      appointments.value = appointmentData
      confirmations.value = confirmationData
      refunds.value = refundData
    }
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '预约记录加载失败'
  } finally {
    loading.value = false
  }
}

async function cancel(appointment: Appointment) {
  if (!window.confirm('确认取消这条预约吗？')) return
  busyId.value = appointment.id
  error.value = ''
  try {
    await api.cancelAppointment(appointment.id, appointment.version)
    await load()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '取消预约失败'
  } finally {
    busyId.value = null
  }
}

async function updateStatus(appointment: Appointment, status: string) {
  busyId.value = appointment.id
  error.value = ''
  try {
    await api.updateAppointmentStatus(appointment.id, status, appointment.version)
    await load()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '状态更新失败'
  } finally {
    busyId.value = null
  }
}

async function startCare(appointment: Appointment) {
  busyId.value = appointment.id
  error.value = ''
  try {
    const detail = await api.startService({
      shopId: appointment.shopId,
      appointmentId: appointment.id,
      appointmentVersion: appointment.version,
    })
    await openCareWorkspace(detail, appointment.shopId)
    await load()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '开始护理失败'
  } finally {
    busyId.value = null
  }
}

async function openCare(appointment: Appointment) {
  if (!appointment.serviceRecordId) return
  busyId.value = appointment.id
  error.value = ''
  try {
    const detail = await api.serviceRecord(appointment.serviceRecordId, appointment.shopId)
    await openCareWorkspace(detail, appointment.shopId)
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '护理记录加载失败'
  } finally {
    busyId.value = null
  }
}

async function openCareWorkspace(detail: ServiceRecordDetail, shopId: number) {
  const [resourceData, packageData] = await Promise.all([
    api.serviceResources(shopId),
    api.memberPackages(detail.memberId, shopId),
  ])
  selectedRecord.value = detail
  resources.value = resourceData
  Object.assign(careForm, {
    serviceSummary: detail.serviceSummary ?? '',
    nextVisitRecommendation: detail.nextVisitRecommendation ?? '',
    skinType: detail.skinType ?? '',
    concernsText: detail.concerns?.join('、') ?? '',
    observations: detail.observations ?? '',
    homeCareAdvice: detail.homeCareAdvice ?? '',
    nextRecommendedAt: detail.nextRecommendedAt?.slice(0, 10) ?? '',
  })
  consumptionLines.value = []
  memberPackages.value = packageData
  packageWriteOffSelection.value = ''
  packageWriteOffQuantity.value = 1
  packageWriteOffReason.value = ''
  requestAnimationFrame(() => {
    document.querySelector('.care-workspace')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

async function writeOffPackage() {
  const record = selectedRecord.value
  if (!record || !packageWriteOffSelection.value) {
    error.value = '请选择本次护理需要核销的套餐项目'
    return
  }
  const parts = packageWriteOffSelection.value.split(':')
  const instanceId = Number(parts[0])
  const serviceId = Number(parts[1])
  const option = writeOffOptions.value.find((item) => item.key === packageWriteOffSelection.value)
  if (
    !option
    || !Number.isFinite(instanceId)
    || !Number.isFinite(serviceId)
    || packageWriteOffQuantity.value <= 0
  ) {
    error.value = '套餐核销数量不正确'
    return
  }
  if (packageWriteOffQuantity.value > Number(option.item.remainingQuantity)) {
    error.value = '核销数量不能超过套餐项目剩余次数'
    return
  }
  packageWriteOffBusy.value = true
  error.value = ''
  try {
    await api.writeOffPackage(instanceId, {
      shop_id: record.shopId,
      service_record_id: record.id,
      service_id: serviceId,
      quantity: packageWriteOffQuantity.value,
      version: option.instance.version,
      reason: packageWriteOffReason.value.trim() || undefined,
    })
    memberPackages.value = await api.memberPackages(record.memberId, record.shopId)
    packageWriteOffSelection.value = ''
    packageWriteOffQuantity.value = 1
    packageWriteOffReason.value = ''
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '套餐核销失败'
  } finally {
    packageWriteOffBusy.value = false
  }
}

function addConsumption() {
  if (!availableConsumables.value.length) {
    error.value = '当前护理耗材间没有可领用库存，请联系管理员补充库存'
    return
  }
  consumptionLines.value.push({
    key: uniqueKey('line'),
    optionKey: '',
    quantity: 1,
  })
}

function removeConsumption(key: string) {
  consumptionLines.value = consumptionLines.value.filter((line) => line.key !== key)
}

async function completeCare() {
  const record = selectedRecord.value
  if (!record) return
  if (!careForm.serviceSummary.trim() || !careForm.observations.trim()) {
    error.value = '请填写服务总结和护理观察'
    return
  }
  busyId.value = record.appointmentId ?? record.id
  error.value = ''
  try {
    const consumptions = consumptionLines.value.map((line) => {
      const [productId, locationId] = line.optionKey.split(':').map(Number)
      const option = resources.value?.consumables.find(
        (item) => item.productId === productId && item.locationId === locationId,
      )
      if (!option) throw new Error('请完整选择耗材和领用位置')
      if (line.quantity <= 0 || line.quantity > Number(option.quantityAvailable)) {
        throw new Error(`${option.productName} 的领用数量超出可用库存`)
      }
      return {
        locationId: option.locationId,
        productId: option.productId,
        quantity: line.quantity,
        balanceVersion: option.balanceVersion,
      }
    })
    await api.completeService(record.id, {
      shopId: record.shopId,
      version: record.version,
      serviceSummary: careForm.serviceSummary.trim(),
      nextVisitRecommendation: careForm.nextVisitRecommendation.trim() || undefined,
      skinType: careForm.skinType || undefined,
      concerns: careForm.concernsText.split(/[、,，]/).map((item) => item.trim()).filter(Boolean),
      observations: careForm.observations.trim(),
      homeCareAdvice: careForm.homeCareAdvice.trim() || undefined,
      nextRecommendedAt: careForm.nextRecommendedAt || undefined,
      consumptions,
      idempotencyKey: uniqueKey('service-complete'),
    })
    selectedRecord.value = undefined
    resources.value = undefined
    consumptionLines.value = []
    await load()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '完成护理失败'
  } finally {
    busyId.value = null
  }
}

async function actOnConfirmation(
  confirmation: CustomerConfirmation,
  action: 'CONFIRMED' | 'REJECTED',
) {
  const reason = action === 'REJECTED' ? rejectionReason.value.trim() : undefined
  if (action === 'REJECTED' && !reason) {
    error.value = '拒绝护理结果时请填写原因'
    return
  }
  confirmationBusyId.value = confirmation.id
  error.value = ''
  try {
    await api.actOnConfirmation(confirmation.id, {
      action,
      reason,
      version: confirmation.version,
      idempotencyKey: uniqueKey(`confirmation-${confirmation.id}`),
    })
    rejectingId.value = null
    rejectionReason.value = ''
    await load()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '护理结果确认失败'
  } finally {
    confirmationBusyId.value = null
  }
}

function concernText(value?: string) {
  if (!value) return ''
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.join('、') : value
  } catch {
    return value
  }
}

function refundStage(refund: MemberRefund) {
  if (refund.status === 'SUCCESS') return 3
  if (['APPROVED', 'PROCESSING', 'FAILED'].includes(refund.status)) return 2
  return 1
}

function refundStatusText(refund: MemberRefund) {
  return {
    PENDING: '等待门店审核',
    APPROVED: '已批准，等待执行',
    PROCESSING: '退款处理中',
    SUCCESS: '退款已完成',
    REJECTED: '退款未通过',
    FAILED: '执行未完成',
  }[refund.status]
}

function refundStatusDescription(refund: MemberRefund) {
  if (refund.status === 'SUCCESS') {
    return refund.paymentMethod === 'BALANCE'
      ? '款项已退回会员余额。'
      : '门店账务已确认退款完成。'
  }
  if (refund.status === 'REJECTED') {
    return refund.decisionNote || '门店未批准本次退款申请。'
  }
  if (refund.status === 'FAILED') {
    return '退款尚未成功，门店会核对后重新执行。'
  }
  if (
    refund.status === 'APPROVED'
    && ['CARD', 'WECHAT', 'ALIPAY'].includes(refund.paymentMethod)
  ) {
    return '门店已批准，正在等待支付通道处理，不会提前标记完成。'
  }
  return refund.reason
}

function uniqueKey(prefix: string) {
  const value = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
  return `${prefix}-${value}`.slice(0, 80)
}

onMounted(load)
</script>

<template>
  <div class="page-container appointments-page">
    <header class="page-heading">
      <div>
        <span class="page-context">{{ auth.isTechnician ? '技师工作台' : '个人服务中心' }}</span>
        <h1>{{ auth.isTechnician ? '今日护理任务' : '预约与护理记录' }}</h1>
        <p>
          {{ auth.isTechnician
            ? '按签到、开工、护理记录和完成顺序处理本人任务。完成后库存与顾客确认会同步生成。'
            : '预约状态、护理结果和本人确认都集中在这里，记录与门店保持一致。' }}
        </p>
      </div>
      <router-link v-if="!auth.isTechnician" class="button button-primary" to="/booking">
        新增预约
      </router-link>
    </header>

    <div v-if="error" class="notice appointment-notice" role="alert">{{ error }}</div>

    <section
      v-if="!auth.isTechnician && recentRefunds.length"
      class="refund-progress-section"
      aria-labelledby="refund-progress-title"
    >
      <div class="section-copy">
        <h2 id="refund-progress-title">近期退款进度</h2>
        <p>申请、审核与实际退款分开记录，完成时间以门店账务确认为准。</p>
      </div>
      <article v-for="refund in recentRefunds" :key="refund.id" class="refund-progress-row">
        <div class="refund-progress-main">
          <div class="refund-progress-heading">
            <div>
              <strong>{{ money(refund.amount) }}</strong>
              <span>{{ refundStatusText(refund) }}</span>
            </div>
            <small>{{ refund.refundNo }}</small>
          </div>
          <ol class="refund-steps" :aria-label="`退款 ${refund.refundNo} 当前进度`">
            <li :class="{ complete: refundStage(refund) > 1, active: refundStage(refund) === 1 }">
              已申请
            </li>
            <li
              :class="{
                complete: refundStage(refund) > 2,
                active: refundStage(refund) === 2 && refund.status !== 'REJECTED',
                rejected: refund.status === 'REJECTED',
              }"
            >
              {{ refund.status === 'REJECTED' ? '未通过' : '已审核' }}
            </li>
            <li :class="{ complete: refund.status === 'SUCCESS' }">已退款</li>
          </ol>
          <p>{{ refundStatusDescription(refund) }}</p>
        </div>
        <dl class="refund-progress-meta">
          <div>
            <dt>订单</dt>
            <dd>{{ refund.orderNo }}</dd>
          </div>
          <div>
            <dt>申请时间</dt>
            <dd>{{ dateTime(refund.createdAt) }}</dd>
          </div>
        </dl>
      </article>
    </section>

    <section
      v-if="!auth.isTechnician && pendingConfirmations.length"
      class="confirmation-section"
      aria-labelledby="confirmation-title"
    >
      <div class="section-copy">
        <h2 id="confirmation-title">待确认的护理结果</h2>
        <p>请核对本次护理记录。确认状态独立保存，不会改写已完成的服务事实。</p>
      </div>
      <article
        v-for="confirmation in pendingConfirmations"
        :key="confirmation.id"
        class="confirmation-row"
      >
        <div class="confirmation-main">
          <div class="confirmation-title">
            <strong>{{ confirmation.serviceNames || '本次护理' }}</strong>
            <span>{{ confirmation.staffName }} · {{ confirmation.recordNo }}</span>
          </div>
          <dl class="confirmation-facts">
            <div>
              <dt>服务总结</dt>
              <dd>{{ confirmation.serviceSummary || '门店未填写' }}</dd>
            </div>
            <div>
              <dt>护理观察</dt>
              <dd>{{ confirmation.observations || '门店未填写' }}</dd>
            </div>
            <div v-if="confirmation.homeCareAdvice">
              <dt>居家建议</dt>
              <dd>{{ confirmation.homeCareAdvice }}</dd>
            </div>
            <div v-if="concernText(confirmation.concerns)">
              <dt>关注点</dt>
              <dd>{{ concernText(confirmation.concerns) }}</dd>
            </div>
          </dl>
        </div>
        <div class="confirmation-actions">
          <button
            class="button button-primary button-small"
            type="button"
            :disabled="confirmationBusyId === confirmation.id"
            @click="actOnConfirmation(confirmation, 'CONFIRMED')"
          >
            确认无误
          </button>
          <button
            class="button button-quiet button-small"
            type="button"
            :disabled="confirmationBusyId === confirmation.id"
            @click="rejectingId = rejectingId === confirmation.id ? null : confirmation.id"
          >
            反馈问题
          </button>
        </div>
        <div v-if="rejectingId === confirmation.id" class="rejection-form">
          <label :for="`reject-${confirmation.id}`">问题说明</label>
          <textarea
            :id="`reject-${confirmation.id}`"
            v-model="rejectionReason"
            class="form-control"
            rows="3"
            maxlength="500"
            placeholder="请说明记录中需要门店核对的内容"
          />
          <button
            class="button button-secondary button-small"
            type="button"
            :disabled="confirmationBusyId === confirmation.id"
            @click="actOnConfirmation(confirmation, 'REJECTED')"
          >
            提交反馈
          </button>
        </div>
      </article>
    </section>

    <section v-if="auth.isTechnician && selectedRecord" class="care-workspace panel">
      <div class="workspace-heading">
        <div>
          <span>护理单 {{ selectedRecord.recordNo }}</span>
          <h2>记录本次护理并完成服务</h2>
          <p>{{ selectedRecord.memberName }} · {{ selectedRecord.staffName }}</p>
        </div>
        <button class="text-button" type="button" @click="selectedRecord = undefined">关闭</button>
      </div>

      <div class="care-form-grid">
        <div class="field">
          <label for="skin-type">肤质</label>
          <select id="skin-type" v-model="careForm.skinType" class="form-control">
            <option value="">未选择</option>
            <option v-for="item in resources?.skinTypes ?? []" :key="item" :value="item">
              {{ item }}
            </option>
          </select>
        </div>
        <div class="field">
          <label for="next-date">建议复访日期</label>
          <input id="next-date" v-model="careForm.nextRecommendedAt" class="form-control" type="date" />
        </div>
        <div class="field full">
          <label for="concerns">皮肤关注点</label>
          <input
            id="concerns"
            v-model="careForm.concernsText"
            class="form-control"
            placeholder="多个关注点可用顿号分隔"
          />
        </div>
        <div class="field full">
          <label for="summary">服务总结 <span aria-hidden="true">*</span></label>
          <textarea id="summary" v-model="careForm.serviceSummary" class="form-control" maxlength="1000" />
        </div>
        <div class="field full">
          <label for="observations">护理观察 <span aria-hidden="true">*</span></label>
          <textarea id="observations" v-model="careForm.observations" class="form-control" maxlength="4000" />
        </div>
        <div class="field full">
          <label for="home-advice">居家护理建议</label>
          <textarea id="home-advice" v-model="careForm.homeCareAdvice" class="form-control" maxlength="4000" />
        </div>
        <div class="field full">
          <label for="next-visit">下次到店建议</label>
          <textarea id="next-visit" v-model="careForm.nextVisitRecommendation" class="form-control" maxlength="1000" />
        </div>
      </div>

      <div class="consumption-section">
        <div class="consumption-heading">
          <div>
            <h3>本次耗材</h3>
            <p>完成服务时同步扣减库存并生成不可覆盖的流水。</p>
          </div>
          <button
            class="button button-secondary button-small"
            type="button"
            :disabled="!availableConsumables.length"
            @click="addConsumption"
          >
            添加耗材
          </button>
        </div>
        <div v-if="!availableConsumables.length" class="inventory-empty" role="status">
          <strong>暂无可领用耗材</strong>
          <p>护理耗材间没有可用库存，请先联系管理员登记产品并完成入库。</p>
        </div>
        <p v-else-if="!consumptionLines.length" class="inline-hint">
          如本次未使用耗材，可直接完成护理。
        </p>
        <div v-for="line in consumptionLines" :key="line.key" class="consumption-line">
          <select v-model="line.optionKey" class="form-control" aria-label="选择耗材">
            <option value="">选择耗材与领用位置</option>
            <option
              v-for="item in availableConsumables"
              :key="`${item.productId}:${item.locationId}`"
              :value="`${item.productId}:${item.locationId}`"
            >
              {{ item.productName }} · {{ item.locationName }} · 可用 {{ item.quantityAvailable }} {{ item.unitName }}
            </option>
          </select>
          <input
            v-model.number="line.quantity"
            class="form-control quantity-input"
            type="number"
            min="0.001"
            step="0.001"
            aria-label="耗材数量"
          />
          <button class="text-button danger-text" type="button" @click="removeConsumption(line.key)">
            移除
          </button>
        </div>
      </div>

      <div class="package-writeoff-section">
        <div class="consumption-heading">
          <div>
            <h3>套餐核销</h3>
            <p>仅显示当前会员可用且与本次服务匹配的项目，提交后生成不可覆盖流水。</p>
          </div>
        </div>
        <p v-if="!writeOffOptions.length" class="inline-hint">
          当前会员没有可用于本次护理的套餐项目。
        </p>
        <div v-else class="package-writeoff-form">
          <div class="field package-option-field">
            <label for="package-writeoff-option">套餐项目</label>
            <select id="package-writeoff-option" v-model="packageWriteOffSelection" class="form-control">
              <option value="">请选择套餐与项目</option>
              <option v-for="option in writeOffOptions" :key="option.key" :value="option.key">
                {{ option.instance.packageName }} · {{ option.item.serviceName }} · 剩余 {{ option.item.remainingQuantity }} 次
              </option>
            </select>
          </div>
          <div class="field">
            <label for="package-writeoff-quantity">核销次数</label>
            <input
              id="package-writeoff-quantity"
              v-model.number="packageWriteOffQuantity"
              class="form-control"
              type="number"
              min="0.0001"
              step="1"
            />
          </div>
          <div class="field package-reason-field">
            <label for="package-writeoff-reason">备注</label>
            <input
              id="package-writeoff-reason"
              v-model="packageWriteOffReason"
              class="form-control"
              maxlength="500"
              placeholder="可选，记录特殊说明"
            />
          </div>
          <button
            class="button button-secondary"
            type="button"
            :disabled="packageWriteOffBusy || !packageWriteOffSelection"
            @click="writeOffPackage"
          >
            {{ packageWriteOffBusy ? '正在核销…' : '确认核销' }}
          </button>
        </div>
      </div>

      <div class="workspace-actions">
        <button class="button button-quiet" type="button" @click="selectedRecord = undefined">稍后填写</button>
        <button class="button button-primary" type="button" :disabled="busyId !== null" @click="completeCare">
          {{ busyId !== null ? '正在完成…' : '完成护理并同步库存' }}
        </button>
      </div>
    </section>

    <div class="status-tabs" aria-label="筛选预约状态">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        :class="{ active: selectedStatus === tab.value }"
        type="button"
        @click="selectedStatus = tab.value"
      >
        {{ tab.label }}
      </button>
    </div>

    <LoadingState v-if="loading" class="appointment-loading" label="预约记录加载中" />
    <div v-else-if="filtered.length" class="appointment-list">
      <AppointmentCard
        v-for="appointment in filtered"
        :key="appointment.id"
        :appointment="appointment"
        :technician="auth.isTechnician"
        :busy="busyId === appointment.id"
        @cancel="cancel"
        @status="updateStatus"
        @start="startCare"
        @care="openCare"
      />
    </div>
    <EmptyState
      v-else
      :title="auth.isTechnician ? '当前没有对应任务' : '还没有预约记录'"
      :description="auth.isTechnician ? '新的预约分配给你后会显示在这里。' : '选择护理项目、技师和时间后，记录会同步显示在这里。'"
      :action-label="auth.isTechnician ? '' : '预约护理'"
      :to="auth.isTechnician ? '' : '/booking'"
    />
  </div>
</template>

<style scoped>
.appointments-page {
  padding-bottom: 76px;
}

.page-context {
  color: var(--copper);
  font-size: 13px;
  font-weight: 700;
}

.appointment-notice {
  margin: 18px 0;
}

.refund-progress-section {
  margin: 26px 0 30px;
  border-top: 1px solid var(--line-strong);
  border-bottom: 1px solid var(--line);
}

.refund-progress-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(190px, 0.36fr);
  gap: 28px;
  padding: 22px 0;
  border-top: 1px solid var(--line);
}

.refund-progress-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 18px;
}

.refund-progress-heading > div {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.refund-progress-heading strong {
  color: var(--text);
  font-size: 20px;
}

.refund-progress-heading span {
  color: var(--rose-strong);
  font-size: 13px;
  font-weight: 700;
}

.refund-progress-heading small {
  color: var(--text-muted);
  font-size: 11px;
}

.refund-steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 18px 0 12px;
  padding: 0;
  list-style: none;
}

.refund-steps li {
  position: relative;
  padding-top: 18px;
  color: var(--text-muted);
  font-size: 12px;
}

.refund-steps li::before {
  position: absolute;
  top: 3px;
  left: 0;
  width: 9px;
  height: 9px;
  border: 2px solid var(--line-strong);
  border-radius: 50%;
  background: var(--surface);
  content: "";
  z-index: 1;
}

.refund-steps li:not(:last-child)::after {
  position: absolute;
  top: 7px;
  right: 8px;
  left: 9px;
  height: 1px;
  background: var(--line-strong);
  content: "";
}

.refund-steps li.complete,
.refund-steps li.active {
  color: var(--text);
  font-weight: 700;
}

.refund-steps li.complete::before,
.refund-steps li.active::before {
  border-color: var(--rose);
  background: var(--rose);
}

.refund-steps li.complete::after {
  background: rgba(168, 79, 100, 0.55);
}

.refund-steps li.rejected {
  color: var(--danger);
  font-weight: 700;
}

.refund-steps li.rejected::before {
  border-color: var(--danger);
}

.refund-progress-main > p {
  margin: 0;
  color: var(--text-soft);
  font-size: 13px;
  line-height: 1.6;
}

.refund-progress-meta {
  display: grid;
  align-content: start;
  gap: 12px;
  margin: 0;
}

.refund-progress-meta div {
  display: grid;
  gap: 3px;
}

.refund-progress-meta dt {
  color: var(--text-muted);
  font-size: 11px;
}

.refund-progress-meta dd {
  margin: 0;
  color: var(--text-soft);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.status-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  margin: 22px 0 24px;
  padding-bottom: 4px;
}

.status-tabs button {
  flex: 0 0 auto;
  min-height: 38px;
  padding: 0 15px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--text-soft);
  background: transparent;
  cursor: pointer;
}

.status-tabs button:hover,
.status-tabs button.active {
  border-color: rgba(168, 79, 100, 0.5);
  color: var(--rose-strong);
  background: rgba(168, 79, 100, 0.08);
}

.appointment-loading {
  display: grid;
  gap: 14px;
}

.appointment-loading .skeleton {
  min-height: 210px;
}

.confirmation-section {
  margin: 34px 0 28px;
  border-top: 1px solid var(--line-strong);
  border-bottom: 1px solid var(--line);
}

.section-copy {
  padding: 24px 0 18px;
}

.section-copy h2,
.workspace-heading h2,
.consumption-heading h3 {
  margin: 0;
}

.section-copy p,
.workspace-heading p,
.consumption-heading p {
  margin: 7px 0 0;
  color: var(--text-soft);
  line-height: 1.65;
}

.confirmation-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 20px;
  padding: 22px 0;
  border-top: 1px solid var(--line);
}

.confirmation-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.confirmation-title span {
  color: var(--text-muted);
  font-size: 12px;
}

.confirmation-facts {
  display: grid;
  gap: 10px;
  margin: 16px 0 0;
}

.confirmation-facts div {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  gap: 12px;
}

.confirmation-facts dt {
  color: var(--text-muted);
  font-size: 12px;
}

.confirmation-facts dd {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.6;
}

.confirmation-actions {
  display: flex;
  align-items: start;
  gap: 8px;
}

.rejection-form {
  grid-column: 1 / -1;
  display: grid;
  gap: 9px;
  padding: 16px;
  background: var(--surface-strong);
}

.rejection-form label {
  color: var(--text-soft);
  font-size: 13px;
  font-weight: 700;
}

.rejection-form .button {
  justify-self: start;
}

.care-workspace {
  scroll-margin-top: 92px;
  margin: 34px 0;
  padding: 24px;
}

.workspace-heading,
.consumption-heading,
.workspace-actions {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 20px;
}

.workspace-heading > div > span {
  color: var(--copper);
  font-size: 12px;
  font-weight: 700;
}

.care-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 26px;
}

.care-form-grid .full {
  grid-column: 1 / -1;
}

.consumption-section,
.package-writeoff-section {
  margin-top: 28px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}

.package-writeoff-form {
  display: grid;
  grid-template-columns: minmax(240px, 1.5fr) minmax(120px, 0.45fr) minmax(220px, 1fr) auto;
  align-items: end;
  gap: 12px;
  margin-top: 18px;
  padding: 18px;
  background: var(--surface-strong);
}

.package-writeoff-form .field {
  margin: 0;
}

.consumption-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 130px auto;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.inline-hint {
  margin: 16px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.inventory-empty {
  display: grid;
  gap: 5px;
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--surface-strong);
}

.inventory-empty strong {
  color: var(--text);
  font-size: 13px;
}

.inventory-empty p {
  margin: 0;
  color: var(--text-soft);
  font-size: 12px;
  line-height: 1.6;
}

.danger-text {
  color: var(--danger);
}

.workspace-actions {
  justify-content: flex-end;
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

@media (max-width: 760px) {
  .refund-progress-row {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .refund-progress-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .confirmation-row {
    grid-template-columns: 1fr;
  }

  .confirmation-actions {
    justify-content: flex-start;
  }

  .care-form-grid {
    grid-template-columns: 1fr;
  }

  .care-form-grid .full {
    grid-column: auto;
  }

  .consumption-line {
    grid-template-columns: 1fr 100px;
  }

  .package-writeoff-form {
    grid-template-columns: 1fr 120px;
  }

  .package-option-field,
  .package-reason-field,
  .package-writeoff-form .button {
    grid-column: 1 / -1;
  }

  .consumption-line .danger-text {
    grid-column: 1 / -1;
    justify-self: start;
  }
}

@media (max-width: 520px) {
  .refund-progress-heading,
  .refund-progress-heading > div {
    align-items: flex-start;
    flex-direction: column;
    gap: 5px;
  }

  .refund-progress-meta {
    grid-template-columns: 1fr;
  }

  .confirmation-title,
  .workspace-heading,
  .consumption-heading,
  .workspace-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .confirmation-facts div {
    grid-template-columns: 1fr;
    gap: 3px;
  }

  .confirmation-actions,
  .workspace-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .package-writeoff-form {
    grid-template-columns: 1fr;
  }

  .package-writeoff-form > * {
    grid-column: auto;
  }

  .confirmation-actions .button,
  .workspace-actions .button {
    width: 100%;
  }
}
</style>
