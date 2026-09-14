<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createAnalyticsReport,
  downloadAnalyticsReport,
  getAnalyticsLines,
  getAnalyticsOverview,
  getAnalyticsReports,
  type AnalyticsItemType,
  type AnalyticsLinePage,
  type AnalyticsOverview,
  type AnalyticsReportPage,
} from '../services/api'
import { useAuthStore } from '../stores/auth'

type PageTab = 'overview' | 'periods' | 'shops' | 'brands' | 'lines' | 'reports'

const auth = useAuthStore()
const today = localDateString(new Date())
const monthAgo = localDateString(new Date(Date.now() - 29 * 24 * 60 * 60 * 1000))
const query = reactive({
  shopId: undefined as number | undefined,
  dateRange: [monthAgo, today] as [string, string],
  itemType: 'ALL' as AnalyticsItemType,
})
const activeTab = ref<PageTab>('overview')
const overview = ref<AnalyticsOverview>()
const lines = ref<AnalyticsLinePage>()
const reports = ref<AnalyticsReportPage>()
const overviewLoading = ref(true)
const linesLoading = ref(false)
const reportsLoading = ref(false)
const reportCreating = ref(false)
const downloadingReportId = ref<number>()
const reportIdempotencyKey = ref(crypto.randomUUID())
const error = ref('')

const canExport = computed(() =>
  auth.context?.permissions?.includes('analytics:export') ?? false,
)

const itemTypes: Array<{ value: AnalyticsItemType; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'SERVICE', label: '服务项目' },
  { value: 'PRODUCT', label: '零售商品' },
]

const categoryMaximum = computed(() =>
  Math.max(
    1,
    ...(overview.value?.categoryComposition.map(
      (item) => Number(item.lineSalesAmountBeforeRefund),
    ) ?? [1]),
  ),
)
const brandMaximum = computed(() =>
  Math.max(
    1,
    ...(overview.value?.brandComposition.map(
      (item) => Number(item.lineSalesAmountBeforeRefund),
    ) ?? [1]),
  ),
)
const brandTotal = computed(() =>
  overview.value?.brandComposition.reduce(
    (total, item) => total + Number(item.lineSalesAmountBeforeRefund),
    0,
  ) ?? 0,
)
const shopMaximum = computed(() =>
  Math.max(
    1,
    ...(overview.value?.shopBreakdown.map(
      (item) => Number(item.netCollectedAmount),
    ) ?? [1]),
  ),
)
const comparisonRows = computed(() => {
  if (!overview.value) return []
  const current = overview.value.summary
  const previous = overview.value.comparison.previousSummary
  return [
    {
      label: '有效订单',
      current: Number(current.orderCount),
      previous: Number(previous.orderCount),
      format: 'number',
    },
    {
      label: '消费客户',
      current: Number(current.consumingCustomerCount),
      previous: Number(previous.consumingCustomerCount),
      format: 'number',
    },
    {
      label: '实收金额',
      current: Number(current.collectedAmount),
      previous: Number(previous.collectedAmount),
      format: 'money',
    },
    {
      label: '退款金额',
      current: Number(current.refundedAmount),
      previous: Number(previous.refundedAmount),
      format: 'money',
    },
    {
      label: '净收款',
      current: Number(current.netCollectedAmount),
      previous: Number(previous.netCollectedAmount),
      format: 'money',
    },
  ]
})
const definitionDescription = computed(() => {
  if (!overview.value) return ''
  if (activeTab.value === 'reports') {
    return '报表按生成时的指标版本与授权门店范围固化，保存 30 天；下载会再次校验当前导出权限并记录审计日志。'
  }
  if (activeTab.value === 'periods') {
    return overview.value.dataQuality.periodComparisonDefinition
  }
  if (activeTab.value === 'shops') {
    return overview.value.dataQuality.shopComparisonDefinition
  }
  return activeTab.value === 'brands'
    ? `${overview.value.dataQuality.brandSalesDefinition} ${overview.value.dataQuality.dimensionSnapshot}`
    : `${overview.value.dataQuality.dimensionSnapshot} 订单级实收、退款与净收款始终按所选门店和日期的全部有效订单统计，不受品项范围筛选影响。`
})

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  query.shopId = auth.shops.length === 1 ? auth.shops[0]?.id : undefined
  await loadOverview()
})

