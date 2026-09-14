<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, ArrowLeft, ArrowRight, Check, Clock, UserFilled } from '@element-plus/icons-vue'
import {
  api,
  type BookingAvailability,
  type BookingLock,
  type BookingWaitlistItem,
} from '../api/client'
import { useAuthStore } from '../stores/auth'
import type { ServiceItem, Staff } from '../types/domain'
import { handleMediaError, mediaUrl, money } from '../utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const services = ref<ServiceItem[]>([])
const staff = ref<Staff[]>([])
const selectedServiceId = ref<number | null>(null)
const assignmentMode = ref<'SPECIFIED' | 'UNASSIGNED'>('UNASSIGNED')
const selectedStaffId = ref<number | null>(null)
const pageStart = ref(todayValue())
const selectedDate = ref(todayValue())
const selectedStartAt = ref('')
const note = ref('')
const termsConfirmed = ref(false)
const availability = ref<BookingAvailability | null>(null)
const activeLock = ref<BookingLock | null>(null)
const remainingSeconds = ref(0)
const waitlist = ref<BookingWaitlistItem[]>([])
const waitlistTimeFrom = ref('09:00')
const waitlistTimeTo = ref('18:00')
const waitlistFlexibility = ref(30)
const waitlistOtherStaff = ref(true)
const loading = ref(true)
const availabilityLoading = ref(false)
const submitting = ref(false)
const joiningWaitlist = ref(false)
const error = ref('')
const success = ref('')
const handingOffToCheckout = ref(false)
const servicePickerOpen = ref(false)
const activeServiceCategory = ref('')
const servicePicker = ref<HTMLElement | null>(null)
const servicePickerButton = ref<HTMLButtonElement | null>(null)
let countdownTimer: number | undefined

const selectedService = computed(() => services.value.find((item) => item.id === selectedServiceId.value) ?? null)
const serviceCategories = computed(() => [...new Set(services.value.map(serviceCategory))])
const filteredServices = computed(() => {
  const category = activeServiceCategory.value || serviceCategories.value[0]
  return services.value.filter((service) => serviceCategory(service) === category)
})
const selectedStaff = computed(() => staff.value.find((item) => item.id === selectedStaffId.value) ?? null)
const selectedDay = computed(() => availability.value?.days.find((day) => day.date === selectedDate.value))
const currentSlots = computed(() => selectedDay.value?.slots ?? [])
const displayStaffName = computed(() => activeLock.value?.staff.name
  ?? (assignmentMode.value === 'UNASSIGNED' ? '由系统安排' : selectedStaff.value?.name || '待选择'))
const canLoadSpecified = computed(() => assignmentMode.value === 'UNASSIGNED' || Boolean(selectedStaffId.value))
const canHold = computed(() => Boolean(
  selectedServiceId.value && selectedStartAt.value && canLoadSpecified.value && !submitting.value && !activeLock.value,
))
const canConfirm = computed(() => Boolean(activeLock.value && termsConfirmed.value && remainingSeconds.value > 0 && !submitting.value))
const canPrevious = computed(() => pageStart.value > todayValue())
const canNext = computed(() => {
  if (!availability.value) return true
  return addDays(pageStart.value, 7) <= addDays(availability.value.max_booking_date, -6)
})
const hasAnySlot = computed(() => availability.value?.days.some((day) => day.slots.length) ?? false)
const selectedTerms = computed(() => availability.value?.service)
const termsSummary = computed(() => {
  const rule = selectedTerms.value
  if (!rule) return []
  return [
    `每 ${formatDuration(rule.slot_interval_minutes)}开放一个预约开始时间，护理时长按所选项目计算`,
    `需至少提前 ${formatDuration(rule.minimum_advance_minutes)} 预约${rule.same_day_booking_allowed ? '，支持当日预约' : '，不支持当日预约'}`,
    `开始前 ${formatDuration(rule.free_cancel_minutes)} 可免费取消`,
    `开始前 ${formatDuration(rule.reschedule_cutoff_minutes)} 可改期，最多 ${rule.max_reschedules} 次`,
    latePolicyText(rule.late_cancel_policy, rule.late_cancel_value),
  ]
})

function todayValue() {
  const now = new Date()
  return localDateValue(now)
}

function localDateValue(value: Date) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
}

function addDays(value: string, days: number) {
  const date = new Date(`${value}T12:00:00`)
  date.setDate(date.getDate() + days)
  return localDateValue(date)
}

function displayDate(value: string) {
  const date = new Date(`${value}T12:00:00`)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function weekday(value: string) {
  return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][new Date(`${value}T12:00:00`).getDay()]
}

