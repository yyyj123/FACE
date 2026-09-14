<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  Calendar,
  Clock,
  Edit,
  Plus,
  RefreshLeft,
  Search,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import {
  changeAppointmentStatus,
  createAppointment,
  getAppointmentAvailability,
  getAppointmentResources,
  getAppointments,
  rescheduleAppointment,
  type AppointmentAvailability,
  type AppointmentResources,
  type AppointmentStaffOption,
  type AppointmentStatus,
  type AppointmentSummary,
} from '../services/api'

type EditorMode = 'create' | 'reschedule'

interface AppointmentEditor {
  appointmentId?: number
  version?: number
  shopId?: number
  date: string
  memberId?: number
  serviceIds: number[]
  staffId?: number
  startAt: string
  source: 'ONLINE' | 'FRONT_DESK' | 'PHONE' | 'WECHAT'
  memberNote: string
  internalNote: string
  reason: string
}

const statusOptions: Array<{
  value: AppointmentStatus | 'ALL'
  label: string
}> = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待确认' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'CHECKED_IN', label: '已到店' },
  { value: 'IN_SERVICE', label: '服务中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'NO_SHOW', label: '爽约' },
]

const auth = useAuthStore()
const records = ref<AppointmentSummary[]>([])
const summary = ref<Partial<Record<AppointmentStatus, number>>>({})
const total = ref(0)
const loading = ref(true)
const resourcesLoading = ref(false)
const availabilityLoading = ref(false)
const saving = ref(false)
const actionId = ref<number>()
const error = ref('')
const editorOpen = ref(false)
const editorMode = ref<EditorMode>('create')
const formRef = ref<FormInstance>()
const dayResources = ref<AppointmentResources>()
const editorResources = ref<AppointmentResources>()
const availability = ref<AppointmentAvailability>()

const query = reactive({
  shopId: undefined as number | undefined,
  date: localDateString(new Date()),
  status: 'ALL' as AppointmentStatus | 'ALL',
  keyword: '',
  page: 1,
  pageSize: 50,
})

const editor = reactive<AppointmentEditor>({
  date: query.date,
  serviceIds: [],
  startAt: '',
  source: 'FRONT_DESK',
  memberNote: '',
  internalNote: '',
  reason: '',
})

const rules: FormRules<AppointmentEditor> = {
  shopId: [{ required: true, message: '请选择预约门店', trigger: 'change' }],
  memberId: [{ required: true, message: '请选择会员', trigger: 'change' }],
  serviceIds: [{ required: true, type: 'array', min: 1, message: '请选择美容项目', trigger: 'change' }],
  staffId: [{ required: true, message: '请选择美容师', trigger: 'change' }],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
  startAt: [{ required: true, message: '请选择可预约时间', trigger: 'change' }],
}

const selectedShopName = computed(
  () => auth.shops.find((shop) => shop.id === query.shopId)?.name ?? '当前门店',
)

const eligibleStaff = computed(() => {
  const staff = editorResources.value?.staff ?? []
  if (!editor.serviceIds.length) return staff
  return staff.filter((person) => {
    const skills = parseIds(person.serviceIds)
    return editor.serviceIds.every((serviceId) => skills.includes(serviceId))
  })
})

const selectedServices = computed(() => {
  const services = editorResources.value?.services ?? []
  return services.filter((service) => editor.serviceIds.includes(service.id))
})

const estimatedPrice = computed(() =>
  selectedServices.value.reduce((sum, service) => sum + Number(service.memberPrice || 0), 0),
)

const scheduledStaff = computed(() => {
  const schedules = dayResources.value?.schedules ?? []
  return schedules.filter((schedule) => schedule.scheduleType === 'WORK')
})

const unavailableStaff = computed(() => {
  const schedules = dayResources.value?.schedules ?? []
  return schedules.filter((schedule) => schedule.scheduleType !== 'WORK')
})

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  query.shopId =
    auth.context?.homeShopId && auth.shops.some((shop) => shop.id === auth.context?.homeShopId)
      ? auth.context.homeShopId
      : auth.shops[0]?.id
  await loadPage()
})

