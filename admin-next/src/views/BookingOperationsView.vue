<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  activateBookingScheduleRule,
  addBookingScheduleFact,
  addBookingScheduleRule,
  confirmProxyBooking,
  deactivateBookingScheduleFact,
  deactivateBookingScheduleRule,
  getAdminWeeklySchedule,
  getBookingPolicies,
  getBookingSchedules,
  getMembers,
  getServiceCatalog,
  getStaffDirectory,
  holdProxyBooking,
  processBookingWaitlistVacancy,
  updateBookingScheduleFact,
  updateBookingPolicy,
  type BookingPolicyItem,
  type BookingScheduleFact,
  type BookingScheduleResponse,
  type AdminWeeklySchedule,
  type MemberSummary,
  type ServiceCatalogItem,
  type StaffDirectoryItem,
} from '../services/api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const activeTab = ref('schedule')
const loading = ref(false)
const saving = ref(false)
const ruleActionId = ref<number>()
const policies = ref<BookingPolicyItem[]>([])
const schedules = ref<BookingScheduleResponse>({ from: '', to: '', facts: [], rules: [] })
const weekly = ref<AdminWeeklySchedule>({ shopId: 0, from: '', to: '', maxDate: '', dates: [], staff: [] })
const staff = ref<StaffDirectoryItem[]>([])
const services = ref<ServiceCatalogItem[]>([])
const members = ref<MemberSummary[]>([])
const policyDialog = ref(false)
const ruleDialog = ref(false)
const factDialog = ref(false)
const selectedDay = ref<{ staff: AdminWeeklySchedule['staff'][number]; day: AdminWeeklySchedule['staff'][number]['days'][number] }>()
const currentPolicy = reactive<BookingPolicyItem>({
  id: 0, name: '', durationMinutes: 0, slotIntervalMinutes: 60,
  bufferBeforeMinutes: 0, bufferAfterMinutes: 0, minimumAdvanceMinutes: 30,
  sameDayBookingAllowed: true, freeCancelMinutes: 1440, rescheduleCutoffMinutes: 720,
  maxReschedules: 1, lateCancelPolicy: 'FULL_REFUND', lateCancelValue: 0,
  termsVersion: 1, status: 'ACTIVE', updatedAt: '', bookingNotice: '',
})
const ruleForm = reactive({ staffId: 0, dayOfWeek: 1, startTime: '09:00', endTime: '18:00', ruleType: 'WORK' as 'WORK' | 'BREAK' | 'STOP_BOOKING', effectiveFrom: today(), effectiveTo: '' })
const factForm = reactive({ id: 0, version: 0, staffId: 0, scheduleDate: today(), startTime: '09:00', endTime: '18:00', scheduleType: 'WORK' as BookingScheduleFact['scheduleType'], remark: '' })
const proxyForm = reactive({ memberId: 0, serviceId: 0, staffId: 0, assignmentMode: 'SPECIFIED' as 'SPECIFIED' | 'UNASSIGNED', startAt: '', bypass: false, reason: '', note: '' })
const vacancyForm = reactive({ serviceId: 0, staffId: 0, startAt: '' })

const shopId = computed(() => auth.shops[0]?.id ?? auth.context?.homeShopId ?? 0)
const canManageSchedule = computed(() => auth.context?.permissions?.includes('schedule:manage') ?? false)
const fromDate = ref(today())
const toDate = computed(() => addDays(fromDate.value, 6))
const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']
const scheduleTypeLabels: Record<string, string> = { WORK: '工作', BREAK: '休息', LEAVE: '请假', BLOCKED: '占用', STOP_BOOKING: '停止预约' }

function localDateValue(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
function today() { return localDateValue(new Date()) }
function addDays(value: string, days: number) { const date = new Date(`${value}T12:00:00`); date.setDate(date.getDate() + days); return localDateValue(date) }
function editPolicy(item: BookingPolicyItem) {
  Object.assign(currentPolicy, item, { slotIntervalMinutes: 60 })
  policyDialog.value = true
}

async function load() {
  if (!shopId.value) return
  loading.value = true
  try {
    const [policyRows, scheduleRows, staffRows, serviceRows, memberRows] = await Promise.all([
      getBookingPolicies(shopId.value),
      Promise.all([
        getBookingSchedules(shopId.value, fromDate.value, toDate.value),
        getAdminWeeklySchedule(shopId.value, fromDate.value),
      ]),
      getStaffDirectory(shopId.value),
      getServiceCatalog(shopId.value),
      getMembers({ shopId: shopId.value, status: 'ACTIVE', page: 1, pageSize: 100 }),
    ])
    policies.value = policyRows
    schedules.value = scheduleRows[0]
    weekly.value = scheduleRows[1]
    staff.value = staffRows.filter((item) => item.status === 'ACTIVE')
    services.value = serviceRows.filter((item) => item.status === 'ACTIVE')
    members.value = memberRows.records
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '预约运营数据加载失败')
  } finally { loading.value = false }
}

