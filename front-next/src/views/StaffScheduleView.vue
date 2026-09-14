<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type PublicWeeklySchedule, type WeeklyAvailabilityStatus } from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import { useAuthStore } from '../stores/auth'

type StaffDay = PublicWeeklySchedule['staff'][number]['days'][number]
type StaffItem = PublicWeeklySchedule['staff'][number]

const auth = useAuthStore()
const loading = ref(true)
const error = ref('')
const fromDate = ref(today())
const schedule = ref<PublicWeeklySchedule>()
const selected = ref<{ staff: StaffItem; day: StaffDay }>()

const canGoBack = computed(() => fromDate.value > today())
const canGoForward = computed(() => {
  if (!schedule.value) return false
  return addDays(fromDate.value, 13) <= schedule.value.maxDate
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    schedule.value = await api.staffWeeklySchedule(fromDate.value, auth.session?.shopId)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '技师排班加载失败'
  } finally {
    loading.value = false
  }
}

async function move(days: number) {
  const next = addDays(fromDate.value, days)
  fromDate.value = next < today() ? today() : next
  await load()
}

function today() {
  const now = new Date()
  return localDate(now)
}

function addDays(value: string, days: number) {
  const date = new Date(`${value}T12:00:00`)
  date.setDate(date.getDate() + days)
  return localDate(date)
}

function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function shortDate(value: string) {
  const [, month, day] = value.split('-')
  return `${month}/${day}`
}

function periods(day: StaffDay) {
  if (!day.workPeriods.length) return statusLabel(day.availabilityStatus)
  return day.workPeriods.map((item) => `${item.start.slice(0, 5)}–${item.end.slice(0, 5)}`).join('、')
}

function statusLabel(status: WeeklyAvailabilityStatus) {
  return {
    AVAILABLE: '可预约',
    PARTIALLY_AVAILABLE: '部分可约',
    FULL: '已约满',
    UNAVAILABLE: '不可预约',
  }[status]
}

function scheduleLabel(status: StaffDay['scheduleStatus']) {
  return { WORKING: '上班', REST: '休息', LEAVE: '请假', UNSCHEDULED: '未排班' }[status]
}
</script>

<template>
  <div class="weekly-page page-container page-section">
    <header class="page-heading">
      <div>
        <span class="page-context">一周排班</span>
        <h1>看看哪位技师有空</h1>
        <p>连续查看 7 天上班时间与可预约情况，点开日期可查看每小时状态。</p>
      </div>
      <router-link class="button button-primary button-small" to="/booking">去预约护理</router-link>
    </header>

    <div class="week-toolbar" aria-label="切换排班日期">
      <button class="button button-secondary button-small" type="button" :disabled="!canGoBack || loading" @click="move(-7)">上一周</button>
      <strong>{{ schedule?.from || fromDate }} 至 {{ schedule?.to || addDays(fromDate, 6) }}</strong>
      <button class="button button-secondary button-small" type="button" :disabled="!canGoForward || loading" @click="move(7)">下一周</button>
      <button class="text-button" type="button" :disabled="fromDate === today() || loading" @click="fromDate=today(); load()">回到今天</button>
    </div>

    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="schedule-loading" aria-label="正在加载排班">
      <span v-for="n in 5" :key="n" class="skeleton" />
    </div>

    <div v-else-if="schedule?.staff.length" class="weekly-table-wrap" tabindex="0" aria-label="技师一周排班表，可横向滚动">
      <table class="weekly-table">
        <thead>
          <tr>
            <th class="staff-column">技师</th>
            <th v-for="date in schedule.dates" :key="date.date" :class="{ today: date.today }">
              <span>{{ date.weekday }}</span><strong>{{ shortDate(date.date) }}</strong><small v-if="date.today">今天</small>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="person in schedule.staff" :key="person.id">
            <th class="staff-column" scope="row">
              <img v-if="person.avatarUrl" :src="person.avatarUrl" :alt="`${person.name}头像`" />
              <span v-else class="avatar-fallback" aria-hidden="true">{{ person.name.slice(0, 1) }}</span>
              <span><strong>{{ person.name }}</strong><small>{{ person.levelName || '护理技师' }}</small></span>
            </th>
            <td v-for="day in person.days" :key="day.date">
              <button class="day-cell" type="button" :class="`status-${day.availabilityStatus.toLowerCase()}`" @click="selected={ staff: person, day }">
                <strong>{{ statusLabel(day.availabilityStatus) }}</strong>
                <span>{{ scheduleLabel(day.scheduleStatus) }}</span>
                <small>{{ periods(day) }}</small>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <EmptyState v-else-if="!error" title="暂无可展示技师" description="门店更新公开排班后会显示在这里。" />

    <div v-if="selected" class="day-sheet-backdrop" @click.self="selected=undefined">
      <section class="day-sheet" role="dialog" aria-modal="true" :aria-label="`${selected.staff.name} ${selected.day.date} 排班详情`">
        <header><div><span class="page-context">{{ selected.day.date }}</span><h2>{{ selected.staff.name }}的当日安排</h2></div><button class="text-button" type="button" @click="selected=undefined">关闭</button></header>
        <p>{{ scheduleLabel(selected.day.scheduleStatus) }} · {{ periods(selected.day) }}</p>
        <div class="slot-list">
          <div v-for="slot in selected.day.slots" :key="slot.start" :class="`status-${slot.status.toLowerCase()}`">
            <span>{{ slot.start.slice(0,5) }}–{{ slot.end.slice(0,5) }}</span><strong>{{ statusLabel(slot.status) }}</strong>
          </div>
        </div>
        <router-link class="button button-primary" to="/booking">选择项目并预约</router-link>
      </section>
    </div>
  </div>