function displayTime(value: string) {
  return value.slice(11, 16)
}

function formatDuration(minutes: number) {
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60
  return remainingMinutes ? `${hours} 小时 ${remainingMinutes} 分钟` : `${hours} 小时`
}

function serviceCategory(service: ServiceItem) {
  return service.categoryName?.trim() || '其他护理'
}

function toggleServicePicker() {
  if (servicePickerOpen.value) {
    servicePickerOpen.value = false
    return
  }
  activeServiceCategory.value = selectedService.value
    ? serviceCategory(selectedService.value)
    : serviceCategories.value[0] || ''
  servicePickerOpen.value = true
}

function selectService(service: ServiceItem) {
  selectedServiceId.value = service.id
  activeServiceCategory.value = serviceCategory(service)
  servicePickerOpen.value = false
  servicePickerButton.value?.focus()
}

function closeServicePicker(returnFocus = false) {
  if (!servicePickerOpen.value) return
  servicePickerOpen.value = false
  if (returnFocus) servicePickerButton.value?.focus()
}

function handleServicePickerPointerDown(event: PointerEvent) {
  if (servicePickerOpen.value && !servicePicker.value?.contains(event.target as Node)) {
    closeServicePicker()
  }
}

function handleServicePickerKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && servicePickerOpen.value) {
    event.preventDefault()
    closeServicePicker(true)
  }
}

function latePolicyText(policy: string, value: number) {
  const labels: Record<string, string> = {
    FULL_REFUND: '临时取消仍全额退还',
    FIXED_FEE: `临时取消收取 ${money(value)} 手续费`,
    PERCENTAGE_FEE: `临时取消收取 ${value}% 手续费`,
    DEDUCT_CARD_TIMES: `临时取消扣减 ${value} 次权益`,
    NON_REFUNDABLE: '临时取消不予退还',
  }
  return labels[policy] || '临时取消按门店规则处理'
}