async function savePolicy() {
  saving.value = true
  try { await updateBookingPolicy(shopId.value, currentPolicy); ElMessage.success('预约规则已更新，条款版本已递增'); policyDialog.value = false; await load() }
  finally { saving.value = false }
}

async function saveRule() {
  saving.value = true
  try {
    await addBookingScheduleRule(shopId.value, {
      staff_id: ruleForm.staffId, day_of_week: ruleForm.dayOfWeek,
      start_time: ruleForm.startTime, end_time: ruleForm.endTime,
      rule_type: ruleForm.ruleType, effective_from: ruleForm.effectiveFrom,
      effective_to: ruleForm.effectiveTo || undefined,
    })
    ElMessage.success('周期排班已保存'); ruleDialog.value = false; await load()
  } finally { saving.value = false }
}

async function saveFact() {
  saving.value = true
  try {
    const payload = {
      staff_id: factForm.staffId, schedule_date: factForm.scheduleDate,
      start_time: factForm.startTime || undefined, end_time: factForm.endTime || undefined,
      schedule_type: factForm.scheduleType, remark: factForm.remark || undefined,
    }
    if (factForm.id) {
      await updateBookingScheduleFact(shopId.value, factForm.id, { ...payload, version: factForm.version })
      ElMessage.success('排班已更新')
    } else {
      await addBookingScheduleFact(shopId.value, payload)
      ElMessage.success('日期排班已保存')
    }
    factDialog.value = false; selectedDay.value = undefined; await load()
  } finally { saving.value = false }
}

function openFactEditor(
  staffId = 0,
  date = today(),
  type: BookingScheduleFact['scheduleType'] = 'WORK',
  fact?: AdminWeeklySchedule['staff'][number]['days'][number]['scheduleFacts'][number],
) {
  Object.assign(factForm, {
    id: fact?.id ?? 0,
    version: fact?.version ?? 0,
    staffId,
    scheduleDate: date,
    startTime: fact?.startTime?.slice(0, 5) ?? (type === 'LEAVE' ? '' : '09:00'),
    endTime: fact?.endTime?.slice(0, 5) ?? (type === 'LEAVE' ? '' : '18:00'),
    scheduleType: fact?.scheduleType ?? type,
    remark: fact?.remark ?? '',
  })
  factDialog.value = true
}

async function removeFact(fact: AdminWeeklySchedule['staff'][number]['days'][number]['scheduleFacts'][number]) {
  if (!window.confirm('确认停用这条日期排班吗？已有预约的工作排班不能删除。')) return
  saving.value = true
  try {
    await deactivateBookingScheduleFact(shopId.value, fact.id, fact.version)
    ElMessage.success('排班已停用')
    selectedDay.value = undefined
    await load()
  } finally { saving.value = false }
}

async function moveWeek(days: number) {
  fromDate.value = addDays(fromDate.value, days)
  selectedDay.value = undefined
  await load()
}

function statusLabel(status: string) {
  return { AVAILABLE: '可预约', PARTIALLY_AVAILABLE: '部分可约', FULL: '已约满', UNAVAILABLE: '不可预约' }[status] ?? status
}

function scheduleLabel(status: string) {
  return { WORKING: '上班', REST: '休息', LEAVE: '请假', UNSCHEDULED: '未排班' }[status] ?? status
}

function periods(items: Array<{ start: string; end: string }>) {
  return items.length ? items.map((item) => `${item.start.slice(0,5)}–${item.end.slice(0,5)}`).join('、') : '—'
}