async function loadPage() {
  if (!query.shopId) {
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [page, resources] = await Promise.all([
      getAppointments({
        shopId: query.shopId,
        fromDate: query.date,
        toDate: query.date,
        status: query.status,
        keyword: query.keyword.trim() || undefined,
        page: query.page,
        pageSize: query.pageSize,
      }),
      getAppointmentResources(query.shopId, query.date),
    ])
    records.value = page.records
    total.value = page.total
    summary.value = page.summary
    dayResources.value = resources
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '预约中心加载失败'
  } finally {
    loading.value = false
  }
}

async function applyFilters() {
  query.page = 1
  closeEditor()
  await loadPage()
}

async function resetFilters() {
  query.status = 'ALL'
  query.keyword = ''
  query.page = 1
  await loadPage()
}

async function moveDate(offset: number) {
  const date = parseLocalDate(query.date)
  date.setDate(date.getDate() + offset)
  query.date = localDateString(date)
  await applyFilters()
}

function setStatus(status: AppointmentStatus | 'ALL') {
  query.status = status
  void applyFilters()
}

function resetEditor() {
  Object.assign(editor, {
    appointmentId: undefined,
    version: undefined,
    shopId: query.shopId,
    date: query.date,
    memberId: undefined,
    serviceIds: [],
    staffId: undefined,
    startAt: '',
    source: 'FRONT_DESK',
    memberNote: '',
    internalNote: '',
    reason: '',
  })
  availability.value = undefined
  formRef.value?.clearValidate()
}

async function openCreate() {
  editorMode.value = 'create'
  resetEditor()
  editorResources.value = dayResources.value
  editorOpen.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openReschedule(appointment: AppointmentSummary) {
  editorMode.value = 'reschedule'
  Object.assign(editor, {
    appointmentId: appointment.id,
    version: appointment.version,
    shopId: appointment.shopId,
    date: appointment.startAt.slice(0, 10),
    memberId: appointment.memberId,
    serviceIds: parseIds(appointment.serviceIds),
    staffId: appointment.staffId,
    startAt: '',
    source: appointment.source,
    memberNote: appointment.memberNote ?? '',
    internalNote: appointment.internalNote ?? '',
    reason: '',
  })
  editorOpen.value = true
  await loadEditorResources()
  await loadAvailability()
  await nextTick()
  formRef.value?.clearValidate()
}

function closeEditor() {
  editorOpen.value = false
  resetEditor()
}

async function loadEditorResources() {
  if (!editor.shopId || !editor.date) return
  resourcesLoading.value = true
  try {
    editorResources.value = await getAppointmentResources(editor.shopId, editor.date)
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '预约资源加载失败')
  } finally {
    resourcesLoading.value = false
  }
}

async function handleEditorDateChange() {
  editor.startAt = ''
  availability.value = undefined
  await loadEditorResources()
  if (editor.staffId && editor.serviceIds.length) await loadAvailability()
}

async function handleServicesChange() {
  editor.startAt = ''
  availability.value = undefined
  if (editor.staffId && !eligibleStaff.value.some((staff) => staff.id === editor.staffId)) {
    editor.staffId = undefined
  }
  if (editor.staffId) await loadAvailability()
}

async function handleStaffChange() {
  editor.startAt = ''
  await loadAvailability()
}

async function loadAvailability() {
  if (!editor.shopId || !editor.staffId || !editor.date || !editor.serviceIds.length) {
    availability.value = undefined
    return
  }
  availabilityLoading.value = true
  try {
    availability.value = await getAppointmentAvailability(
      editor.shopId,
      editor.staffId,
      editor.date,
      editor.serviceIds,
    )
  } catch (reason) {
    availability.value = undefined
    ElMessage.error(reason instanceof Error ? reason.message : '可预约时间加载失败')
  } finally {
    availabilityLoading.value = false
  }
}