function countdownText(seconds: number) {
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

async function loadStaff() {
  selectedStaffId.value = null
  selectedStartAt.value = ''
  availability.value = null
  if (!selectedServiceId.value) {
    staff.value = []
    return
  }
  try {
    staff.value = await api.staff(selectedServiceId.value)
    const requestedStaffId = Number(route.query.staffId)
    if (Number.isFinite(requestedStaffId) && staff.value.some((item) => item.id === requestedStaffId)) {
      assignmentMode.value = 'SPECIFIED'
      selectedStaffId.value = requestedStaffId
    }
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '技师加载失败'
  }
}

async function loadAvailability() {
  selectedStartAt.value = ''
  availability.value = null
  if (!selectedServiceId.value || !canLoadSpecified.value) return
  availabilityLoading.value = true
  error.value = ''
  try {
    availability.value = await api.bookingAvailability(
      selectedServiceId.value,
      assignmentMode.value === 'SPECIFIED' ? selectedStaffId.value ?? undefined : undefined,
      pageStart.value,
    )
    if (!availability.value.days.some((day) => day.date === selectedDate.value)) {
      selectedDate.value = availability.value.days[0]?.date ?? pageStart.value
    }
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '可预约时间加载失败'
  } finally {
    availabilityLoading.value = false
  }
}

async function releaseActiveLock(reason = 'SELECTION_CHANGED') {
  const current = activeLock.value
  activeLock.value = null
  termsConfirmed.value = false
  remainingSeconds.value = 0
  if (!current) return
  try {
    await api.releaseBookingLock(current.lock_token, reason)
  } catch {
    // 服务端会按过期时间释放；切换选择不因重复释放而阻断。
  }
}

async function holdSlot() {
  if (!canHold.value || !selectedServiceId.value || !auth.session) {
    error.value = '请先登录会员账号并完整选择预约信息'
    return
  }
  submitting.value = true
  error.value = ''
  success.value = ''
  try {
    activeLock.value = await api.holdBooking({
      shop_id: auth.session.shopId,
      service_id: selectedServiceId.value,
      staff_id: assignmentMode.value === 'SPECIFIED' ? selectedStaffId.value ?? undefined : undefined,
      assignment_mode: assignmentMode.value,
      start_at: selectedStartAt.value,
    })
    termsConfirmed.value = false
    updateCountdown()
    startCountdown()
    success.value = '所选到店时段已暂时保留 15 分钟，请在倒计时结束前核对条款并进入结算。'
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '时段锁定失败'
    await loadAvailability()
  } finally {
    submitting.value = false
  }
}

async function confirmAppointment() {
  if (!canConfirm.value || !activeLock.value) return
  submitting.value = true
  error.value = ''
  try {
    handingOffToCheckout.value = true
    stopCountdown()
    await router.push({
      path: '/checkout',
      query: {
        lock: activeLock.value.lock_token,
        note: note.value.trim() || undefined,
      },
    })
  } catch (exception) {
    handingOffToCheckout.value = false
    error.value = exception instanceof Error ? exception.message : '无法进入结算'
    startCountdown()
  } finally {
    submitting.value = false
  }
}

async function joinWaitlist() {
  if (!selectedServiceId.value || !auth.session || !availability.value) return
  joiningWaitlist.value = true
  error.value = ''
  try {
    await api.joinBookingWaitlist({
      shop_id: auth.session.shopId,
      service_id: selectedServiceId.value,
      requested_staff_id: assignmentMode.value === 'SPECIFIED' ? selectedStaffId.value ?? undefined : undefined,
      date_from: availability.value.from_date,
      date_to: availability.value.to_date,
      time_from: waitlistTimeFrom.value,
      time_to: waitlistTimeTo.value,
      flexibility_minutes: waitlistFlexibility.value,
      accept_other_staff: assignmentMode.value === 'UNASSIGNED' || waitlistOtherStaff.value,
    })
    success.value = '已加入候补。出现空位后会为一位候补用户保留 15 分钟，仍需你确认。'
    await loadWaitlist()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '加入候补失败'
  } finally {
    joiningWaitlist.value = false
  }
}

async function confirmWaitlist(item: BookingWaitlistItem) {
  if (!item.termsVersion) return
  submitting.value = true
  try {
    const result = await api.confirmBookingWaitlist(item.id, {
      terms_version: item.termsVersion,
      terms_confirmed: true,
      member_note: note.value.trim() || undefined,
    })
    success.value = `候补预约已确认，预约号 ${result.appointment_no}`
    await loadWaitlist()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '候补确认失败'
  } finally {
    submitting.value = false
  }
}

async function cancelWaitlist(item: BookingWaitlistItem) {
  try {
    await api.cancelBookingWaitlist(item.id)
    await loadWaitlist()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '候补取消失败'
  }
}

async function loadWaitlist() {
  if (!auth.session) return
  try {
    waitlist.value = await api.bookingWaitlist()
  } catch {
    waitlist.value = []
  }
}

function updateCountdown() {
  if (!activeLock.value) {
    remainingSeconds.value = 0
    return
  }
  remainingSeconds.value = Math.max(0, Math.ceil((new Date(activeLock.value.expires_at).getTime() - Date.now()) / 1000))
  if (remainingSeconds.value === 0) {
    void releaseActiveLock('LOCK_TIMEOUT').then(loadAvailability)
    error.value = '时段保留已超时，请重新选择。'
  }
}

function startCountdown() {
  stopCountdown()
  countdownTimer = window.setInterval(updateCountdown, 1000)
}

function stopCountdown() {
  if (countdownTimer !== undefined) window.clearInterval(countdownTimer)
  countdownTimer = undefined
}

async function movePage(days: number) {
  await releaseActiveLock()
  pageStart.value = addDays(pageStart.value, days)
  selectedDate.value = pageStart.value
}

watch(selectedServiceId, async () => {
  await releaseActiveLock()
  await loadStaff()
  await loadAvailability()
})
watch([assignmentMode, selectedStaffId, pageStart], async () => {
  await releaseActiveLock()
  await loadAvailability()
})
watch(selectedDate, () => { selectedStartAt.value = '' })

onMounted(async () => {
  document.addEventListener('pointerdown', handleServicePickerPointerDown)
  document.addEventListener('keydown', handleServicePickerKeydown)
  try {
    services.value = await api.services()
    const queryId = Number(route.query.serviceId)
    if (Number.isFinite(queryId) && services.value.some((item) => item.id === queryId)) {
      selectedServiceId.value = queryId
    }
    await loadWaitlist()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '项目加载失败'
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleServicePickerPointerDown)
  document.removeEventListener('keydown', handleServicePickerKeydown)
  stopCountdown()
  if (!handingOffToCheckout.value) void releaseActiveLock('PAGE_LEFT')
})
</script>

<template>
  <div class="page-container booking-page">
    <header class="page-heading booking-heading">
      <div>
        <span class="eyebrow">BOOK A VISIT</span>
        <h1>预约到店护理</h1>
        <p>可选时段全部由服务端依据排班、休息、请假、停约、现有预约和 15 分钟时间锁实时生成。</p>
      </div>
    </header>

    <div v-if="loading" class="booking-skeleton skeleton" />
    <div v-else class="booking-layout">
      <section class="booking-form panel">
        <div class="booking-step">
          <div class="step-title"><span>01</span><div><h2>选择护理项目</h2><p>按护理类别浏览，选中后显示项目摘要</p></div></div>
          <div ref="servicePicker" class="service-picker">
            <button
              ref="servicePickerButton"
              class="service-picker-trigger"
              type="button"
              aria-haspopup="dialog"
              aria-controls="service-picker-panel"
              :aria-expanded="servicePickerOpen"
              :aria-label="selectedService ? `已选择 ${selectedService.name}，点击更换护理项目` : '选择护理项目'"
              :class="{ selected: selectedService }"
              @click="toggleServicePicker"
            >
              <template v-if="selectedService">
                <img :src="mediaUrl(selectedService.coverUrl, selectedService.id)" alt="" @error="handleMediaError($event, selectedService.id)" />
                <span class="service-picker-summary">
                  <strong>{{ selectedService.name }}</strong>
                  <small>{{ serviceCategory(selectedService) }} · {{ formatDuration(selectedService.durationMinutes) }} · {{ money(selectedService.memberPrice ?? selectedService.listPrice) }}</small>
                </span>
              </template>
              <span v-else class="service-picker-placeholder"><strong>请选择护理项目</strong><small>{{ serviceCategories.length }} 个类别，点击展开选择</small></span>
              <span class="service-picker-action"><span>{{ selectedService ? '更换项目' : '选择项目' }}</span><ArrowDown :class="{ open: servicePickerOpen }" /></span>
            </button>

            <div v-if="servicePickerOpen" id="service-picker-panel" class="service-picker-panel" role="dialog" aria-label="按类别选择护理项目">
              <header><strong>按类别选择护理项目</strong><span>当前类别 {{ filteredServices.length }} 个项目</span></header>
              <div class="service-picker-categories" role="group" aria-label="护理项目类别">
                <button
                  v-for="category in serviceCategories"
                  :key="category"
                  type="button"
                  :class="{ active: activeServiceCategory === category }"
                  :aria-pressed="activeServiceCategory === category"
                  @click="activeServiceCategory = category"
                >{{ category }}</button>
              </div>
              <div class="service-picker-list" role="listbox" :aria-label="`${activeServiceCategory}项目`">
                <button
                  v-for="service in filteredServices"
                  :key="service.id"
                  class="service-picker-option"
                  type="button"
                  role="option"
                  :aria-selected="selectedServiceId === service.id"
                  :class="{ selected: selectedServiceId === service.id }"
                  @click="selectService(service)"
                >
                  <img :src="mediaUrl(service.coverUrl, service.id)" alt="" @error="handleMediaError($event, service.id)" />
                  <span><strong>{{ service.name }}</strong><small>{{ formatDuration(service.durationMinutes) }} · {{ money(service.memberPrice ?? service.listPrice) }}</small></span>
                  <Check v-if="selectedServiceId === service.id" />
                </button>
                <div v-if="!filteredServices.length" class="inline-empty">该类别暂时没有可预约项目。</div>
              </div>
            </div>
          </div>
        </div>

        <div class="booking-step" :class="{ disabled: !selectedServiceId }">
          <div class="step-title"><span>02</span><div><h2>选择技师安排方式</h2><p>未指定时按当日已占用分钟数与同负载轮询分配</p></div></div>
          <div class="assignment-options" role="radiogroup" aria-label="技师安排方式">
            <button type="button" :class="{ selected: assignmentMode === 'UNASSIGNED' }" @click="assignmentMode = 'UNASSIGNED'">
              <strong>由系统安排</strong><small>优先选择当前负载较低的可用技师</small>
            </button>
            <button type="button" :class="{ selected: assignmentMode === 'SPECIFIED' }" @click="assignmentMode = 'SPECIFIED'">
              <strong>指定技师</strong><small>只查看所选技师可提供的时段</small>
            </button>
          </div>
          <div v-if="assignmentMode === 'SPECIFIED'" class="staff-options">
            <button
              v-for="person in staff"
              :key="person.id"
              type="button"
              :class="{ selected: selectedStaffId === person.id }"
              @click="selectedStaffId = person.id"
            >
              <img :src="mediaUrl(person.avatarUrl, person.id + 5)" :alt="person.name" @error="handleMediaError($event, person.id + 5)" />
              <span><strong>{{ person.name }}</strong><small>{{ person.levelName || person.jobRole }}</small></span>
              <Check v-if="selectedStaffId === person.id" />
            </button>
            <div v-if="!staff.length" class="inline-empty">暂无可提供该项目的技师。</div>
          </div>
        </div>

        <div class="booking-step" :class="{ disabled: !selectedServiceId || !canLoadSpecified }">
          <div class="step-title"><span>03</span><div><h2>选择日期与到店时间</h2><p>每页连续 7 天，最多开放未来 30 天</p></div></div>
          <div class="date-pager">
            <button type="button" aria-label="前 7 天" :disabled="!canPrevious" @click="movePage(-7)"><ArrowLeft /></button>
            <span>{{ displayDate(pageStart) }} — {{ displayDate(addDays(pageStart, 6)) }}</span>
            <button type="button" aria-label="后 7 天" :disabled="!canNext" @click="movePage(7)"><ArrowRight /></button>
          </div>
          <div class="date-tabs" role="tablist" aria-label="预约日期">
            <button
              v-for="day in availability?.days ?? []"
              :key="day.date"
              type="button"
              :class="{ selected: selectedDate === day.date }"
              :aria-selected="selectedDate === day.date"
              @click="selectedDate = day.date"
            >
              <small>{{ weekday(day.date) }}</small><strong>{{ displayDate(day.date) }}</strong><span>{{ day.slots.length }} 个时段</span>
            </button>
          </div>
          <div v-if="availabilityLoading" class="inline-empty">正在核对排班与占用区间…</div>
          <div v-else-if="availability && !currentSlots.length" class="inline-empty">当天暂无可预约时段，可切换日期或加入候补。</div>
          <div class="time-slots">
            <button
              v-for="slot in currentSlots"
              :key="slot.start_at"
              type="button"
              :class="{ selected: selectedStartAt === slot.start_at }"
              @click="selectedStartAt = slot.start_at"
            >
              <strong>{{ displayTime(slot.start_at) }}</strong>
              <small>{{ assignmentMode === 'UNASSIGNED' ? `${slot.staff.length} 位可安排` : slot.staff[0]?.name }}</small>
            </button>
          </div>
        </div>

        <div class="booking-step" :class="{ disabled: !selectedStartAt && !activeLock }">
          <div class="step-title"><span>04</span><div><h2>核对条款并进入结算</h2><p>先暂时保留所选到店时段 15 分钟，再确认当前版本条款并结算</p></div></div>
          <textarea v-model="note" class="form-control" maxlength="300" placeholder="选填：希望技师提前了解的护理需求"></textarea>
          <div v-if="selectedTerms" class="terms-card">
            <strong>预约规则 · 版本 {{ selectedTerms.terms_version }}</strong>
            <ul><li v-for="item in termsSummary" :key="item">{{ item }}</li></ul>
            <p v-if="selectedTerms.booking_notice">{{ selectedTerms.booking_notice }}</p>
          </div>
          <div v-if="activeLock" class="lock-confirmation" aria-live="polite">
            <div><strong>时段已保留</strong><span>剩余 {{ countdownText(remainingSeconds) }}</span></div>
            <label><input v-model="termsConfirmed" type="checkbox" /> 我已重新阅读并同意以上预约、取消与改期规则</label>
          </div>
        </div>

        <div v-if="error" class="notice">{{ error }}</div>
        <div v-if="success" class="notice success">{{ success }}</div>
        <button v-if="!activeLock" class="button button-primary submit-button" type="button" :disabled="!canHold" @click="holdSlot">
          {{ submitting ? '正在保留所选时段…' : '暂时保留所选时段 15 分钟' }}<Clock />
        </button>
        <button v-else class="button button-primary submit-button" type="button" :disabled="!canConfirm" @click="confirmAppointment">
          {{ submitting ? '正在进入结算…' : '确认条款并结算' }}<ArrowRight />
        </button>

        <section v-if="availability && !hasAnySlot" class="waitlist-panel">
          <span class="eyebrow">WAITLIST</span><h2>本页暂无合适时段？</h2>
          <p>登记候补只记录意向，不会自动创建预约。空位出现时按加入顺序匹配一位，并保留 15 分钟等待确认。</p>
          <div class="waitlist-fields">
            <label>最早时间<input v-model="waitlistTimeFrom" class="form-control" type="time" /></label>
            <label>最晚时间<input v-model="waitlistTimeTo" class="form-control" type="time" /></label>
            <label>可浮动分钟<input v-model.number="waitlistFlexibility" class="form-control" type="number" min="0" max="240" step="15" /></label>
          </div>
          <label v-if="assignmentMode === 'SPECIFIED'" class="check-row"><input v-model="waitlistOtherStaff" type="checkbox" /> 指定技师无空位时接受其他技师</label>
          <button class="button" type="button" :disabled="joiningWaitlist" @click="joinWaitlist">{{ joiningWaitlist ? '正在登记…' : '加入候补' }}</button>
        </section>
      </section>

      <aside class="booking-summary panel">
        <span class="eyebrow">YOUR BOOKING</span><h2>预约摘要</h2>
        <div v-if="selectedService" class="summary-service">
          <img :src="mediaUrl(selectedService.coverUrl, selectedService.id)" :alt="selectedService.name" @error="handleMediaError($event, selectedService.id)" />
          <div><strong>{{ selectedService.name }}</strong><span>{{ selectedService.categoryName }}</span></div>
        </div>
        <div v-else class="summary-placeholder">请先选择护理项目</div>
        <dl>
          <div><dt><UserFilled />护理技师</dt><dd>{{ displayStaffName }}</dd></div>
          <div><dt><Clock />到店时间</dt><dd>{{ selectedStartAt ? `${displayDate(selectedDate)} ${displayTime(selectedStartAt)}` : '待选择' }}</dd></div>
          <div><dt>服务时长</dt><dd>{{ selectedTerms ? `${selectedTerms.duration_minutes} 分钟` : '—' }}</dd></div>
          <div><dt>项目金额</dt><dd class="summary-price">{{ selectedService ? money(selectedService.memberPrice ?? selectedService.listPrice) : '—' }}</dd></div>
        </dl>
        <p>页面不会自行推算时段；提交前与确认时都会由服务端重新校验完整占用区间。</p>

        <div v-if="waitlist.length" class="my-waitlist">
          <strong>我的候补</strong>
          <article v-for="item in waitlist" :key="item.id">
            <div><span>{{ item.serviceName }}</span><small>{{ item.status }}</small></div>
            <p>{{ item.dateFrom }} 至 {{ item.dateTo }} · {{ item.timeFrom }}—{{ item.timeTo }}</p>
            <button v-if="item.status === 'WAITING_CONFIRMATION'" type="button" @click="confirmWaitlist(item)">确认候补时段</button>
            <button v-else-if="['WAITING','MATCHED'].includes(item.status)" type="button" @click="cancelWaitlist(item)">取消候补</button>
          </article>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.booking-page{padding-bottom:76px}.booking-heading{max-width:860px}.booking-skeleton{min-height:700px}.booking-layout{min-width:0;display:grid;grid-template-columns:minmax(0,1fr) 350px;gap:22px;align-items:start}.booking-form{min-width:0;padding:30px}.booking-step{padding:6px 0 30px;border-bottom:1px solid var(--line);transition:opacity 180ms ease}.booking-step+.booking-step{padding-top:28px}.booking-step.disabled{pointer-events:none;opacity:.42}.step-title{display:grid;grid-template-columns:35px 1fr;gap:10px;margin-bottom:18px}.step-title>span{color:var(--copper);font-size:12px;font-weight:700}.step-title h2{margin:0;font-size:19px}.step-title p{margin:5px 0 0;color:var(--text-muted);font-size:12px}.service-options,.staff-options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.service-options button,.staff-options button{position:relative;min-height:84px;display:grid;grid-template-columns:64px 1fr 20px;align-items:center;gap:12px;padding:9px;border:1px solid var(--line);border-radius:8px;color:var(--text);text-align:left;background:var(--surface);cursor:pointer}.service-options button.selected,.staff-options button.selected,.assignment-options button.selected{border-color:rgba(168,79,100,.58);background:rgba(168,79,100,.08)}.service-options img,.staff-options img{width:64px;height:64px;border-radius:6px;object-fit:cover}.service-options button>span,.staff-options button>span{display:grid;gap:6px}.service-options small,.staff-options small{color:var(--text-muted);font-size:11px}.service-options svg,.staff-options svg{width:17px;color:var(--rose-strong)}.assignment-options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-bottom:14px}.assignment-options button{display:grid;gap:4px;padding:16px;border:1px solid var(--line);border-radius:8px;background:var(--surface);color:var(--text);text-align:left;cursor:pointer}.assignment-options small{color:var(--text-muted)}.date-pager{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}.date-pager button{display:grid;width:38px;height:38px;place-items:center;border:1px solid var(--line);border-radius:6px;background:var(--surface);cursor:pointer}.date-pager button:disabled{opacity:.35;cursor:not-allowed}.date-pager svg{width:16px}.date-pager span{font-size:13px;font-weight:650}.date-tabs{display:grid;grid-template-columns:repeat(7,minmax(82px,1fr));gap:7px;overflow-x:auto;padding-bottom:8px}.date-tabs button{display:grid;gap:3px;min-width:82px;padding:10px 7px;border:1px solid var(--line);border-radius:7px;background:var(--surface);color:var(--text);cursor:pointer}.date-tabs button.selected{border-color:var(--rose);box-shadow:inset 0 0 0 1px var(--rose)}.date-tabs small,.date-tabs span{color:var(--text-muted);font-size:10px}.time-slots{display:grid;grid-template-columns:repeat(auto-fill,minmax(92px,1fr));gap:8px;margin-top:14px}.time-slots button{display:grid;gap:2px;min-height:52px;padding:8px;border:1px solid var(--line);border-radius:6px;color:var(--text-soft);background:transparent;cursor:pointer}.time-slots button small{color:var(--text-muted);font-size:10px}.time-slots button.selected{border-color:var(--rose);color:#fff;background:var(--rose)}.time-slots button.selected small{color:rgba(255,255,255,.78)}.inline-empty{margin-top:12px;padding:15px;border:1px dashed var(--line);border-radius:7px;color:var(--text-muted);font-size:13px}.terms-card{display:grid;gap:10px;margin-top:14px;padding:16px;border:1px solid var(--line);border-radius:8px;background:var(--surface-muted,#faf8f9)}.terms-card ul{display:grid;gap:6px;margin:0;padding-left:18px;color:var(--text-soft);font-size:12px}.terms-card p{margin:0;color:var(--text-muted);font-size:12px;line-height:1.7}.lock-confirmation{display:grid;gap:12px;margin-top:12px;padding:15px;border:1px solid rgba(168,79,100,.34);border-radius:8px;background:rgba(168,79,100,.06)}.lock-confirmation>div{display:flex;justify-content:space-between}.lock-confirmation span{color:var(--rose);font-variant-numeric:tabular-nums}.lock-confirmation label,.check-row{display:flex;align-items:flex-start;gap:8px;color:var(--text-soft);font-size:12px}.submit-button{width:100%;margin-top:22px}.submit-button svg{width:17px}.waitlist-panel{display:grid;gap:12px;margin-top:28px;padding:20px;border:1px solid var(--line);border-radius:8px;background:var(--surface)}.waitlist-panel h2,.waitlist-panel p{margin:0}.waitlist-panel p{color:var(--text-muted);font-size:12px;line-height:1.7}.waitlist-fields{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.waitlist-fields label{display:grid;gap:6px;color:var(--text-muted);font-size:11px}.booking-summary{min-width:0;position:sticky;top:calc(var(--header-height) + 20px);padding:26px}.booking-summary h2{margin:9px 0 20px}.summary-service{display:grid;grid-template-columns:76px 1fr;gap:13px;align-items:center;padding-bottom:19px;border-bottom:1px solid var(--line)}.summary-service img{width:76px;height:76px;border-radius:7px;object-fit:cover}.summary-service div{display:grid;gap:6px}.summary-service span{color:var(--text-muted);font-size:12px}.summary-placeholder{padding:24px 0;border-bottom:1px solid var(--line);color:var(--text-muted);font-size:13px}.booking-summary dl{display:grid;margin:12px 0}.booking-summary dl>div{display:flex;justify-content:space-between;gap:12px;padding:13px 0;border-bottom:1px solid var(--line)}.booking-summary dt{display:flex;align-items:center;gap:7px;color:var(--text-muted);font-size:12px}.booking-summary dt svg{width:14px}.booking-summary dd{min-width:0;margin:0;font-size:13px;text-align:right;overflow-wrap:anywhere}.booking-summary .summary-price{color:var(--copper);font-weight:700}.booking-summary>p{margin:17px 0;color:var(--text-muted);font-size:12px;line-height:1.7}.my-waitlist{min-width:0;display:grid;gap:9px;margin-top:18px;padding-top:18px;border-top:1px solid var(--line)}.my-waitlist article{min-width:0;display:grid;gap:5px;padding:10px;border:1px solid var(--line);border-radius:7px}.my-waitlist article>div{min-width:0;display:flex;flex-wrap:wrap;justify-content:space-between;gap:8px}.my-waitlist article>div>span{min-width:0;overflow-wrap:anywhere}.my-waitlist small{color:var(--copper)}.my-waitlist p{margin:0;color:var(--text-muted);font-size:10px;overflow-wrap:anywhere}.my-waitlist button{width:max-content;padding:0;border:0;background:none;color:var(--rose);font-size:11px;cursor:pointer}@media(max-width:900px){.booking-layout{grid-template-columns:1fr}.booking-summary{position:static;grid-row:1}}@media(max-width:640px){.booking-form{padding:20px 16px}.service-options,.staff-options,.assignment-options,.waitlist-fields{grid-template-columns:1fr}.date-tabs{margin-right:-16px}.booking-summary{padding:20px}.booking-heading p{font-size:13px}}@media(prefers-reduced-motion:reduce){.booking-step{transition:none}}
.service-picker {
  position: relative;
  z-index: 12;
}

.service-picker-trigger {
  width: 100%;
  min-height: 84px;
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  align-items: center;
  gap: 13px;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--text);
  text-align: left;
  background: var(--surface);
  cursor: pointer;
  transition: border-color 180ms ease, background-color 180ms ease;
}