async function loadOverview() {
  overviewLoading.value = true
  error.value = ''
  try {
    overview.value = await getAnalyticsOverview({
      shopId: query.shopId,
      fromDate: query.dateRange[0],
      toDate: query.dateRange[1],
      itemType: query.itemType,
    })
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '经营分析加载失败'
  } finally {
    overviewLoading.value = false
  }
}

async function loadLines(resetPage = false) {
  linesLoading.value = true
  try {
    lines.value = await getAnalyticsLines({
      shopId: query.shopId,
      fromDate: query.dateRange[0],
      toDate: query.dateRange[1],
      itemType: query.itemType,
      page: resetPage ? 1 : lines.value?.page ?? 1,
      pageSize: lines.value?.pageSize ?? 30,
    })
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '销售明细加载失败')
  } finally {
    linesLoading.value = false
  }
}

async function loadReports(resetPage = false) {
  if (!canExport.value) return
  reportsLoading.value = true
  try {
    reports.value = await getAnalyticsReports({
      page: resetPage ? 1 : reports.value?.page ?? 1,
      pageSize: reports.value?.pageSize ?? 30,
    })
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '经营报表列表加载失败')
  } finally {
    reportsLoading.value = false
  }
}

async function applyFilters() {
  if (!query.dateRange?.[0] || !query.dateRange?.[1]) {
    ElMessage.warning('请选择完整的统计日期')
    return
  }
  await loadOverview()
  if (activeTab.value === 'lines') await loadLines(true)
}

async function switchTab(tab: PageTab) {
  activeTab.value = tab
  if (tab === 'lines' && !lines.value) await loadLines(true)
  if (tab === 'reports' && !reports.value) await loadReports(true)
}

async function changeLinePage(page: number) {
  if (lines.value) lines.value.page = page
  await loadLines()
}

async function changeReportPage(page: number) {
  if (reports.value) reports.value.page = page
  await loadReports()
}

async function createReport() {
  if (!canExport.value) {
    ElMessage.warning('当前账号没有经营报表导出权限')
    return
  }
  if (!query.dateRange?.[0] || !query.dateRange?.[1]) {
    ElMessage.warning('请选择完整的统计日期')
    return
  }
  reportCreating.value = true
  try {
    await createAnalyticsReport({
      shopId: query.shopId,
      fromDate: query.dateRange[0],
      toDate: query.dateRange[1],
      itemType: query.itemType,
      format: 'CSV',
    }, reportIdempotencyKey.value)
    reportIdempotencyKey.value = crypto.randomUUID()
    ElMessage.success('经营报表快照已生成')
    await loadReports(true)
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '经营报表生成失败')
  } finally {
    reportCreating.value = false
  }
}

async function downloadReport(reportId: number) {
  downloadingReportId.value = reportId
  try {
    const { blob } = await downloadAnalyticsReport(reportId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `sales-overview-${reportId}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('经营报表已下载并记录审计')
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '经营报表下载失败')
  } finally {
    downloadingReportId.value = undefined
  }
}

function shopName(shopId: number) {
  return auth.shops.find((shop) => shop.id === shopId)?.name ?? `门店 ${shopId}`
}

function itemTypeLabel(value: string) {
  return value === 'SERVICE' ? '服务项目' : '零售商品'
}

function reportItemTypeLabel(value: AnalyticsItemType) {
  return itemTypes.find((item) => item.value === value)?.label ?? value
}

function reportScopeLabel(shopIds: number[]) {
  if (shopIds.length === 1) return shopName(shopIds[0]!)
  return `${shopIds.length} 家授权门店`
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`
  return `${(value / 1024).toFixed(1)} KB`
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function snapshotQualityLabel(value: string) {
  return {
    TRANSACTION_TIME: '成交时快照',
    CURRENT_MASTER_BACKFILL: '历史主数据回填',
    MISSING: '维度缺失',
  }[value] ?? value
}

function formatMoney(value?: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
  }).format(Number(value ?? 0))
}

function formatNumber(value?: number) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 3 }).format(Number(value ?? 0))
}