async function saveAppointment() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (
    !valid ||
    !editor.shopId ||
    !editor.memberId ||
    !editor.staffId ||
    !editor.startAt ||
    !editor.serviceIds.length
  ) {
    return
  }

  saving.value = true
  try {
    if (editorMode.value === 'create') {
      const created = await createAppointment({
        shopId: editor.shopId,
        memberId: editor.memberId,
        staffId: editor.staffId,
        serviceIds: editor.serviceIds,
        startAt: editor.startAt,
        source: editor.source,
        memberNote: editor.memberNote.trim() || undefined,
        internalNote: editor.internalNote.trim() || undefined,
      })
      ElMessage.success(`预约 ${created.appointmentNo} 创建成功`)
    } else if (editor.appointmentId && editor.version !== undefined) {
      await rescheduleAppointment(editor.appointmentId, {
        shopId: editor.shopId,
        staffId: editor.staffId,
        startAt: editor.startAt,
        version: editor.version,
        reason: editor.reason.trim() || undefined,
      })
      ElMessage.success('预约已改期')
    }
    closeEditor()
    await loadPage()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '预约保存失败')
  } finally {
    saving.value = false
  }
}

async function performStatusAction(
  appointment: AppointmentSummary,
  target: AppointmentStatus,
) {
  if (actionId.value !== undefined) return
  actionId.value = appointment.id
  const needsReason = target === 'CANCELLED' || target === 'NO_SHOW'
  let reason = ''
  try {
    if (needsReason) {
      const result = await ElMessageBox.prompt(
        target === 'CANCELLED'
          ? '取消后该时段会重新释放，请填写取消原因。'
          : '请记录会员未到店的原因或联系结果。',
        target === 'CANCELLED' ? '取消预约' : '标记爽约',
        {
          confirmButtonText: '确认提交',
          cancelButtonText: '返回',
          inputPlaceholder: '请输入原因',
          inputValidator: (value) => Boolean(value?.trim()) || '必须填写原因',
          inputType: 'textarea',
        },
      )
      reason = result.value.trim()
    } else {
      await ElMessageBox.confirm(
        `确认将 ${appointment.memberName} 的预约更新为“${statusLabel(target)}”？`,
        '更新预约状态',
        {
          confirmButtonText: '确认更新',
          cancelButtonText: '返回',
          type: target === 'COMPLETED' ? 'success' : 'info',
        },
      )
    }
    await changeAppointmentStatus(appointment.id, {
      shopId: appointment.shopId,
      status: target,
      version: appointment.version,
      reason: reason || undefined,
    })
    ElMessage.success(`预约已更新为${statusLabel(target)}`)
    await loadPage()
  } catch (reasonOrCancel) {
    if (reasonOrCancel === 'cancel' || reasonOrCancel === 'close') return
    ElMessage.error(
      reasonOrCancel instanceof Error ? reasonOrCancel.message : '预约状态更新失败',
    )
  } finally {
    actionId.value = undefined
  }
}

function primaryAction(status: AppointmentStatus) {
  const actions: Partial<Record<AppointmentStatus, { status: AppointmentStatus; label: string }>> = {
    PENDING: { status: 'CONFIRMED', label: '确认预约' },
    CONFIRMED: { status: 'CHECKED_IN', label: '确认到店' },
    CHECKED_IN: { status: 'IN_SERVICE', label: '开始服务' },
    IN_SERVICE: { status: 'COMPLETED', label: '完成服务' },
  }
  return actions[status]
}

function canCancel(status: AppointmentStatus) {
  return ['PENDING', 'CONFIRMED', 'CHECKED_IN'].includes(status)
}

function canReschedule(status: AppointmentStatus) {
  return ['PENDING', 'CONFIRMED'].includes(status)
}

function statusLabel(status: AppointmentStatus | 'ALL') {
  return statusOptions.find((item) => item.value === status)?.label ?? status
}

function statusClass(status: AppointmentStatus) {
  return `appointment-status appointment-status--${status.toLowerCase().replace('_', '-')}`
}

function summaryCount(status: AppointmentStatus | 'ALL') {
  if (status === 'ALL') {
    return Object.values(summary.value).reduce((sum, count) => sum + Number(count || 0), 0)
  }
  return Number(summary.value[status] || 0)
}

function formatTime(value: string) {
  return value.slice(11, 16)
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(Number(value || 0))
}

function formatScheduleTime(value?: string) {
  return value ? value.slice(0, 5) : '全天'
}

function parseIds(value?: string) {
  if (!value) return []
  return value
    .split(',')
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item))
}

function parseLocalDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  if (year === undefined || month === undefined || day === undefined) {
    return new Date()
  }
  return new Date(year, month - 1, day)
}

function localDateString(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function disablePastDates(date: Date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

function staffSkillLabel(staff: AppointmentStaffOption) {
  const count = parseIds(staff.serviceIds).length
  return count ? `${count} 个可服务项目` : '暂未配置项目'
}
</script>

<template>
  <section class="page-heading appointment-page-heading">
    <div>
      <p class="environment-label">每日预约运营</p>
      <h1>预约与排班</h1>
      <p>处理预约确认、到店、服务和改期，系统会自动校验美容师排班、项目技能与时间冲突。</p>
    </div>
    <el-button type="primary" :disabled="!query.shopId" @click="openCreate">
      <el-icon><Plus /></el-icon>
      新建预约
    </el-button>
  </section>

  <section class="content-section appointment-toolbar" aria-label="预约筛选">
    <div class="appointment-date-control">
      <el-button text aria-label="前一天" @click="moveDate(-1)">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <el-date-picker
        v-model="query.date"
        type="date"
        value-format="YYYY-MM-DD"
        aria-label="预约日期"
        @change="applyFilters"
      />
      <el-button text aria-label="后一天" @click="moveDate(1)">
        <el-icon><ArrowRight /></el-icon>
      </el-button>
    </div>
    <div class="appointment-filters">
      <el-select v-model="query.shopId" aria-label="预约门店" @change="applyFilters">
        <el-option
          v-for="shop in auth.shops"
          :key="shop.id"
          :label="shop.name"
          :value="shop.id"
        />
      </el-select>
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="预约号、会员或美容师"
        aria-label="搜索预约"
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

  <nav class="appointment-status-tabs" aria-label="预约状态筛选">
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

  <section v-if="dayResources" class="schedule-strip" aria-label="当日排班">
    <div class="schedule-strip-title">
      <el-icon><Calendar /></el-icon>
      <div>
        <strong>{{ selectedShopName }}排班</strong>
        <span>{{ scheduledStaff.length }} 个在岗班次</span>
      </div>
    </div>
    <div v-if="scheduledStaff.length" class="schedule-shifts">
      <span v-for="schedule in scheduledStaff" :key="schedule.id">
        <strong>{{ schedule.staffName }}</strong>
        {{ formatScheduleTime(schedule.startTime) }}–{{ formatScheduleTime(schedule.endTime) }}
      </span>
    </div>
    <span v-else class="schedule-empty">当天尚未安排在岗班次</span>
    <span v-if="unavailableStaff.length" class="schedule-unavailable">
      {{ unavailableStaff.length }} 个请假/锁定时段
    </span>
  </section>

  <el-alert
    v-if="error"
    class="appointment-error"
    type="error"
    :title="error"
    :closable="false"
    show-icon
  >
    <template #default>
      <el-button size="small" @click="loadPage">重新加载</el-button>
    </template>
  </el-alert>

  <div class="appointment-page-layout" :class="{ 'appointment-page-layout--editing': editorOpen }">
    <section class="content-section appointment-results">
      <div v-if="loading" class="appointment-loading" aria-busy="true">
        <el-skeleton :rows="7" animated />
      </div>

      <template v-else-if="records.length">
        <div class="appointment-table-wrap">
          <table class="appointment-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>会员与项目</th>
                <th>美容师</th>
                <th>金额</th>
                <th>状态</th>
                <th><span class="visually-hidden">操作</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="appointment in records" :key="appointment.id">
                <td>
                  <div class="appointment-time">
                    <strong>{{ formatTime(appointment.startAt) }}</strong>
                    <span>至 {{ formatTime(appointment.endAt) }}</span>
                    <code>{{ appointment.appointmentNo }}</code>
                  </div>
                </td>
                <td>
                  <div class="appointment-person">
                    <strong>{{ appointment.memberName }}</strong>
                    <span>{{ appointment.memberPhone }}</span>
                    <p>{{ appointment.serviceNames }}</p>
                  </div>
                </td>
                <td>
                  <div class="appointment-staff">
                    <strong>{{ appointment.staffName }}</strong>
                    <span>{{ appointment.staffLevel || appointment.shopName }}</span>
                  </div>
                </td>
                <td><strong>{{ formatMoney(appointment.totalPrice) }}</strong></td>
                <td>
                  <span :class="statusClass(appointment.status)">
                    {{ statusLabel(appointment.status) }}
                  </span>
                </td>
                <td>
                  <div class="appointment-actions">
                    <el-button
                      v-if="canReschedule(appointment.status)"
                      text
                      size="small"
                      @click="openReschedule(appointment)"
                    >
                      <el-icon><Edit /></el-icon>
                      改期
                    </el-button>
                    <el-button
                      v-if="primaryAction(appointment.status)"
                      size="small"
                      type="primary"
                      :loading="actionId === appointment.id"
                      @click="
                        performStatusAction(
                          appointment,
                          primaryAction(appointment.status)!.status,
                        )
                      "
                    >
                      {{ primaryAction(appointment.status)!.label }}
                    </el-button>
                    <el-button
                      v-if="appointment.status === 'CONFIRMED'"
                      text
                      size="small"
                      @click="performStatusAction(appointment, 'NO_SHOW')"
                    >
                      爽约
                    </el-button>
                    <el-button
                      v-if="canCancel(appointment.status)"
                      text
                      size="small"
                      type="danger"
                      @click="performStatusAction(appointment, 'CANCELLED')"
                    >
                      取消
                    </el-button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="appointment-mobile-list">
          <article
            v-for="appointment in records"
            :key="`mobile-${appointment.id}`"
            class="appointment-mobile-item"
          >
            <div class="appointment-mobile-heading">
              <div>
                <time>{{ formatTime(appointment.startAt) }}–{{ formatTime(appointment.endAt) }}</time>
                <span>{{ appointment.appointmentNo }}</span>
              </div>
              <span :class="statusClass(appointment.status)">
                {{ statusLabel(appointment.status) }}
              </span>
            </div>
            <h2>{{ appointment.memberName }} · {{ appointment.serviceNames }}</h2>
            <p>{{ appointment.staffName }} · {{ formatMoney(appointment.totalPrice) }}</p>
            <div class="appointment-actions">
              <el-button
                v-if="canReschedule(appointment.status)"
                text
                size="small"
                @click="openReschedule(appointment)"
              >
                改期
              </el-button>
              <el-button
                v-if="primaryAction(appointment.status)"
                type="primary"
                size="small"
                @click="
                  performStatusAction(appointment, primaryAction(appointment.status)!.status)
                "
              >
                {{ primaryAction(appointment.status)!.label }}
              </el-button>
              <el-button
                v-if="canCancel(appointment.status)"
                text
                size="small"
                type="danger"
                @click="performStatusAction(appointment, 'CANCELLED')"
              >
                取消
              </el-button>
            </div>
          </article>
        </div>
      </template>

      <div v-else class="empty-state appointment-empty">
        <el-icon><Clock /></el-icon>
        <h2>{{ query.keyword ? '没有找到匹配预约' : '当天没有该状态的预约' }}</h2>
        <p>
          {{
            query.keyword
              ? '请检查预约号、会员姓名、手机号或美容师姓名。'
              : '可以切换状态查看，或直接为到店会员建立新预约。'
          }}
        </p>
        <el-button v-if="query.keyword" @click="resetFilters">清除筛选</el-button>
        <el-button v-else type="primary" @click="openCreate">新建预约</el-button>
      </div>
    </section>

    <aside v-if="editorOpen" class="appointment-editor" aria-label="预约编辑">
      <div class="appointment-editor-heading">
        <div>
          <span class="environment-label">
            {{ editorMode === 'create' ? '建立新预约' : '调整预约时间' }}
          </span>
          <h2>{{ editorMode === 'create' ? '新建预约' : '预约改期' }}</h2>
        </div>
        <el-button text aria-label="关闭预约编辑" @click="closeEditor">关闭</el-button>
      </div>

      <el-form
        ref="formRef"
        v-loading="resourcesLoading"
        :model="editor"
        :rules="rules"
        label-position="top"
      >
        <div class="appointment-form-row">
          <el-form-item label="预约门店" prop="shopId">
            <el-select v-model="editor.shopId" disabled>
              <el-option
                v-for="shop in auth.shops"
                :key="shop.id"
                :label="shop.name"
                :value="shop.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="预约日期" prop="date">
            <el-date-picker
              v-model="editor.date"
              type="date"
              value-format="YYYY-MM-DD"
              :disabled-date="disablePastDates"
              @change="handleEditorDateChange"
            />
          </el-form-item>
        </div>

        <el-form-item label="会员" prop="memberId">
          <el-select
            v-model="editor.memberId"
            filterable
            :disabled="editorMode === 'reschedule'"
            placeholder="搜索会员姓名或手机号"
          >
            <el-option
              v-for="member in editorResources?.members ?? []"
              :key="member.id"
              :label="`${member.name} · ${member.phone}`"
              :value="member.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="美容项目" prop="serviceIds">
          <el-select
            v-model="editor.serviceIds"
            multiple
            filterable
            :disabled="editorMode === 'reschedule'"
            placeholder="可选择多个项目"
            @change="handleServicesChange"
          >
            <el-option
              v-for="service in editorResources?.services ?? []"
              :key="service.id"
              :label="`${service.name} · ${formatMoney(service.memberPrice)}`"
              :value="service.id"
            />
          </el-select>
          <p v-if="selectedServices.length" class="form-helper">
            预计 {{ availability?.durationMinutes ?? '—' }} 分钟，会员价合计
            {{ formatMoney(estimatedPrice) }}
          </p>
        </el-form-item>

        <el-form-item label="美容师" prop="staffId">
          <el-select
            v-model="editor.staffId"
            filterable
            placeholder="先选择项目，再选择美容师"
            @change="handleStaffChange"
          >
            <el-option
              v-for="staff in eligibleStaff"
              :key="staff.id"
              :label="`${staff.name} · ${staffSkillLabel(staff)}`"
              :value="staff.id"
            />
          </el-select>
          <p v-if="editor.serviceIds.length && !eligibleStaff.length" class="form-helper form-helper--error">
            暂无美容师同时具备所选项目技能。
          </p>
        </el-form-item>

        <el-form-item label="可预约时间" prop="startAt">
          <div v-if="availabilityLoading" class="appointment-slot-loading">
            正在核对排班与已占用时间…
          </div>
          <div v-else-if="availability?.slots.length" class="appointment-slot-grid">
            <button
              v-for="slot in availability.slots"
              :key="slot.startAt"
              type="button"
              :class="{ active: editor.startAt === slot.startAt }"
              @click="editor.startAt = slot.startAt"
            >
              {{ formatTime(slot.startAt) }}
            </button>
          </div>
          <div
            v-else-if="editor.staffId && editor.serviceIds.length"
            class="appointment-slot-empty"
          >
            当天没有满足项目时长的空闲时段，请更换日期或美容师。
          </div>
          <p v-else class="form-helper">选择项目和美容师后显示可约时间。</p>
        </el-form-item>

        <template v-if="editorMode === 'create'">
          <el-form-item label="预约来源">
            <el-segmented
              v-model="editor.source"
              :options="[
                { label: '前台', value: 'FRONT_DESK' },
                { label: '电话', value: 'PHONE' },
                { label: '微信', value: 'WECHAT' },
                { label: '线上', value: 'ONLINE' },
              ]"
            />
          </el-form-item>
          <el-form-item label="会员备注">
            <el-input
              v-model="editor.memberNote"
              type="textarea"
              :rows="2"
              maxlength="1000"
              placeholder="会员提出的需求或注意事项"
            />
          </el-form-item>
          <el-form-item label="门店内部备注">
            <el-input
              v-model="editor.internalNote"
              type="textarea"
              :rows="2"
              maxlength="1000"
              placeholder="仅员工可见"
            />
          </el-form-item>
        </template>
        <el-form-item v-else label="改期原因">
          <el-input
            v-model="editor.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="例如：会员临时调整时间"
          />
        </el-form-item>
      </el-form>

      <div class="appointment-editor-actions">
        <el-button @click="closeEditor">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAppointment">
          {{ saving ? '正在保存' : editorMode === 'create' ? '创建预约' : '确认改期' }}
        </el-button>
      </div>
    </aside>
  </div>
</template>
