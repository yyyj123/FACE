<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getOperationsOverview, type OperationsOverview } from '../services/api'
import { useAuthStore } from '../stores/auth'

const EChartPanel = defineAsyncComponent(() => import('../components/EChartPanel.vue'))
const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const overview = ref<OperationsOverview | null>(null)
const days = ref(30)
const shopId = ref<number | undefined>(auth.context?.homeShopId ?? auth.shops[0]?.id)
const palette = ['#a84f64', '#95614d', '#d7a591', '#6f5964', '#35704b', '#c9b8bf']

const canView = computed(() => auth.context?.roles.includes('SUPER_ADMIN')
  || auth.context?.permissions?.includes('analytics:view'))
const selectedShopName = computed(() => shopId.value
  ? auth.shops.find((shop) => shop.id === shopId.value)?.name ?? '当前门店'
  : '全部授权门店')
const hasOperations = computed(() => Number(overview.value?.summary.appointmentCount ?? 0) > 0
  || Number(overview.value?.sales.summary.orderCount ?? 0) > 0
  || Number(overview.value?.summary.newMemberCount ?? 0) > 0)
const previous = computed(() => overview.value?.sales.comparison.previousSummary)

function localDate(value: Date) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
}

function queryRange() {
  const to = new Date()
  const from = new Date(to)
  from.setDate(from.getDate() - days.value + 1)
  return { fromDate: localDate(from), toDate: localDate(to) }
}