function formatShare(value?: number) {
  if (!brandTotal.value) return '0.0%'
  return `${((Number(value ?? 0) / brandTotal.value) * 100).toFixed(1)}%`
}

function formatAverage(total?: number, count?: number) {
  if (!count) return formatMoney(0)
  return formatMoney(Number(total ?? 0) / Number(count))
}

function formatComparisonValue(value: number, format: string) {
  return format === 'money' ? formatMoney(value) : formatNumber(value)
}

function formatComparisonDelta(current: number, previous: number, format: string) {
  const delta = current - previous
  const prefix = delta > 0 ? '+' : ''
  return `${prefix}${formatComparisonValue(delta, format)}`
}

function formatComparisonRate(current: number, previous: number) {
  if (previous === 0) return current === 0 ? '—' : '无基期'
  const rate = ((current - previous) / previous) * 100
  return `${rate > 0 ? '+' : ''}${rate.toFixed(1)}%`
}

function comparisonDirection(current: number, previous: number) {
  if (current > previous) return 'comparison-change--up'
  if (current < previous) return 'comparison-change--down'
  return ''
}

function localDateString(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="environment-label">M6-05 · 受审计报表</p>
      <h1>经营分析</h1>
      <p>从已支付订单观察门店经营结构。品项金额为退款前成交额，退款只在订单层核算。</p>
    </div>
    <span class="status-badge status-badge--success">口径版本 M6-04-v1</span>
  </section>

  <section class="analytics-filter" aria-label="经营分析筛选条件">
    <label>
      <span>门店范围</span>
      <el-select v-model="query.shopId" clearable placeholder="全部授权门店">
        <el-option
          v-for="shop in auth.shops"
          :key="shop.id"
          :label="shop.name"
          :value="shop.id"
        />
      </el-select>
    </label>
    <label>
      <span>统计日期</span>
      <el-date-picker
        v-model="query.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :clearable="false"
      />
    </label>
    <fieldset>
      <legend>排行与构成范围</legend>
      <div class="analytics-type-filter">
        <button
          v-for="item in itemTypes"
          :key="item.value"
          type="button"
          :class="{ active: query.itemType === item.value }"
          @click="query.itemType = item.value"
        >
          {{ item.label }}
        </button>
      </div>
    </fieldset>
    <el-button type="primary" :loading="overviewLoading" @click="applyFilters">
      更新分析
    </el-button>
  </section>

  <div class="analytics-tabs" role="tablist" aria-label="经营分析视图">
    <button
      type="button"
      role="tab"
      :aria-selected="activeTab === 'overview'"
      :class="{ active: activeTab === 'overview' }"
      @click="switchTab('overview')"
    >
      经营总览
    </button>
    <button
      type="button"
      role="tab"
      :aria-selected="activeTab === 'periods'"
      :class="{ active: activeTab === 'periods' }"
      @click="switchTab('periods')"
    >
      周期对比
    </button>
    <button
      type="button"
      role="tab"
      :aria-selected="activeTab === 'shops'"
      :class="{ active: activeTab === 'shops' }"
      @click="switchTab('shops')"
    >
      门店对比
    </button>
    <button
      type="button"
      role="tab"
      :aria-selected="activeTab === 'brands'"
      :class="{ active: activeTab === 'brands' }"
      @click="switchTab('brands')"
    >
      品牌分析
    </button>
    <button
      type="button"
      role="tab"
      :aria-selected="activeTab === 'lines'"
      :class="{ active: activeTab === 'lines' }"
      @click="switchTab('lines')"
    >
      销售明细
    </button>
    <button
      v-if="canExport"
      type="button"
      role="tab"
      :aria-selected="activeTab === 'reports'"
      :class="{ active: activeTab === 'reports' }"
      @click="switchTab('reports')"
    >
      报表快照
    </button>
  </div>

  <el-alert
    v-if="overview"
    class="analytics-definition"
    type="info"
    :closable="false"
    show-icon
    :title="overview.dataQuality.lineSalesDefinition"
    :description="definitionDescription"
  />

  <el-alert
    v-if="error"
    class="analytics-error"
    type="error"
    :closable="false"
    show-icon
    title="经营分析暂时无法加载"
    :description="error"
  />

  <template v-if="activeTab === 'overview'">
    <el-skeleton v-if="overviewLoading" :rows="8" animated />
    <template v-else-if="overview">
      <section class="metric-strip analytics-metrics" aria-label="经营指标">
        <div>
          <span>有效订单</span>
          <strong>{{ formatNumber(overview.summary.orderCount) }}</strong>
          <small>已支付及退款订单</small>
        </div>
        <div>
          <span>消费客户</span>
          <strong>{{ formatNumber(overview.summary.consumingCustomerCount) }}</strong>
          <small>按会员去重</small>
        </div>
        <div>
          <span>实收金额</span>
          <strong>{{ formatMoney(overview.summary.collectedAmount) }}</strong>
          <small>订单级收款</small>
        </div>
        <div>
          <span>退款金额</span>
          <strong>{{ formatMoney(overview.summary.refundedAmount) }}</strong>
          <small>订单级可靠口径</small>
        </div>
        <div>
          <span>净收款</span>
          <strong>{{ formatMoney(overview.summary.netCollectedAmount) }}</strong>
          <small>实收减退款</small>
        </div>
      </section>

      <section class="analytics-grid">
        <div class="analytics-panel">
          <div class="section-heading">
            <div>
              <h2>项目与商品排行</h2>
              <p>按退款前订单行成交额排序，避免错误分摊订单退款。</p>
            </div>
          </div>
          <el-table :data="overview.ranking" empty-text="当前范围内暂无成交品项">
            <el-table-column label="排名" width="72">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column prop="itemName" label="品项" min-width="180" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ itemTypeLabel(row.itemType) }}</template>
            </el-table-column>
            <el-table-column prop="categoryName" label="品类" min-width="130" />
            <el-table-column prop="brandName" label="品牌" min-width="120">
              <template #default="{ row }">{{ row.brandName || '—' }}</template>
            </el-table-column>
            <el-table-column label="客户数" width="90" align="right">
              <template #default="{ row }">{{ formatNumber(row.consumingCustomerCount) }}</template>
            </el-table-column>
            <el-table-column label="数量" width="90" align="right">
              <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
            </el-table-column>
            <el-table-column label="成交额" width="130" align="right">
              <template #default="{ row }">
                {{ formatMoney(row.lineSalesAmountBeforeRefund) }}
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="analytics-panel analytics-panel--composition">
          <div class="section-heading">
            <div>
              <h2>品类销售构成</h2>
              <p>仅代表本店销售结构，不等同外部市场占有率。</p>
            </div>
          </div>
          <div v-if="overview.categoryComposition.length" class="composition-list">
            <div
              v-for="item in overview.categoryComposition"
              :key="`${item.itemType}-${item.categoryName}`"
              class="composition-row"
            >
              <div>
                <strong>{{ item.categoryName }}</strong>
                <span>{{ itemTypeLabel(item.itemType) }}</span>
              </div>
              <div
                class="composition-track"
                role="img"
                :aria-label="`${item.categoryName}成交额${formatMoney(item.lineSalesAmountBeforeRefund)}`"
              >
                <span
                  :style="{
                    width: `${Math.max(
                      3,
                      (Number(item.lineSalesAmountBeforeRefund) / categoryMaximum) * 100,
                    )}%`,
                  }"
                />
              </div>
              <strong>{{ formatMoney(item.lineSalesAmountBeforeRefund) }}</strong>
            </div>
          </div>
          <p v-else class="analytics-empty">当前范围内暂无品类成交数据。</p>
        </div>
      </section>

      <section class="analytics-panel">
        <div class="section-heading">
          <div>
            <h2>每日收款趋势</h2>
            <p>按订单业务日期汇总，退款保持订单级冲正。</p>
          </div>
        </div>
        <el-table :data="overview.trend" empty-text="当前范围内暂无趋势数据">
          <el-table-column prop="businessDate" label="业务日期" min-width="140" />
          <el-table-column label="订单数" min-width="100" align="right">
            <template #default="{ row }">{{ formatNumber(row.orderCount) }}</template>
          </el-table-column>
          <el-table-column label="实收金额" min-width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.collectedAmount) }}</template>
          </el-table-column>
          <el-table-column label="退款金额" min-width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.refundedAmount) }}</template>
          </el-table-column>
          <el-table-column label="净收款" min-width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.netCollectedAmount) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </template>
  </template>

  <section v-else-if="activeTab === 'periods'" class="analytics-panel">
    <div class="section-heading">
      <div>
        <h2>同期经营对比</h2>
        <p>上一周期与当前周期天数相同并紧邻，使用相同的授权门店和订单级口径。</p>
      </div>
    </div>
    <el-skeleton v-if="overviewLoading" :rows="7" animated />
    <template v-else-if="overview">
      <div class="comparison-periods" aria-label="对比周期">
        <div>
          <span>当前周期</span>
          <strong>{{ overview.fromDate }} 至 {{ overview.toDate }}</strong>
        </div>
        <div>
          <span>上一周期</span>
          <strong>
            {{ overview.comparison.previousFromDate }} 至
            {{ overview.comparison.previousToDate }}
          </strong>
        </div>
      </div>
      <el-table :data="comparisonRows" empty-text="暂无周期对比数据">
        <el-table-column prop="label" label="指标" min-width="150" />
        <el-table-column label="当前周期" min-width="150" align="right">
          <template #default="{ row }">
            {{ formatComparisonValue(row.current, row.format) }}
          </template>
        </el-table-column>
        <el-table-column label="上一周期" min-width="150" align="right">
          <template #default="{ row }">
            {{ formatComparisonValue(row.previous, row.format) }}
          </template>
        </el-table-column>
        <el-table-column label="变化量" min-width="150" align="right">
          <template #default="{ row }">
            {{ formatComparisonDelta(row.current, row.previous, row.format) }}
          </template>
        </el-table-column>
        <el-table-column label="变化率" min-width="120" align="right">
          <template #default="{ row }">
            <span
              :class="[
                'comparison-change',
                comparisonDirection(row.current, row.previous),
              ]"
            >
              {{ formatComparisonRate(row.current, row.previous) }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <p class="analytics-table-note">
        “无基期”表示上一周期为零，系统不会生成误导性的无限增长百分比。
      </p>
    </template>
  </section>

  <section v-else-if="activeTab === 'shops'" class="analytics-panel">
    <div class="section-heading">
      <div>
        <h2>授权门店经营对比</h2>
        <p>按订单业务日期比较实收、退款和净收款；门店名称来自当前账号的授权上下文。</p>
      </div>
      <span v-if="overview" class="analytics-record-count">
        {{ overview.shopBreakdown.length }} 家门店
      </span>
    </div>
    <el-skeleton v-if="overviewLoading" :rows="7" animated />
    <template v-else-if="overview">
      <p v-if="query.shopId" class="analytics-scope-note">
        当前已指定单店。清除“门店范围”筛选后，可比较全部授权门店。
      </p>
      <div v-if="overview.shopBreakdown.length" class="analytics-shop-layout">
        <div class="composition-list analytics-shop-bars">
          <div
            v-for="item in overview.shopBreakdown"
            :key="item.shopId"
            class="composition-row"
          >
            <div>
              <strong>{{ shopName(item.shopId) }}</strong>
              <span>{{ formatNumber(item.orderCount) }} 笔有效订单</span>
            </div>
            <div
              class="composition-track"
              role="img"
              :aria-label="`${shopName(item.shopId)}净收款${formatMoney(item.netCollectedAmount)}`"
            >
              <span
                :style="{
                  width: `${Math.max(
                    3,
                    (Number(item.netCollectedAmount) / shopMaximum) * 100,
                  )}%`,
                }"
              />
            </div>
            <strong>{{ formatMoney(item.netCollectedAmount) }}</strong>
          </div>
        </div>
        <el-table :data="overview.shopBreakdown" empty-text="暂无门店经营数据">
          <el-table-column label="门店" min-width="160">
            <template #default="{ row }">{{ shopName(row.shopId) }}</template>
          </el-table-column>
          <el-table-column label="订单" width="90" align="right">
            <template #default="{ row }">{{ formatNumber(row.orderCount) }}</template>
          </el-table-column>
          <el-table-column label="客户" width="90" align="right">
            <template #default="{ row }">
              {{ formatNumber(row.consumingCustomerCount) }}
            </template>
          </el-table-column>
          <el-table-column label="实收" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.collectedAmount) }}</template>
          </el-table-column>
          <el-table-column label="退款" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.refundedAmount) }}</template>
          </el-table-column>
          <el-table-column label="净收款" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.netCollectedAmount) }}</template>
          </el-table-column>
          <el-table-column label="单均净收" width="130" align="right">
            <template #default="{ row }">
              {{ formatAverage(row.netCollectedAmount, row.orderCount) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
      <p v-else class="analytics-empty">当前授权门店和日期范围内暂无有效订单。</p>
    </template>
  </section>

  <section v-else-if="activeTab === 'brands'" class="analytics-panel">
    <div class="section-heading">
      <div>
        <h2>品牌销售构成</h2>
        <p>仅统计零售商品的退款前订单行成交额，用于观察本店品牌结构，不代表外部市场占有率。</p>
      </div>
      <span v-if="overview" class="analytics-record-count">
        {{ overview.brandComposition.length }} 个品牌
      </span>
    </div>
    <el-skeleton v-if="overviewLoading" :rows="7" animated />
    <template v-else-if="overview">
      <div v-if="overview.brandComposition.length" class="analytics-brand-layout">
        <div class="composition-list analytics-brand-bars">
          <div
            v-for="item in overview.brandComposition.slice(0, 8)"
            :key="item.brandName"
            class="composition-row"
          >
            <div>
              <strong>{{ item.brandName }}</strong>
              <span>{{ formatShare(item.lineSalesAmountBeforeRefund) }}</span>
            </div>
            <div
              class="composition-track"
              role="img"
              :aria-label="`${item.brandName}销售构成${formatShare(item.lineSalesAmountBeforeRefund)}`"
            >
              <span
                :style="{
                  width: `${Math.max(
                    3,
                    (Number(item.lineSalesAmountBeforeRefund) / brandMaximum) * 100,
                  )}%`,
                }"
              />
            </div>
            <strong>{{ formatMoney(item.lineSalesAmountBeforeRefund) }}</strong>
          </div>
        </div>
        <el-table :data="overview.brandComposition" empty-text="暂无品牌销售数据">
          <el-table-column label="排名" width="72">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column prop="brandName" label="品牌" min-width="150" />
          <el-table-column label="销售构成" width="110" align="right">
            <template #default="{ row }">
              {{ formatShare(row.lineSalesAmountBeforeRefund) }}
            </template>
          </el-table-column>
          <el-table-column label="订单数" width="100" align="right">
            <template #default="{ row }">{{ formatNumber(row.orderCount) }}</template>
          </el-table-column>
          <el-table-column label="客户数" width="100" align="right">
            <template #default="{ row }">
              {{ formatNumber(row.consumingCustomerCount) }}
            </template>
          </el-table-column>
          <el-table-column label="数量" width="90" align="right">
            <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="成交额" width="130" align="right">
            <template #default="{ row }">
              {{ formatMoney(row.lineSalesAmountBeforeRefund) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
      <p v-else class="analytics-empty">
        当前门店和日期范围内没有带品牌维度的零售商品成交数据。
      </p>
    </template>
  </section>

  <section v-else-if="activeTab === 'reports'" class="analytics-panel">
    <div class="section-heading analytics-report-heading">
      <div>
        <h2>经营报表快照</h2>
        <p>按当前筛选条件生成不可变 CSV；内容不含会员手机号、支付凭据等敏感信息。</p>
      </div>
      <el-button type="primary" :loading="reportCreating" @click="createReport">
        生成当前报表
      </el-button>
    </div>
    <div class="analytics-scope-note">
      快照保留生成时口径与范围，30 天后不可下载；重复提交沿用同一幂等键，不会生成重复记录。
    </div>
    <el-table
      v-loading="reportsLoading"
      :data="reports?.records ?? []"
      empty-text="尚未生成经营报表"
    >
      <el-table-column prop="id" label="报表编号" width="110" />
      <el-table-column label="门店范围" min-width="150">
        <template #default="{ row }">{{ reportScopeLabel(row.shopIds) }}</template>
      </el-table-column>
      <el-table-column label="统计周期" min-width="210">
        <template #default="{ row }">{{ row.fromDate }} 至 {{ row.toDate }}</template>
      </el-table-column>
      <el-table-column label="品项范围" width="110">
        <template #default="{ row }">{{ reportItemTypeLabel(row.itemType) }}</template>
      </el-table-column>
      <el-table-column prop="metricVersion" label="指标版本" width="120" />
      <el-table-column label="文件" width="120" align="right">
        <template #default="{ row }">{{ formatBytes(row.contentBytes) }}</template>
      </el-table-column>
      <el-table-column label="生成时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.readyAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span
            :class="[
              'status-badge',
              row.status === 'READY' ? 'status-badge--success' : 'report-status--expired',
            ]"
          >
            {{ row.status === 'READY' ? '可下载' : '已过期' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="row.status !== 'READY'"
            :loading="downloadingReportId === row.id"
            @click="downloadReport(row.id)"
          >
            下载 CSV
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="reports && reports.total > reports.pageSize"
      class="analytics-pagination"
      background
      layout="prev, pager, next, total"
      :current-page="reports.page"
      :page-size="reports.pageSize"
      :total="reports.total"
      @current-change="changeReportPage"
    />
  </section>

  <section v-else class="analytics-panel">
    <div class="section-heading">
      <div>
        <h2>销售明细</h2>
        <p>不返回会员手机号等敏感信息；订单退款额不分摊到品项。</p>
      </div>
      <span v-if="lines" class="analytics-record-count">共 {{ lines.total }} 行</span>
    </div>
    <el-table v-loading="linesLoading" :data="lines?.records ?? []" empty-text="暂无销售明细">
      <el-table-column prop="businessDate" label="日期" width="112" />
      <el-table-column prop="orderNo" label="订单号" min-width="180" />
      <el-table-column label="门店" min-width="140">
        <template #default="{ row }">{{ shopName(row.shopId) }}</template>
      </el-table-column>
      <el-table-column prop="itemName" label="品项" min-width="180" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ itemTypeLabel(row.itemType) }}</template>
      </el-table-column>
      <el-table-column prop="categoryName" label="品类" min-width="130" />
      <el-table-column prop="brandName" label="品牌" min-width="120">
        <template #default="{ row }">{{ row.brandName || '—' }}</template>
      </el-table-column>
      <el-table-column label="数量" width="90" align="right">
        <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
      </el-table-column>
      <el-table-column label="行成交额" width="130" align="right">
        <template #default="{ row }">{{ formatMoney(row.lineSalesAmountBeforeRefund) }}</template>
      </el-table-column>
      <el-table-column label="订单退款" width="130" align="right">
        <template #default="{ row }">{{ formatMoney(row.orderRefundedAmount) }}</template>
      </el-table-column>
      <el-table-column label="维度质量" min-width="140">
        <template #default="{ row }">
          <span
            :class="[
              'snapshot-quality',
              `snapshot-quality--${row.dimensionSnapshotQuality.toLowerCase()}`,
            ]"
          >
            {{ snapshotQualityLabel(row.dimensionSnapshotQuality) }}
          </span>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="lines && lines.total > lines.pageSize"
      class="analytics-pagination"
      background
      layout="prev, pager, next, total"
      :current-page="lines.page"
      :page-size="lines.pageSize"
      :total="lines.total"
      @current-change="changeLinePage"
    />
  </section>
</template>

<style scoped>
.analytics-filter {
  display: flex;
  align-items: end;
  gap: 16px;
  padding: 18px;
  margin-bottom: 18px;
  background: var(--oc-surface-1);
  border: 1px solid var(--oc-border);
  border-radius: 12px;
}

.analytics-filter label,
.analytics-filter fieldset {
  display: grid;
  gap: 7px;
  min-width: 180px;
  margin: 0;
  padding: 0;
  border: 0;
}

.analytics-filter label > span,
.analytics-filter legend {
  color: var(--oc-text-soft);
  font-size: 13px;
  font-weight: 600;
}

.analytics-filter label:nth-child(2) {
  min-width: 320px;
}

.analytics-type-filter,
.analytics-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  background: var(--oc-surface-2);
  border-radius: 10px;
}

