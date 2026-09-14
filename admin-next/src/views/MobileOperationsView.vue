<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getOperationsWorkbench, type OperationsWorkbench } from '../services/api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const data = ref<OperationsWorkbench>({
  summary: {}, todayAppointments: [], staffSchedule: [], waitlist: [], shipments: [], afterSales: [],
})
const shopId = ref(0)
const pendingTotal = computed(() =>
  Number(data.value.summary.waitingCustomers || 0)
  + Number(data.value.summary.pendingFulfillment || 0)
  + Number(data.value.summary.pendingShipments || 0)
  + Number(data.value.summary.openAfterSales || 0),
)

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  shopId.value = auth.shops[0]?.id ?? auth.context?.homeShopId ?? 0
  await load()
})

async function load() {
  if (!shopId.value) return
  loading.value = true
  error.value = ''
  try { data.value = await getOperationsWorkbench(shopId.value) }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '工作台加载失败' }
  finally { loading.value = false }
}

function time(value: unknown) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(String(value)))
}

const statusText: Record<string, string> = {
  PENDING: '待处理', CONFIRMED: '已确认', ARRIVED: '已到店', IN_SERVICE: '服务中', COMPLETED: '已完成',
  WAITING: '候补中', NOTIFIED: '待确认', PAID: '待发货', SHIPPED: '已发货', OPEN: '待受理',
  TRIAGED: '已分派', PROCESSING: '处理中', WAITING_CUSTOMER: '等待顾客', REOPENED: '已重开',
}
</script>