function money(value: unknown) {
  return `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function count(value: unknown) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

function changeText(current: unknown, old: unknown) {
  const now = Number(current ?? 0)
  const before = Number(old ?? 0)
  if (!before) return now ? '上期无成交' : '与上期持平'
  const rate = ((now - before) / Math.abs(before)) * 100
  return `${rate >= 0 ? '较上期 +' : '较上期 '}${rate.toFixed(1)}%`
}

async function load() {
  if (!canView.value) return
  loading.value = true
  error.value = ''
  try {
    overview.value = await getOperationsOverview({ shopId: shopId.value, ...queryRange() })
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '经营数据加载失败'
  } finally {
    loading.value = false
  }
}

const trendOption = computed<Record<string, unknown>>(() => {
  const appointmentRows = overview.value?.appointmentTrend ?? []
  const salesRows = overview.value?.sales.trend ?? []
  const dates = [...new Set([
    ...appointmentRows.map((row) => String(row.businessDate)),
    ...salesRows.map((row) => String(row.businessDate)),
  ])].sort()
  const appointments = new Map(appointmentRows.map((row) => [String(row.businessDate), row]))
  const sales = new Map(salesRows.map((row) => [String(row.businessDate), row]))
  return {
    color: [palette[0], palette[1], palette[4]], aria: { enabled: true },
    tooltip: { trigger: 'axis' },
    legend: { data: ['净实收', '预约数', '已完成'], bottom: 0, textStyle: { color: '#5f535a' } },
    grid: { left: 58, right: 52, top: 20, bottom: 48 },
    xAxis: { type: 'category', data: dates.map((date) => date.slice(5)), axisLine: { lineStyle: { color: '#d9d1d5' } }, axisLabel: { color: '#776b71' } },
    yAxis: [
      { type: 'value', axisLabel: { formatter: (value: number) => `¥${value}`, color: '#776b71' }, splitLine: { lineStyle: { color: '#eee9ec' } } },
      { type: 'value', minInterval: 1, axisLabel: { color: '#776b71' }, splitLine: { show: false } },
    ],
    series: [
      { name: '净实收', type: 'line', smooth: true, showSymbol: dates.length < 16, data: dates.map((date) => Number(sales.get(date)?.netCollectedAmount ?? 0)), areaStyle: { color: 'rgba(168,79,100,.10)' }, lineStyle: { width: 3 } },
      { name: '预约数', type: 'bar', yAxisIndex: 1, barMaxWidth: 22, data: dates.map((date) => Number(appointments.get(date)?.appointmentCount ?? 0)), itemStyle: { borderRadius: [4, 4, 0, 0] } },
      { name: '已完成', type: 'line', yAxisIndex: 1, smooth: true, data: dates.map((date) => Number(appointments.get(date)?.completedCount ?? 0)), lineStyle: { width: 2 } },
    ],
  }
})

const statusLabels: Record<string, string> = { PENDING: '待确认', CONFIRMED: '已确认', CHECKED_IN: '已到店', IN_SERVICE: '服务中', COMPLETED: '已完成', CANCELLED: '已取消', NO_SHOW: '未到店' }

const statusOption = computed<Record<string, unknown>>(() => ({
  color: palette, aria: { enabled: true }, tooltip: { trigger: 'item', formatter: '{b}<br/>预约数：{c}<br/>占比：{d}%' },
  legend: { bottom: 0, textStyle: { color: '#5f535a' } },
  series: [{ type: 'pie', radius: ['48%', '72%'], center: ['50%', '44%'], itemStyle: { borderColor: '#fff', borderWidth: 3 }, label: { formatter: '{b}\n{d}%', color: '#5f535a' }, data: (overview.value?.appointmentStatus ?? []).map((row) => ({ name: statusLabels[row.status] || row.status, value: Number(row.value) })) }],
}))

const rankingOption = computed<Record<string, unknown>>(() => {
  const rows = [...(overview.value?.serviceRanking ?? [])].slice(0, 8).reverse()
  return {
    color: [palette[0]], aria: { enabled: true }, tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 110, right: 28, top: 12, bottom: 26 },
    xAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#776b71' }, splitLine: { lineStyle: { color: '#eee9ec' } } },
    yAxis: { type: 'category', data: rows.map((row) => row.serviceName), axisLabel: { width: 92, overflow: 'truncate', color: '#5f535a' }, axisLine: { show: false }, axisTick: { show: false } },
    series: [{ name: '预约次数', type: 'bar', barMaxWidth: 20, data: rows.map((row) => Number(row.appointmentCount)), itemStyle: { borderRadius: [0, 5, 5, 0] } }],
  }
})

const memberOption = computed<Record<string, unknown>>(() => {
  const rows = overview.value?.memberTrend ?? []
  return {
    color: [palette[4]], aria: { enabled: true }, tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 18, bottom: 36 },
    xAxis: { type: 'category', data: rows.map((row) => String(row.businessDate).slice(5)), axisLine: { lineStyle: { color: '#d9d1d5' } }, axisLabel: { color: '#776b71' } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#776b71' }, splitLine: { lineStyle: { color: '#eee9ec' } } },
    series: [{ name: '新增会员', type: 'line', smooth: true, symbolSize: 8, data: rows.map((row) => Number(row.newMemberCount)), areaStyle: { color: 'rgba(53,112,75,.12)' }, lineStyle: { width: 3 } }],
  }
})

const staffOption = computed<Record<string, unknown>>(() => {
  const rows = overview.value?.staffWorkload ?? []
  return {
    color: [palette[1], palette[4]], aria: { enabled: true }, tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['预约数', '已完成'], bottom: 0, textStyle: { color: '#5f535a' } },
    grid: { left: 46, right: 20, top: 18, bottom: 54 },
    xAxis: { type: 'category', data: rows.map((row) => row.staffName), axisLabel: { color: '#5f535a', width: 68, overflow: 'truncate' }, axisLine: { lineStyle: { color: '#d9d1d5' } } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#776b71' }, splitLine: { lineStyle: { color: '#eee9ec' } } },
    series: [
      { name: '预约数', type: 'bar', barMaxWidth: 28, data: rows.map((row) => Number(row.appointmentCount)), itemStyle: { borderRadius: [5, 5, 0, 0] } },
      { name: '已完成', type: 'bar', barMaxWidth: 28, data: rows.map((row) => Number(row.completedCount)), itemStyle: { borderRadius: [5, 5, 0, 0] } },
    ],
  }
})

watch([days, shopId], load)
onMounted(load)
</script>

<template>
  <section class="overview-heading">
    <div>
      <h1>店铺运营总览</h1>
      <p>把成交、顾客和项目表现放在同一张看板里，及时发现变化。</p>
    </div>
    <div class="overview-filters">
      <el-select v-model="shopId" clearable placeholder="全部授权门店" aria-label="选择门店">
        <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
      </el-select>
      <el-segmented v-model="days" :options="[{ label: '近7天', value: 7 }, { label: '近30天', value: 30 }, { label: '近90天', value: 90 }]" aria-label="选择统计周期" />
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>
  </section>

  <el-alert v-if="!canView" title="当前账号没有经营数据查看权限，请联系超级管理员开通。" type="warning" :closable="false" show-icon />
  <el-alert v-else-if="error" class="overview-alert" :title="error" type="error" :closable="false" show-icon>
    <template #default><el-button size="small" @click="load">重新加载</el-button></template>
  </el-alert>

  <template v-if="canView">
    <section v-loading="loading && !overview" class="overview-metrics" aria-label="经营核心指标">
      <div><span>净实收</span><strong>{{ money(overview?.sales.summary.netCollectedAmount) }}</strong><small>{{ changeText(overview?.sales.summary.netCollectedAmount, previous?.netCollectedAmount) }}</small></div>
      <div><span>预约总数</span><strong>{{ count(overview?.summary.appointmentCount) }}</strong><small>所选周期内全部预约</small></div>
      <div><span>在册会员</span><strong>{{ count(overview?.summary.activeMemberCount) }}</strong><small>本期新增 {{ count(overview?.summary.newMemberCount) }} 位</small></div>
      <div><span>服务完成率</span><strong>{{ Number(overview?.summary.completionRate ?? 0).toFixed(1) }}%</strong><small>已完成 {{ count(overview?.summary.completedAppointmentCount) }} 单</small></div>
    </section>

    <div v-if="overview && !hasOperations" class="overview-empty">
      <h2>当前周期暂无运营数据</h2>
      <p>{{ selectedShopName }}在所选周期内还没有预约、会员或成交记录，可切换门店或查看更长周期。</p>
    </div>

    <section v-else-if="overview" class="dashboard-grid">
      <article class="chart-section chart-section--wide">
        <header><div><h2>预约与实收趋势</h2><p>对照每日预约、已完成服务和净实收变化。</p></div><span>{{ overview.fromDate }} 至 {{ overview.toDate }}</span></header>
        <EChartPanel :option="trendOption" label="每日预约、已完成服务和净实收趋势图" />
      </article>

      <article class="chart-section">
        <header><div><h2>预约状态分布</h2><p>查看预约从待确认到完成或取消的构成。</p></div></header>
        <EChartPanel :option="statusOption" label="预约状态分布环形图" />
      </article>

      <article class="chart-section">
        <header><div><h2>热门护理项目</h2><p>按预约次数查看顾客最常选择的项目。</p></div></header>
        <EChartPanel :option="rankingOption" label="护理项目预约次数横向条形图" />
      </article>

      <article class="chart-section">
        <header><div><h2>会员新增趋势</h2><p>查看本期每天新增的会员人数。</p></div></header>
        <EChartPanel :option="memberOption" label="每日新增会员趋势图" />
      </article>

      <article class="chart-section">
        <header><div><h2>技师服务负荷</h2><p>对比每位技师的预约数和已完成服务数。</p></div></header>
        <EChartPanel :option="staffOption" label="技师预约数与已完成服务对比柱状图" />
      </article>
    </section>
  </template>
</template>

<style scoped>
.overview-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:24px;margin-bottom:22px}.overview-heading h1{margin:0 0 8px;font-size:32px;line-height:1.2;letter-spacing:-.025em}.overview-heading p{max-width:58ch;margin:0;color:var(--oc-text-muted)}.overview-filters{display:flex;align-items:center;justify-content:flex-end;gap:10px}.overview-filters .el-select{width:210px}.overview-alert{margin-bottom:18px}.overview-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));min-height:116px;margin-bottom:18px;overflow:hidden;border:1px solid var(--oc-border);border-radius:var(--oc-radius-md);background:var(--oc-surface-1)}.overview-metrics>div{display:flex;min-width:0;flex-direction:column;padding:20px 22px}.overview-metrics>div+div{border-left:1px solid var(--oc-border)}.overview-metrics span,.overview-metrics small{color:var(--oc-text-soft);font-size:12px}.overview-metrics strong{margin:6px 0 3px;font-size:25px;line-height:1.2;font-variant-numeric:tabular-nums}.dashboard-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.chart-section{min-width:0;overflow:hidden;border:1px solid var(--oc-border);border-radius:var(--oc-radius-md);background:var(--oc-surface-1)}.chart-section--wide{grid-column:1/-1}.chart-section header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding:20px 22px 8px}.chart-section h2{margin:0 0 4px;font-size:17px}.chart-section p,.chart-section header>span{margin:0;color:var(--oc-text-soft);font-size:12px}.chart-section header>span{white-space:nowrap}.overview-empty{display:grid;min-height:390px;place-items:center;align-content:center;padding:40px;border:1px dashed var(--oc-border-strong);border-radius:var(--oc-radius-md);text-align:center}.overview-empty h2{margin:0 0 8px}.overview-empty p{max-width:56ch;margin:0;color:var(--oc-text-muted)}
@media(max-width:1120px){.overview-heading{align-items:stretch;flex-direction:column}.overview-filters{justify-content:flex-start}.dashboard-grid{grid-template-columns:1fr}.chart-section--wide{grid-column:auto}}
@media(max-width:760px){.overview-heading h1{font-size:27px}.overview-filters{align-items:stretch;flex-direction:column}.overview-filters .el-select,.overview-filters .el-segmented,.overview-filters .el-button{width:100%}.overview-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.overview-metrics>div:nth-child(3){border-top:1px solid var(--oc-border);border-left:0}.overview-metrics>div:nth-child(4){border-top:1px solid var(--oc-border)}.chart-section header{flex-direction:column;padding:18px 18px 6px}}
@media(max-width:480px){.overview-metrics{grid-template-columns:1fr}.overview-metrics>div+div,.overview-metrics>div:nth-child(4){border-top:1px solid var(--oc-border);border-left:0}}
</style>