.analytics-type-filter button,
.analytics-tabs button {
  min-height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--oc-text-soft);
  font: inherit;
  cursor: pointer;
  transition: background-color 180ms ease-out, color 180ms ease-out;
}

.analytics-type-filter button:hover,
.analytics-tabs button:hover {
  color: var(--oc-text);
}

.analytics-type-filter button.active,
.analytics-tabs button.active {
  background: var(--oc-surface-1);
  color: var(--oc-accent-strong);
  box-shadow: 0 1px 4px rgb(47 26 38 / 10%);
}

.analytics-tabs {
  margin-bottom: 16px;
}

.analytics-definition,
.analytics-error {
  margin-bottom: 16px;
}

.analytics-metrics {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.analytics-metrics strong {
  font-size: 22px;
}

.analytics-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(320px, 0.85fr);
  gap: 18px;
  margin-bottom: 18px;
}

.analytics-brand-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.8fr) minmax(0, 1.5fr);
  gap: 28px;
  align-items: start;
}

.analytics-shop-layout {
  display: grid;
  gap: 26px;
}

.comparison-periods {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  padding: 14px 0 20px;
  border-bottom: 1px solid var(--oc-border);
  margin-bottom: 16px;
}

.comparison-periods > div {
  display: grid;
  gap: 4px;
}