.service-picker-trigger:hover,
.service-picker-trigger.selected {
  border-color: rgba(168, 79, 100, .58);
  background: rgba(168, 79, 100, .04);
}

.service-picker-trigger:focus-visible {
  outline: 3px solid rgba(168, 79, 100, .22);
  outline-offset: 2px;
}

.service-picker-trigger > img {
  width: 64px;
  height: 64px;
  border-radius: 6px;
  object-fit: cover;
}

.service-picker-summary,
.service-picker-placeholder {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.service-picker-summary strong,
.service-picker-summary small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-picker-summary small,
.service-picker-placeholder small {
  color: var(--text-muted);
  font-size: 11px;
}

.service-picker-placeholder {
  grid-column: 1 / 3;
}

.service-picker-action {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--rose);
  font-size: 12px;
  font-weight: 650;
}

.service-picker-action svg {
  width: 16px;
  transition: transform 180ms ease;
}

.service-picker-action svg.open {
  transform: rotate(180deg);
}

.service-picker-panel {
  position: absolute;
  z-index: 30;
  top: calc(100% + 8px);
  right: 0;
  left: 0;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  max-height: min(520px, 70vh);
  border-radius: 10px;
  background: var(--surface);
  box-shadow: 0 18px 44px rgba(39, 29, 34, .18);
  overflow: hidden;
}