</template>

<style scoped>
.weekly-page{padding-bottom:96px}.page-context{color:var(--copper);font-size:13px;font-weight:700}.week-toolbar{display:flex;align-items:center;gap:10px;margin:24px 0 16px}.week-toolbar strong{margin-right:auto}.schedule-loading{display:grid;gap:10px}.schedule-loading .skeleton{min-height:76px}.weekly-table-wrap{overflow:auto;border:1px solid var(--line-strong);background:var(--surface);scrollbar-gutter:stable}.weekly-table{width:100%;min-width:1040px;border-collapse:separate;border-spacing:0}.weekly-table th,.weekly-table td{min-width:132px;padding:0;border-right:1px solid var(--line);border-bottom:1px solid var(--line);vertical-align:top}.weekly-table thead th{position:sticky;top:0;z-index:2;height:72px;padding:12px;background:var(--surface);text-align:left}.weekly-table thead th span,.weekly-table thead th strong,.weekly-table thead th small{display:block}.weekly-table thead th span,.weekly-table thead th small{color:var(--text-muted);font-size:11px}.weekly-table thead th.today{box-shadow:inset 0 3px var(--rose)}.weekly-table .staff-column{position:sticky;left:0;z-index:3;min-width:164px;width:164px;background:var(--surface)}.weekly-table tbody .staff-column{display:flex;align-items:center;gap:10px;min-height:98px;padding:14px;text-align:left}.weekly-table tbody .staff-column img,.avatar-fallback{width:38px;height:38px;flex:0 0 38px;border-radius:50%;object-fit:cover}.avatar-fallback{display:grid;place-items:center;background:var(--rose-soft);color:var(--rose-strong);font-weight:800}.weekly-table tbody .staff-column span span,.weekly-table tbody .staff-column strong,.weekly-table tbody .staff-column small{display:block}.weekly-table tbody .staff-column small{margin-top:3px;color:var(--text-muted);font-weight:400}.day-cell{width:100%;min-height:98px;display:grid;align-content:start;gap:5px;padding:14px;border:0;background:transparent;color:var(--text);text-align:left}.day-cell:hover,.day-cell:focus-visible{outline:2px solid var(--rose);outline-offset:-2px}.day-cell span,.day-cell small{color:var(--text-muted);font-size:11px}.status-available strong{color:#287a58}.status-partially_available strong{color:#a46b16}.status-full strong{color:var(--rose-strong)}.status-unavailable{background:var(--surface-soft)}.status-unavailable strong{color:var(--text-muted)}.day-sheet-backdrop{position:fixed;z-index:90;inset:0;display:grid;place-items:center;padding:20px;background:rgba(24,17,21,.38)}.day-sheet{width:min(620px,100%);max-height:min(760px,90vh);overflow:auto;padding:24px;border:1px solid var(--line-strong);background:var(--surface);box-shadow:0 20px 70px rgba(35,20,28,.2)}.day-sheet header{display:flex;justify-content:space-between;gap:16px}.day-sheet h2{margin:4px 0}.day-sheet>p{color:var(--text-muted)}.slot-list{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin:18px 0}.slot-list>div{display:flex;justify-content:space-between;gap:12px;padding:10px 12px;border:1px solid var(--line);background:var(--surface)}
@media(max-width:700px){.week-toolbar{display:grid;grid-template-columns:1fr 1fr}.week-toolbar strong{grid-column:1/-1;grid-row:1;margin:0}.week-toolbar .text-button{grid-column:1/-1;justify-self:start}.weekly-table{min-width:1090px}.weekly-table .staff-column{min-width:112px;width:112px}.weekly-table tbody .staff-column{display:grid;justify-items:start;min-height:112px;padding:10px}.weekly-table tbody .staff-column img,.avatar-fallback{width:32px;height:32px;flex-basis:32px}.weekly-table th,.weekly-table td{min-width:138px}.day-cell{min-height:112px;padding:12px}.day-sheet-backdrop{align-items:end;padding:0}.day-sheet{width:100%;max-height:84vh;padding:20px 16px calc(20px + env(safe-area-inset-bottom));border-radius:16px 16px 0 0}.slot-list{grid-template-columns:1fr}}
</style>