async function changeRuleStatus(rule: BookingScheduleResponse['rules'][number], activate: boolean) {
  try {
    await ElMessageBox.confirm(
      activate
        ? '重新启用后，该规则会再次参与可预约时间计算。'
        : '停用后，该规则不再参与新预约时间计算，历史记录仍会保留。',
      activate ? '确认重新启用周期排班' : '确认停用周期排班',
      {
        confirmButtonText: activate ? '确认启用' : '确认停用',
        cancelButtonText: '返回',
        type: activate ? 'info' : 'warning',
      },
    )
    ruleActionId.value = rule.id
    if (activate) await activateBookingScheduleRule(shopId.value, rule)
    else await deactivateBookingScheduleRule(shopId.value, rule)
    ElMessage.success(activate ? '周期排班已重新启用' : '周期排班已停用')
    await load()
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : activate ? '周期排班启用失败' : '周期排班停用失败')
  } finally {
    ruleActionId.value = undefined
  }
}

async function createProxyBooking() {
  if (!proxyForm.memberId || !proxyForm.serviceId || !proxyForm.startAt || !proxyForm.reason.trim()) {
    ElMessage.warning('请选择会员、项目和时段，并填写代客原因')
    return
  }
  saving.value = true
  try {
    const lock = await holdProxyBooking({
      shop_id: shopId.value, member_id: proxyForm.memberId, service_id: proxyForm.serviceId,
      staff_id: proxyForm.assignmentMode === 'SPECIFIED' ? proxyForm.staffId : undefined,
      assignment_mode: proxyForm.assignmentMode, start_at: proxyForm.startAt,
      bypass_minimum_advance: proxyForm.bypass, proxy_reason: proxyForm.reason,
    })
    const result = await confirmProxyBooking(lock.lock_token, lock.terms_version, proxyForm.note || undefined)
    ElMessage.success(`代客预约已创建：${result.appointment_no}`)
  } finally { saving.value = false }
}