.comparison-periods span,
.analytics-table-note {
  color: var(--oc-text-muted);
  font-size: 13px;
}

.comparison-periods strong {
  color: var(--oc-text);
  font-size: 15px;
}

.comparison-change {
  font-variant-numeric: tabular-nums;
}

.comparison-change--up {
  color: var(--oc-accent-strong);
}

.comparison-change--down {
  color: var(--oc-text-muted);
}

.analytics-table-note {
  margin: 14px 0 0;
}

.analytics-shop-bars {
  max-width: 920px;
}

.analytics-scope-note {
  padding: 10px 12px;
  margin: 0 0 18px;
  background: var(--oc-surface-2);
  border-radius: 8px;
  color: var(--oc-text-muted);
  font-size: 13px;
}

.analytics-brand-bars {
  padding: 4px 0;
}

.analytics-panel {
  padding: 20px;
  margin-bottom: 18px;
  overflow: hidden;
  background: var(--oc-surface-1);
  border: 1px solid var(--oc-border);
  border-radius: 12px;
}

.analytics-grid .analytics-panel {
  margin-bottom: 0;
}

.composition-list {
  display: grid;
  gap: 18px;
  margin-top: 12px;
}

.composition-row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) minmax(100px, 1.4fr) auto;
  align-items: center;
  gap: 12px;
}