.service-picker-panel > header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 18px 10px;
}

.service-picker-panel > header span {
  color: var(--text-muted);
  font-size: 11px;
}

.service-picker-categories {
  display: flex;
  gap: 7px;
  padding: 4px 18px 12px;
  overflow-x: auto;
  scrollbar-width: thin;
}

.service-picker-categories button {
  flex: 0 0 auto;
  min-height: 36px;
  padding: 7px 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--text-soft);
  background: var(--surface);
  cursor: pointer;
  transition: border-color 180ms ease, background-color 180ms ease, color 180ms ease;
}

.service-picker-categories button:hover,
.service-picker-categories button.active {
  border-color: var(--rose);
  color: var(--rose);
  background: rgba(168, 79, 100, .07);
}

.service-picker-list {
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
  gap: 0 14px;
  padding: 0 18px 14px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.service-picker-option {
  min-width: 0;
  min-height: 76px;
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 11px;
  padding: 10px 4px;
  border: 0;
  border-top: 1px solid var(--line);
  color: var(--text);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.service-picker-option:hover,
.service-picker-option.selected {
  background: rgba(168, 79, 100, .06);
}

.service-picker-option:focus-visible {
  outline: 2px solid var(--rose);
  outline-offset: -2px;
}

.service-picker-option img {
  width: 56px;
  height: 56px;
  border-radius: 6px;
  object-fit: cover;
}

.service-picker-option > span {
  min-width: 0;
  display: grid;
  gap: 5px;
}

.service-picker-option strong,
.service-picker-option small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-picker-option small {
  color: var(--text-muted);
  font-size: 11px;
}

.service-picker-option svg {
  width: 16px;
  color: var(--rose);
}

@media (max-width: 640px) {
  .service-picker-trigger {
    min-height: 74px;
    grid-template-columns: 54px minmax(0, 1fr) auto;
    padding: 9px;
  }

  .service-picker-trigger > img {
    width: 54px;
    height: 54px;
  }

  .service-picker-action > span {
    display: none;
  }

  .service-picker-panel {
    max-height: 64vh;
  }

  .service-picker-panel > header {
    padding: 14px 14px 9px;
  }

  .service-picker-categories {
    padding: 4px 14px 10px;
  }

  .service-picker-list {
    grid-template-columns: 1fr;
    padding: 0 14px 12px;
  }
}

@media (max-height: 560px) {
  .service-picker-panel {
    position: fixed;
    top: 12px;
    right: 12px;
    bottom: 12px;
    left: 12px;
    max-height: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .service-picker-trigger,
  .service-picker-action svg,
  .service-picker-categories button {
    transition: none;
  }
}
</style>