async function matchWaitlist() {
  if (!vacancyForm.serviceId || !vacancyForm.staffId || !vacancyForm.startAt) {
    ElMessage.warning('请填写空出的项目、技师和时间')
    return
  }
  saving.value = true
  try {
    const result = await processBookingWaitlistVacancy(shopId.value, {
      service_id: vacancyForm.serviceId, staff_id: vacancyForm.staffId, start_at: vacancyForm.startAt,
    })
    if (result.matched) ElMessage.success('已为一位候补用户保留 15 分钟；未自动创建预约')
    else ElMessage.info('当前没有符合条件的候补用户')
  } finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section class="booking-ops" v-loading="loading">
    <header class="page-heading">
      <div><span class="environment-label">SC3 BOOKING</span><h1>预约与排班</h1><p>维护项目预约规则、技师排班、代客预约和候补匹配。订单、支付与权益不在本阶段。</p></div>
      <el-date-picker v-model="fromDate" type="date" value-format="YYYY-MM-DD" :clearable="false" @change="load" />
    </header>

    <el-tabs v-model="activeTab" class="ops-tabs">
      <el-tab-pane label="排班" name="schedule">
        <div class="section-actions"><div><h2>技师一周排班总览</h2><p>{{ fromDate }} 至 {{ toDate }} · 点击日期格查看每小时安排</p></div><div><el-button @click="moveWeek(-7)">上一周</el-button><el-button @click="fromDate=today(); load()">今天</el-button><el-button @click="moveWeek(7)">下一周</el-button><el-button @click="openFactEditor()">新增日期排班</el-button><el-button type="primary" @click="ruleDialog=true">新增周期规则</el-button></div></div>
        <div v-if="weekly.staff.length" class="admin-week-wrap" tabindex="0" aria-label="技师一周排班总览，可横向滚动">
          <table class="admin-week-table">
            <thead><tr><th class="staff-sticky">技师</th><th v-for="date in weekly.dates" :key="date.date" :class="{ today: date.today }"><span>{{ date.weekday }}</span><strong>{{ date.date.slice(5) }}</strong><small v-if="date.today">今天</small></th></tr></thead>
            <tbody><tr v-for="person in weekly.staff" :key="person.id"><th class="staff-sticky" scope="row"><strong>{{ person.name }}</strong><small>{{ person.levelName || '护理技师' }}</small></th><td v-for="day in person.days" :key="day.date"><button type="button" class="admin-day-cell" :class="`status-${day.availabilityStatus.toLowerCase()}`" @click="selectedDay={ staff: person, day }"><strong>{{ statusLabel(day.availabilityStatus) }}</strong><span>{{ scheduleLabel(day.scheduleStatus) }}</span><small>{{ periods(day.workPeriods) }}</small><em>{{ day.appointmentCount }} 单 · 可用 {{ day.availableMinutes }} 分钟</em></button></td></tr></tbody>
          </table>
        </div>
        <el-empty v-else description="当前门店暂无启用技师" />

        <div class="section-actions rules-heading"><div><h2>周期排班规则</h2><p>用于每周重复的上班、固定休息和停止预约时段；停用后可随时重新启用。</p></div></div>
        <div class="schedule-grid">
          <article v-for="rule in schedules.rules" :key="`r-${rule.id}`" class="schedule-card">
            <div><strong>{{ rule.staffName }}</strong><el-tag size="small" :type="rule.status === 'ACTIVE' ? 'success' : 'info'">{{ rule.status === 'ACTIVE' ? '启用中' : '已停用' }}</el-tag></div>
            <h3>每周{{ weekdayLabels[rule.dayOfWeek-1] }} · {{ scheduleTypeLabels[rule.ruleType] }}</h3>
            <p>{{ rule.startTime }}—{{ rule.endTime }} · {{ rule.effectiveFrom }} 起</p>
            <el-button
              v-if="rule.status === 'ACTIVE'"
              link
              type="danger"
              :loading="ruleActionId === rule.id"
              :disabled="!canManageSchedule || Boolean(ruleActionId)"
              @click="changeRuleStatus(rule, false)"
            >停用</el-button>
            <el-button
              v-else
              link
              type="primary"
              :loading="ruleActionId === rule.id"
              :disabled="!canManageSchedule || Boolean(ruleActionId)"
              @click="changeRuleStatus(rule, true)"
            >重新启用</el-button>
          </article>
          <el-empty v-if="!schedules.rules.length" description="暂无周期规则，可直接维护具体日期排班" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="预约规则" name="policies">
        <div class="policy-grid">
          <article v-for="item in policies" :key="item.id" class="policy-card">
            <div><span>{{ item.status }}</span><small>条款 v{{ item.termsVersion }}</small></div><h2>{{ item.name }}</h2>
            <dl><div><dt>时段间隔</dt><dd>{{ item.slotIntervalMinutes }} 分钟</dd></div><div><dt>前 / 后缓冲</dt><dd>{{ item.bufferBeforeMinutes }} / {{ item.bufferAfterMinutes }} 分钟</dd></div><div><dt>最短提前</dt><dd>{{ item.minimumAdvanceMinutes }} 分钟</dd></div><div><dt>免费取消</dt><dd>{{ item.freeCancelMinutes }} 分钟</dd></div></dl>
            <el-button @click="editPolicy(item)">编辑规则</el-button>
          </article>
        </div>
      </el-tab-pane>

      <el-tab-pane label="代客预约 / 候补" name="desk">
        <div class="desk-grid">
          <article class="desk-card"><span class="environment-label">PROXY BOOKING</span><h2>代客预约</h2><p>可记录原因后绕过最短提前量，但任何情况下都不能绕过排班与完整占用区间冲突。</p>
            <el-form label-position="top">
              <el-form-item label="会员"><el-select v-model="proxyForm.memberId" filterable><el-option v-for="item in members" :key="item.id" :label="`${item.name} · ${item.phone}`" :value="item.id" /></el-select></el-form-item>
              <el-form-item label="项目"><el-select v-model="proxyForm.serviceId"><el-option v-for="item in services" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
              <el-form-item label="安排方式"><el-radio-group v-model="proxyForm.assignmentMode"><el-radio-button value="SPECIFIED">指定技师</el-radio-button><el-radio-button value="UNASSIGNED">系统安排</el-radio-button></el-radio-group></el-form-item>
              <el-form-item v-if="proxyForm.assignmentMode==='SPECIFIED'" label="技师"><el-select v-model="proxyForm.staffId"><el-option v-for="item in staff" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
              <el-form-item label="到店时间"><el-input v-model="proxyForm.startAt" type="datetime-local" /></el-form-item>
              <el-form-item><el-checkbox v-model="proxyForm.bypass">因现场/电话安排绕过最短提前量</el-checkbox></el-form-item>
              <el-form-item label="代客原因（必填）"><el-input v-model="proxyForm.reason" type="textarea" maxlength="500" /></el-form-item>
              <el-button type="primary" :loading="saving" @click="createProxyBooking">校验并创建预约</el-button>
            </el-form>
          </article>
          <article class="desk-card"><span class="environment-label">WAITLIST</span><h2>处理空位</h2><p>每个空位只匹配一位候补，按加入时间排序并保留 15 分钟；匹配本身不会创建预约。</p>
            <el-form label-position="top">
              <el-form-item label="项目"><el-select v-model="vacancyForm.serviceId"><el-option v-for="item in services" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
              <el-form-item label="技师"><el-select v-model="vacancyForm.staffId"><el-option v-for="item in staff" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
              <el-form-item label="空出时间"><el-input v-model="vacancyForm.startAt" type="datetime-local" /></el-form-item>
              <el-button :loading="saving" @click="matchWaitlist">匹配下一位候补</el-button>
            </el-form>
          </article>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="policyDialog" :title="`预约规则 · ${currentPolicy.name}`" width="min(700px,94vw)"><el-form label-position="top" class="rule-form"><el-form-item label="预约开始时间间隔"><el-input model-value="60 分钟（与排班表一致）" disabled /><div class="field-help">护理时长仍按项目实际分钟数占用，预约开始时间统一按整点开放。</div></el-form-item><el-form-item label="服务前缓冲"><el-input-number v-model="currentPolicy.bufferBeforeMinutes" :min="0" :max="240" /></el-form-item><el-form-item label="服务后缓冲"><el-input-number v-model="currentPolicy.bufferAfterMinutes" :min="0" :max="240" /></el-form-item><el-form-item label="最短提前分钟"><el-input-number v-model="currentPolicy.minimumAdvanceMinutes" :min="0" :max="43200" /></el-form-item><el-form-item label="免费取消截止分钟"><el-input-number v-model="currentPolicy.freeCancelMinutes" :min="0" :max="43200" /></el-form-item><el-form-item label="改期截止分钟"><el-input-number v-model="currentPolicy.rescheduleCutoffMinutes" :min="0" :max="43200" /></el-form-item><el-form-item label="最多改期次数"><el-input-number v-model="currentPolicy.maxReschedules" :min="0" :max="20" /></el-form-item><el-form-item label="临时取消规则"><el-select v-model="currentPolicy.lateCancelPolicy"><el-option label="全额退还" value="FULL_REFUND" /><el-option label="固定费用" value="FIXED_FEE" /><el-option label="按比例收费" value="PERCENTAGE_FEE" /><el-option label="扣减卡次" value="DEDUCT_CARD_TIMES" /><el-option label="不予退还" value="NON_REFUNDABLE" /></el-select></el-form-item><el-form-item label="规则数值"><el-input-number v-model="currentPolicy.lateCancelValue" :min="0" /></el-form-item><el-form-item><el-checkbox v-model="currentPolicy.sameDayBookingAllowed">允许当日预约</el-checkbox></el-form-item><el-form-item label="预约须知"><el-input v-model="currentPolicy.bookingNotice" type="textarea" :rows="4" maxlength="2000" /></el-form-item></el-form><template #footer><el-button @click="policyDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="savePolicy">保存并递增条款版本</el-button></template></el-dialog>
    <el-dialog v-model="ruleDialog" title="新增周期排班" width="min(560px,94vw)"><el-form label-position="top"><el-form-item label="技师"><el-select v-model="ruleForm.staffId"><el-option v-for="item in staff" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="星期"><el-select v-model="ruleForm.dayOfWeek"><el-option v-for="(label,index) in weekdayLabels" :key="label" :label="`星期${label}`" :value="index+1" /></el-select></el-form-item><el-form-item label="类型"><el-select v-model="ruleForm.ruleType"><el-option label="工作" value="WORK" /><el-option label="固定休息" value="BREAK" /><el-option label="停止预约" value="STOP_BOOKING" /></el-select></el-form-item><el-form-item label="开始 / 结束"><div class="time-row"><el-time-select v-model="ruleForm.startTime" start="06:00" step="00:15" end="22:00" /><el-time-select v-model="ruleForm.endTime" start="06:00" step="00:15" end="23:00" /></div></el-form-item><el-form-item label="生效日期"><el-date-picker v-model="ruleForm.effectiveFrom" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-form><template #footer><el-button @click="ruleDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRule">保存</el-button></template></el-dialog>
    <el-dialog v-model="factDialog" :title="factForm.id ? '调整日期排班' : '新增日期排班'" width="min(560px,94vw)"><el-form label-position="top"><el-form-item label="技师"><el-select v-model="factForm.staffId" :disabled="Boolean(factForm.id)"><el-option v-for="item in staff" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="日期"><el-date-picker v-model="factForm.scheduleDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="安排"><el-select v-model="factForm.scheduleType"><el-option label="上班" value="WORK" /><el-option label="休息" value="BREAK" /><el-option label="请假" value="LEAVE" /><el-option label="临时占用" value="BLOCKED" /><el-option label="停止预约" value="STOP_BOOKING" /></el-select></el-form-item><el-form-item label="开始 / 结束（全天请假可留空）"><div class="time-row"><el-time-select v-model="factForm.startTime" start="06:00" step="00:15" end="22:00" clearable /><el-time-select v-model="factForm.endTime" start="06:00" step="00:15" end="23:00" clearable /></div></el-form-item><el-form-item label="内部备注"><el-input v-model="factForm.remark" maxlength="255" /></el-form-item></el-form><template #footer><el-button @click="factDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveFact">保存</el-button></template></el-dialog>

    <el-drawer :model-value="Boolean(selectedDay)" direction="rtl" size="min(520px,100vw)" :with-header="false" @close="selectedDay=undefined">
      <section v-if="selectedDay" class="day-detail">
        <header><div><span class="environment-label">{{ selectedDay.day.date }}</span><h2>{{ selectedDay.staff.name }} · {{ scheduleLabel(selectedDay.day.scheduleStatus) }}</h2><p>{{ statusLabel(selectedDay.day.availabilityStatus) }} · 已预约 {{ selectedDay.day.appointmentCount }} 单 · 可用 {{ selectedDay.day.availableMinutes }} 分钟</p></div><el-button text @click="selectedDay=undefined">关闭</el-button></header>
        <div class="detail-actions"><el-button type="primary" @click="openFactEditor(selectedDay.staff.id, selectedDay.day.date, 'WORK')">加上班时段</el-button><el-button @click="openFactEditor(selectedDay.staff.id, selectedDay.day.date, 'BREAK')">加休息时段</el-button><el-button @click="openFactEditor(selectedDay.staff.id, selectedDay.day.date, 'LEAVE')">设置请假</el-button><el-button @click="openFactEditor(selectedDay.staff.id, selectedDay.day.date, 'STOP_BOOKING')">停止预约</el-button></div>
        <div v-if="selectedDay.day.scheduleFacts.length" class="fact-list"><h3>当天特殊安排</h3><article v-for="fact in selectedDay.day.scheduleFacts" :key="fact.id"><div><strong>{{ scheduleTypeLabels[fact.scheduleType] }}</strong><span>{{ fact.startTime?.slice(0,5) || '全天' }}<template v-if="fact.endTime">–{{ fact.endTime.slice(0,5) }}</template>{{ fact.remark ? ` · ${fact.remark}` : '' }}</span></div><div><el-button link @click="openFactEditor(selectedDay.staff.id, selectedDay.day.date, fact.scheduleType, fact)">修改</el-button><el-button link type="danger" :loading="saving" @click="removeFact(fact)">停用</el-button></div></article></div>
        <div class="hour-list"><h3>每小时安排</h3><article v-for="slot in selectedDay.day.slots" :key="slot.start"><div><strong>{{ slot.start.slice(0,5) }}–{{ slot.end.slice(0,5) }}</strong><span>{{ statusLabel(slot.status) }}</span></div><ul v-if="slot.appointments.length"><li v-for="appointment in slot.appointments" :key="appointment.id"><strong>{{ appointment.customerName }}</strong><span>{{ appointment.serviceNames || '护理预约' }} · {{ appointment.start.slice(0,5) }}–{{ appointment.end.slice(0,5) }}</span></li></ul></article></div>
      </section>
    </el-drawer>
  </section>
</template>

<style scoped>
.booking-ops{grid-template-columns:minmax(0,1fr)}
.booking-ops{display:grid;min-width:0;gap:22px}.page-heading{align-items:end}.page-heading h1{margin:6px 0;font-size:clamp(30px,4vw,44px)}.page-heading p,.section-actions p,.desk-card>p{margin:0;color:var(--text-muted)}.ops-tabs{padding:22px;border:1px solid var(--line);border-radius:10px;background:var(--surface)}.section-actions{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:18px}.section-actions h2{margin:0}.rules-heading{margin-top:30px}.schedule-grid,.policy-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:12px}.schedule-card,.policy-card,.desk-card{padding:18px;border:1px solid var(--line);border-radius:9px;background:var(--surface)}.schedule-card>div,.policy-card>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.schedule-card h3{margin:16px 0 6px;font-size:15px}.schedule-card p{margin:0;color:var(--text-muted);font-size:12px}.schedule-card.fact{border-left-color:var(--copper)}.policy-card h2{margin:14px 0}.policy-card dl{display:grid;gap:7px}.policy-card dl>div{display:flex;justify-content:space-between;gap:10px}.policy-card dt{color:var(--text-muted)}.policy-card dd{margin:0}.desk-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.desk-card h2{margin:7px 0}.desk-card>p{margin-bottom:18px;line-height:1.7}.desk-card :deep(.el-select){width:100%}.rule-form{display:grid;grid-template-columns:repeat(2,1fr);gap:0 16px}.rule-form .el-form-item:last-child{grid-column:1/-1}.field-help{margin-top:7px;color:var(--text-muted);font-size:12px;line-height:1.6}.time-row{display:grid;grid-template-columns:1fr 1fr;gap:8px;width:100%}.admin-week-wrap{overflow:auto;border:1px solid var(--line);border-radius:10px;scrollbar-gutter:stable}.admin-week-table{width:100%;min-width:1160px;border-collapse:separate;border-spacing:0}.admin-week-table th,.admin-week-table td{min-width:142px;padding:0;border-right:1px solid var(--line);border-bottom:1px solid var(--line);vertical-align:top}.admin-week-table thead th{position:sticky;top:0;z-index:3;height:70px;padding:12px;background:var(--surface);text-align:left}.admin-week-table thead th span,.admin-week-table thead th strong,.admin-week-table thead th small{display:block}.admin-week-table thead th span,.admin-week-table thead th small{color:var(--text-muted);font-size:11px}.admin-week-table thead th.today{box-shadow:inset 0 3px var(--rose)}.admin-week-table .staff-sticky{position:sticky;left:0;z-index:4;min-width:150px;width:150px;background:var(--surface)}.admin-week-table tbody .staff-sticky{height:116px;padding:14px;text-align:left}.admin-week-table tbody .staff-sticky strong,.admin-week-table tbody .staff-sticky small{display:block}.admin-week-table tbody .staff-sticky small{margin-top:5px;color:var(--text-muted);font-weight:400}.admin-day-cell{width:100%;min-height:116px;display:grid;align-content:start;gap:4px;padding:12px;border:0;background:transparent;color:var(--text);text-align:left}.admin-day-cell:hover,.admin-day-cell:focus-visible{outline:2px solid var(--rose);outline-offset:-2px}.admin-day-cell span,.admin-day-cell small,.admin-day-cell em{color:var(--text-muted);font-size:11px;font-style:normal}.admin-day-cell.status-available strong{color:#287a58}.admin-day-cell.status-partially_available strong{color:#9b661b}.admin-day-cell.status-full strong{color:var(--rose)}.admin-day-cell.status-unavailable{background:var(--surface-soft)}.day-detail{display:grid;gap:20px}.day-detail>header{display:flex;justify-content:space-between;gap:12px}.day-detail h2{margin:5px 0}.day-detail header p{margin:0;color:var(--text-muted)}.detail-actions{display:flex;flex-wrap:wrap;gap:8px}.fact-list,.hour-list{display:grid;gap:8px}.fact-list h3,.hour-list h3{margin:0 0 4px}.fact-list article,.hour-list>article{padding:12px;border:1px solid var(--line);border-radius:8px}.fact-list article,.fact-list article>div,.hour-list>article>div{display:flex;align-items:center;justify-content:space-between;gap:10px}.fact-list article span,.hour-list article span{color:var(--text-muted);font-size:12px}.hour-list ul{display:grid;gap:6px;margin:10px 0 0;padding:10px 0 0;border-top:1px solid var(--line);list-style:none}.hour-list li{display:grid;grid-template-columns:minmax(80px,.4fr) 1fr;gap:8px}@media(max-width:820px){.page-heading,.section-actions{align-items:stretch;flex-direction:column}.desk-grid,.rule-form{grid-template-columns:1fr}.ops-tabs{padding:16px}.schedule-grid,.policy-grid{grid-template-columns:1fr}.rule-form .el-form-item:last-child{grid-column:auto}.admin-week-table{min-width:1120px}.admin-week-table .staff-sticky{min-width:112px;width:112px}.admin-week-table tbody .staff-sticky{padding:10px}.day-detail{padding-bottom:env(safe-area-inset-bottom)}}
</style>