<template>
  <section class="workbench-page">
    <header class="workbench-heading">
      <div>
        <span class="environment-label">移动高频管理</span>
        <h1>今日工作台</h1>
        <p>预约、日程、履约、候补、发货与售后待办集中处理。</p>
      </div>
      <div class="workbench-controls">
        <el-select v-model="shopId" aria-label="选择门店" @change="load">
          <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div v-if="loading" class="workbench-loading" role="status" aria-live="polite" aria-busy="true" aria-label="今日工作台加载中">
      <el-skeleton v-for="index in 4" :key="index" animated :rows="2" />
    </div>

    <template v-else>
      <section class="workbench-metrics" aria-label="今日运营摘要">
        <div><span>今日预约</span><strong>{{ data.summary.todayAppointments || 0 }}</strong><small>按门店自然日</small></div>
        <div><span>待处理总计</span><strong>{{ pendingTotal }}</strong><small>候补 / 履约 / 发货 / 售后</small></div>
        <div><span>活跃会员</span><strong>{{ data.summary.activeMembers || 0 }}</strong><small>当前门店</small></div>
        <div><span>近 30 日商城单</span><strong>{{ data.summary.mallOrders30d || 0 }}</strong><small>基础运营统计</small></div>
      </section>

      <nav class="workbench-shortcuts" aria-label="高频功能入口">
        <router-link to="/booking-operations"><span>预约与技师日程</span><b>{{ data.summary.todayAppointments || 0 }}</b></router-link>
        <router-link to="/fulfillment"><span>履约与售后沟通</span><b>{{ Number(data.summary.pendingFulfillment || 0) + Number(data.summary.openAfterSales || 0) }}</b></router-link>
      </nav>

      <div class="workbench-columns">
        <section class="workbench-card">
          <div class="card-heading"><div><h2>今日预约</h2><p>按开始时间排序</p></div><span>{{ data.todayAppointments.length }}</span></div>
          <article v-for="item in data.todayAppointments" :key="item.id" class="task-row">
            <time>{{ time(item.startAt) }}</time>
            <span><strong>{{ item.memberName }} · {{ item.serviceNames || '未标注项目' }}</strong><small>{{ item.staffName }} · {{ statusText[item.status] || item.status }}</small></span>
          </article>
          <el-empty v-if="!data.todayAppointments.length" description="今天暂无预约；可在预约与排班中代客创建" />
        </section>

        <section class="workbench-card">
          <div class="card-heading"><div><h2>技师日程</h2><p>今日有效排班</p></div><span>{{ data.staffSchedule.length }}</span></div>
          <article v-for="item in data.staffSchedule" :key="item.id" class="task-row">
            <time>{{ item.startTime || '全天' }}</time>
            <span><strong>{{ item.staffName }}</strong><small>{{ item.scheduleType }} · {{ item.remark || '无备注' }}</small></span>
          </article>
          <el-empty v-if="!data.staffSchedule.length" description="今天没有单独排班事实；请查看周期排班" />
        </section>

        <section class="workbench-card">
          <div class="card-heading"><div><h2>候补与履约提醒</h2><p>优先处理临近时限事项</p></div><span>{{ Number(data.summary.waitingCustomers || 0) + Number(data.summary.pendingFulfillment || 0) }}</span></div>
          <article v-for="item in data.waitlist" :key="item.id" class="task-row">
            <time>{{ statusText[item.status] || item.status }}</time>
            <span><strong>{{ item.memberName }} · {{ item.serviceName }}</strong><small>{{ item.dateFrom }} 至 {{ item.dateTo }} · {{ item.requestedStaffName || '任意技师' }}</small></span>
          </article>
          <el-empty v-if="!data.waitlist.length" description="当前没有待匹配候补" />
        </section>

        <section class="workbench-card">
          <div class="card-heading"><div><h2>订单与发货</h2><p>待发货及在途订单</p></div><span>{{ data.shipments.length }}</span></div>
          <article v-for="item in data.shipments" :key="item.id" class="task-row">
            <time>{{ statusText[item.status] || item.status }}</time>
            <span><strong>{{ item.orderNo }}</strong><small>{{ item.subOrderNo }} · {{ item.deliveryMode }}</small></span>
          </article>
          <el-empty v-if="!data.shipments.length" description="没有待发货或在途订单" />
        </section>

        <section class="workbench-card workbench-card--wide">
          <div class="card-heading"><div><h2>售后沟通</h2><p>未关闭工单</p></div><span>{{ data.afterSales.length }}</span></div>
          <article v-for="item in data.afterSales" :key="item.id" class="task-row">
            <time>{{ statusText[item.status] || item.status }}</time>
            <span><strong>{{ item.caseNo }} · {{ item.memberName || '未关联会员' }}</strong><small>{{ item.summary }}</small></span>
          </article>
          <el-empty v-if="!data.afterSales.length" description="当前没有待沟通售后" />
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.workbench-page { display: grid; gap: 20px; }
.workbench-heading,.workbench-controls,.card-heading,.task-row { display: flex; align-items: center; }
.workbench-heading { justify-content: space-between; gap: 22px; }
.workbench-heading h1 { margin: 4px 0 7px; font-size: 28px; }
.workbench-heading p,.card-heading p { margin: 0; color: var(--oc-text-muted); }
.workbench-controls { gap: 10px; }
.workbench-controls .el-select { width: 210px; }
.workbench-loading { display: grid; gap: 14px; }
.workbench-metrics { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); overflow: hidden; border: 1px solid var(--oc-border); border-radius: var(--oc-radius-md); background: var(--oc-surface-1); }
.workbench-metrics div { display: grid; gap: 3px; padding: 17px 20px; }
.workbench-metrics div + div { border-left: 1px solid var(--oc-border); }
.workbench-metrics span,.workbench-metrics small { color: var(--oc-text-soft); font-size: 11px; }
.workbench-metrics strong { font-size: 25px; }
.workbench-shortcuts { display: none; grid-template-columns: 1fr 1fr; gap: 10px; }
.workbench-shortcuts a { display: flex; align-items: center; justify-content: space-between; min-height: 58px; padding: 0 16px; border: 1px solid var(--oc-border); border-radius: var(--oc-radius-md); background: var(--oc-surface-1); color: inherit; text-decoration: none; }
.workbench-shortcuts b { display: grid; min-width: 28px; height: 28px; place-items: center; border-radius: 8px; background: var(--oc-surface-2); color: var(--oc-accent); }
.workbench-columns { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; }
.workbench-card { min-height: 260px; overflow: hidden; border: 1px solid var(--oc-border); border-radius: var(--oc-radius-md); background: var(--oc-surface-1); }
.workbench-card--wide { grid-column: 1 / -1; }
.card-heading { justify-content: space-between; gap: 16px; padding: 16px 18px; border-bottom: 1px solid var(--oc-border); }
.card-heading h2 { margin: 0 0 3px; font-size: 17px; }
.card-heading > span { display: grid; min-width: 28px; height: 28px; place-items: center; border-radius: 8px; background: var(--oc-surface-2); color: var(--oc-accent-strong); font-weight: 700; }
.task-row { align-items: flex-start; gap: 14px; padding: 14px 18px; border-bottom: 1px solid var(--oc-border); }
.task-row time { flex: 0 0 74px; color: var(--oc-copper); font-size: 12px; font-weight: 700; }
.task-row > span { display: grid; min-width: 0; gap: 3px; }
.task-row small { overflow: hidden; color: var(--oc-text-soft); text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 900px) {
  .workbench-shortcuts { display: grid; }
  .workbench-columns { grid-template-columns: 1fr; }
  .workbench-card--wide { grid-column: auto; }
}
@media (max-width: 640px) {
  .workbench-heading { align-items: stretch; flex-direction: column; }
  .workbench-controls,.workbench-controls .el-select { width: 100%; }
  .workbench-controls .el-select { flex: 1; }
  .workbench-metrics { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .workbench-metrics div + div { border-left: 0; }
  .workbench-metrics div:nth-child(even) { border-left: 1px solid var(--oc-border); }
  .workbench-metrics div:nth-child(n+3) { border-top: 1px solid var(--oc-border); }
  .workbench-shortcuts { grid-template-columns: 1fr; }
  .task-row time { flex-basis: 62px; }
}
</style>