.composition-row > div:first-child {
  display: grid;
  gap: 3px;
}

.composition-row span {
  color: var(--oc-text-muted);
  font-size: 12px;
}

.composition-track {
  height: 8px;
  overflow: hidden;
  background: var(--oc-surface-3);
  border-radius: 999px;
}

.composition-track span {
  display: block;
  height: 100%;
  background: var(--oc-accent);
  border-radius: inherit;
}

.analytics-empty,
.analytics-record-count {
  color: var(--oc-text-muted);
}

.analytics-pagination {
  justify-content: flex-end;
  margin-top: 18px;
}

.analytics-report-heading {
  align-items: center;
}

.report-status--expired {
  color: var(--oc-text-muted);
  background: var(--oc-surface-2);
}

.snapshot-quality {
  display: inline-flex;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--oc-surface-2);
  color: var(--oc-text-soft);
  font-size: 12px;
}

.snapshot-quality--transaction_time {
  background: var(--oc-success-soft);
  color: var(--oc-success);
}

.snapshot-quality--missing {
  background: var(--oc-danger-soft);
  color: var(--oc-danger);
}

@media (max-width: 1180px) {
  .analytics-filter {
    flex-wrap: wrap;
  }

  .analytics-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .analytics-grid {
    grid-template-columns: 1fr;
  }

  .analytics-brand-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .analytics-filter,
  .analytics-filter label,
  .analytics-filter fieldset,
  .analytics-filter label:nth-child(2) {
    width: 100%;
    min-width: 0;
  }

  .analytics-metrics {
    grid-template-columns: 1fr;
  }

  .composition-row {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .analytics-type-filter button,
  .analytics-tabs button {
    transition: none;
  }
}
</style>
