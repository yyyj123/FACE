<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Check,
  Clock,
  Delete,
  DocumentChecked,
  Plus,
  Refresh,
  Search,
  UserFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import {
  appendServiceRecordCorrection,
  completeService,
  getMemberCareHistory,
  getServiceRecord,
  getServiceRecordCorrections,
  getServiceRecordResources,
  getServiceRecords,
  startService,
  type CareKnowledgeOption,
  type MemberCareHistory,
  type ServiceConsumableOption,
  type ServiceRecordDetail,
  type ServiceRecordCorrection,
  type ServiceRecordPage,
  type ServiceRecordResources,
  type ServiceRecordStatus,
  type ServiceRecordSummary,
} from '../services/api'

type EditorMode = 'complete' | 'care'

interface ConsumptionLine {
  key: string
  optionKey?: string
  quantity: number
}

const auth = useAuthStore()
const today = localDateString(new Date())
const fromDate = new Date()
fromDate.setDate(fromDate.getDate() - 30)

const query = reactive({
  shopId: undefined as number | undefined,
  fromDate: localDateString(fromDate),
  toDate: today,
  status: 'ALL' as ServiceRecordStatus | 'ALL',
  keyword: '',
  page: 1,
  pageSize: 30,
})
const page = ref<ServiceRecordPage>()
const resources = ref<ServiceRecordResources>()
const selected = ref<ServiceRecordDetail>()
const memberHistory = ref<MemberCareHistory[]>([])
const corrections = ref<ServiceRecordCorrection[]>([])
const selectedAppointmentId = ref<number>()
const editorMode = ref<EditorMode>()
const loading = ref(true)
const detailLoading = ref(false)
const saving = ref(false)
const error = ref('')
const correctionReason = ref('')

const careForm = reactive({
  serviceSummary: '',
  nextVisitRecommendation: '',
  skinType: '',
  concerns: [] as string[],
  observations: '',
  homeCareAdvice: '',
  nextRecommendedAt: '',
})
const consumptionLines = ref<ConsumptionLine[]>([])

const statusOptions: Array<{ value: ServiceRecordStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: '全部记录' },
  { value: 'IN_PROGRESS', label: '服务中' },
  { value: 'COMPLETED', label: '已完成' },
]

const selectedShopName = computed(
  () => auth.shops.find((shop) => shop.id === query.shopId)?.name ?? '当前门店',
)
const selectedReadyAppointment = computed(() =>
  resources.value?.readyAppointments.find((item) => item.id === selectedAppointmentId.value),
)
const availableConsumables = computed(
  () => resources.value?.consumables.filter((item) => Number(item.quantityAvailable) > 0) ?? [],
)
const concernOptions = computed(() =>
  [...new Set((resources.value?.knowledge ?? []).map((item) => item.category).filter(Boolean))],
)

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  query.shopId = auth.context?.homeShopId ?? auth.shops[0]?.id
  await reloadAll()
})

async function reloadAll() {
  await Promise.all([loadPage(), loadResources()])
}

async function loadPage() {
  if (!query.shopId) return
  loading.value = true
  error.value = ''
  try {
    page.value = await getServiceRecords({
      shopId: query.shopId,
      fromDate: query.fromDate,
      toDate: query.toDate,
      status: query.status,
      keyword: query.keyword.trim() || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '服务记录加载失败'
  } finally {
    loading.value = false
  }
}

async function loadResources() {
  if (!query.shopId) return
  try {
    resources.value = await getServiceRecordResources(query.shopId)
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '服务资源加载失败')
  }
}

async function changeShop() {
  query.page = 1
  closeWorkspace()
  selectedAppointmentId.value = undefined
  await reloadAll()
}

async function applyFilters() {
  query.page = 1
  await loadPage()
}

async function resetFilters() {
  query.status = 'ALL'
  query.keyword = ''
  query.fromDate = localDateString(fromDate)
  query.toDate = today
  query.page = 1
  await loadPage()
}

async function beginSelectedAppointment() {
  if (!query.shopId || !selectedReadyAppointment.value) {
    ElMessage.warning('请先选择一条已到店预约')
    return
  }
  saving.value = true
  try {
    const detail = await startService({
      shopId: query.shopId,
      appointmentId: selectedReadyAppointment.value.id,
      appointmentVersion: selectedReadyAppointment.value.version,
    })
    selectedAppointmentId.value = undefined
    selected.value = detail
    editorMode.value = 'complete'
    fillCareForm(detail)
    consumptionLines.value = []
    await Promise.all([loadPage(), loadResources(), loadMemberHistory(detail.memberId)])
    ElMessage.success(`服务单 ${detail.recordNo} 已开始`)
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '开始服务失败')
  } finally {
    saving.value = false
  }
}

async function openRecord(record: ServiceRecordSummary, mode?: EditorMode) {
  if (!query.shopId) return
  detailLoading.value = true
  try {
    selected.value = await getServiceRecord(record.id, query.shopId)
    editorMode.value = mode
    fillCareForm(selected.value)
    consumptionLines.value = []
    correctionReason.value = ''
    await Promise.all([
      loadMemberHistory(selected.value.memberId),
      loadCorrections(selected.value.id),
    ])
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '服务记录详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function loadMemberHistory(memberId: number) {
  if (!query.shopId) return
  memberHistory.value = await getMemberCareHistory(query.shopId, memberId)
}

async function loadCorrections(serviceRecordId: number) {
  if (!query.shopId) return
  corrections.value = await getServiceRecordCorrections(serviceRecordId, query.shopId)
}

function fillCareForm(detail: ServiceRecordDetail) {
  Object.assign(careForm, {
    serviceSummary: detail.serviceSummary ?? '',
    nextVisitRecommendation: detail.nextVisitRecommendation ?? '',
    skinType: detail.skinType ?? '',
    concerns: [...(detail.concerns ?? [])],
    observations: detail.observations ?? '',
    homeCareAdvice: detail.homeCareAdvice ?? '',
    nextRecommendedAt: detail.nextRecommendedAt?.slice(0, 10) ?? '',
  })
}

function applyKnowledge(item: CareKnowledgeOption) {
  if (!careForm.concerns.includes(item.category)) careForm.concerns.push(item.category)
  careForm.observations = [careForm.observations, item.observationFocus]
    .filter(Boolean)
    .join('；')
  careForm.homeCareAdvice = [careForm.homeCareAdvice, item.advice].filter(Boolean).join('；')
  ElMessage.success(`已引用“${item.title}”的护理要点`)
}

function addConsumptionLine() {
  consumptionLines.value.push({ key: uniqueKey('line'), quantity: 1 })
}

function removeConsumptionLine(key: string) {
  consumptionLines.value = consumptionLines.value.filter((item) => item.key !== key)
}

function consumableKey(item: ServiceConsumableOption) {
  return `${item.productId}:${item.locationId}`
}

function consumableByKey(value?: string) {
  return availableConsumables.value.find((item) => consumableKey(item) === value)
}

async function saveCare() {
  if (!selected.value || !query.shopId) return
  if (!careForm.serviceSummary.trim() || !careForm.observations.trim()) {
    ElMessage.warning('请填写服务总结和护理观察')
    return
  }
  saving.value = true
  try {
    if (editorMode.value === 'complete') {
      const consumptions = consumptionLines.value.map((line) => {
        const option = consumableByKey(line.optionKey)
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
      selected.value = await completeService(selected.value.id, {
        shopId: query.shopId,
        version: selected.value.version,
        ...carePayload(),
        consumptions,
        idempotencyKey: uniqueKey('service-complete'),
      })
      ElMessage.success('服务已完成，护理档案、耗材流水和待收款订单已同步')
    } else {
      if (!correctionReason.value.trim()) {
        ElMessage.warning('追加更正必须填写原因')
        return
      }
      await appendServiceRecordCorrection(selected.value.id, {
        shopId: query.shopId,
        serviceRecordVersion: selected.value.version,
        reason: correctionReason.value.trim(),
        correctedFields: carePayload(),
        idempotencyKey: uniqueKey('care-correction'),
      })
      selected.value = await getServiceRecord(selected.value.id, query.shopId)
      await loadCorrections(selected.value.id)
      correctionReason.value = ''
      ElMessage.success('护理更正已追加，原始护理事实保持不变')
    }
    editorMode.value = undefined
    fillCareForm(selected.value)
    await Promise.all([loadPage(), loadResources(), loadMemberHistory(selected.value.memberId)])
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '护理档案保存失败')
  } finally {
    saving.value = false
  }
}

function carePayload() {
  return {
    serviceSummary: careForm.serviceSummary.trim(),
    nextVisitRecommendation: careForm.nextVisitRecommendation.trim() || undefined,
    skinType: careForm.skinType || undefined,
    concerns: careForm.concerns,
    observations: careForm.observations.trim(),
    homeCareAdvice: careForm.homeCareAdvice.trim() || undefined,
    nextRecommendedAt: careForm.nextRecommendedAt || undefined,
  }
}

function closeWorkspace() {
  selected.value = undefined
  editorMode.value = undefined
  memberHistory.value = []
  corrections.value = []
  correctionReason.value = ''
}

function statusLabel(status: ServiceRecordStatus) {
  return { IN_PROGRESS: '服务中', COMPLETED: '已完成', VOID: '已作废' }[status]
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
  <section class="service-center">
    <header class="page-heading">
      <div>
        <span class="section-kicker">SERVICE &amp; CARE</span>
        <h1>到店服务与护理档案</h1>
        <p>把预约执行、护理记录和耗材领用收在同一条可追溯服务链里。</p>
      </div>
      <el-select
        v-model="query.shopId"
        class="shop-select"
        aria-label="选择门店"
        @change="changeShop"
      >
        <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
      </el-select>
    </header>

    <div class="summary-grid" aria-label="服务概览">
      <article class="summary-card summary-card--ready">
        <span>已到店待开始</span>
        <strong>{{ resources?.readyAppointments.length ?? 0 }}</strong>
        <small>{{ selectedShopName }}</small>
      </article>
      <article class="summary-card">
        <span>服务中</span>
        <strong>{{ page?.summary.inProgress ?? 0 }}</strong>
        <small>正在执行的护理</small>
      </article>
      <article class="summary-card">
        <span>已完成</span>
        <strong>{{ page?.summary.completed ?? 0 }}</strong>
        <small>当前筛选周期</small>
      </article>
      <article class="summary-card">
        <span>档案待补</span>
        <strong>{{ page?.summary.carePending ?? 0 }}</strong>
        <small>完成后未填写护理记录</small>
      </article>
    </div>

    <section class="start-panel">
      <div class="start-panel__copy">
        <el-icon><Clock /></el-icon>
        <div>
          <strong>开始已到店服务</strong>
          <span>只显示前台已办理到店、且尚未生成服务单的预约。</span>
        </div>
      </div>
      <el-select
        v-model="selectedAppointmentId"
        class="appointment-select"
        clearable
        placeholder="选择已到店预约"
        :empty-values="[null, undefined]"
      >
        <el-option
          v-for="item in resources?.readyAppointments"
          :key="item.id"
          :label="`${item.memberName} · ${item.serviceNames} · ${item.staffName}`"
          :value="item.id"
        />
      </el-select>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="!selectedAppointmentId"
        @click="beginSelectedAppointment"
      >
        开始服务
      </el-button>
    </section>

    <section class="filter-panel">
      <div class="date-range">
        <el-date-picker
          v-model="query.fromDate"
          type="date"
          value-format="YYYY-MM-DD"
          aria-label="开始日期"
        />
        <span>至</span>
        <el-date-picker
          v-model="query.toDate"
          type="date"
          value-format="YYYY-MM-DD"
          aria-label="结束日期"
        />
      </div>
      <el-select v-model="query.status" aria-label="服务状态">
        <el-option
          v-for="item in statusOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="服务单 / 会员 / 手机 / 美容师"
        @keyup.enter="applyFilters"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="applyFilters">查询</el-button>
      <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
    </section>

    <el-alert v-if="error" class="page-alert" type="error" :closable="false" :title="error" />

    <div class="workspace-grid" :class="{ 'workspace-grid--open': selected }">
      <section class="records-panel">
        <div class="panel-heading">
          <div>
            <h2>服务记录</h2>
            <span>共 {{ page?.total ?? 0 }} 条</span>
          </div>
          <span class="privacy-note">护理内容仅对授权岗位可见</span>
        </div>
        <el-table
          v-loading="loading"
          :data="page?.records ?? []"
          row-key="id"
          class="service-table"
          empty-text="当前筛选条件下没有服务记录"
        >
          <el-table-column label="服务单" min-width="178">
            <template #default="{ row }">
              <button
                class="record-link"
                type="button"
                @click="openRecord(row as ServiceRecordSummary)"
              >
                <strong>{{ row.recordNo }}</strong>
                <span>{{ formatDateTime(row.actualStartAt) }}</span>
              </button>
            </template>
          </el-table-column>
          <el-table-column label="会员 / 美容师" min-width="180">
            <template #default="{ row }">
              <div class="person-cell">
                <strong>{{ row.memberName }}</strong>
                <span>{{ row.staffName }} · {{ row.staffLevel || '美容师' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="serviceNames" label="护理项目" min-width="190" show-overflow-tooltip />
          <el-table-column label="状态" width="112">
            <template #default="{ row }">
              <span class="status-mark" :class="`status-mark--${row.status.toLowerCase()}`">
                {{ statusLabel(row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="档案 / 耗材" width="130">
            <template #default="{ row }">
              <div class="trace-cell">
                <span>{{ row.careCompleted ? '档案完整' : '待补档案' }}</span>
                <small>{{ row.consumptionCount }} 条耗材流水</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="178" fixed="right">
            <template #default="{ row }">
              <el-button link @click="openRecord(row as ServiceRecordSummary)">查看</el-button>
              <el-button
                v-if="row.status === 'IN_PROGRESS'"
                link
                type="primary"
                @click="openRecord(row as ServiceRecordSummary, 'complete')"
              >
                完成服务
              </el-button>
              <el-button
                v-else-if="row.status === 'COMPLETED'"
                link
                type="primary"
                @click="openRecord(row as ServiceRecordSummary, 'care')"
              >
                {{ row.careCompleted ? '追加更正' : '补录档案' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="(page?.total ?? 0) > query.pageSize"
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          class="table-pagination"
          layout="total, prev, pager, next"
          :total="page?.total ?? 0"
          @current-change="loadPage"
        />
      </section>

      <aside v-if="selected" v-loading="detailLoading" class="record-workspace">
        <div class="workspace-heading">
          <div>
            <span>{{ selected.recordNo }}</span>
            <h2>{{ selected.memberName }}的护理记录</h2>
          </div>
          <el-button text aria-label="关闭详情" @click="closeWorkspace">关闭</el-button>
        </div>

        <div class="record-facts">
          <div>
            <span>护理项目</span>
            <strong>{{ selected.serviceNames || '—' }}</strong>
          </div>
          <div>
            <span>服务美容师</span>
            <strong>{{ selected.staffName }}</strong>
          </div>
          <div>
            <span>服务时间</span>
            <strong>{{ formatDateTime(selected.actualStartAt) }}</strong>
          </div>
        </div>

        <template v-if="editorMode">
          <div class="editor-title">
            <div>
              <span>{{ editorMode === 'complete' ? 'COMPLETE SERVICE' : 'APPEND CORRECTION' }}</span>
              <h3>{{ editorMode === 'complete' ? '完成服务并建立档案' : '追加护理更正' }}</h3>
            </div>
            <el-icon><DocumentChecked /></el-icon>
          </div>
          <p v-if="editorMode === 'care'" class="editor-guard">
            本次提交只会追加更正记录，提交后不可覆盖原始护理事实。
          </p>

          <el-form label-position="top" class="care-form">
            <div class="form-grid">
              <el-form-item label="肤质">
                <el-select v-model="careForm.skinType" clearable placeholder="选择肤质">
                  <el-option
                    v-for="item in resources?.skinTypes"
                    :key="item"
                    :label="item"
                    :value="item"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="建议下次到店日期">
                <el-date-picker
                  v-model="careForm.nextRecommendedAt"
                  type="date"
                  value-format="YYYY-MM-DD"
                />
              </el-form-item>
            </div>
            <el-form-item label="皮肤关注点">
              <el-select
                v-model="careForm.concerns"
                multiple
                allow-create
                filterable
                default-first-option
                placeholder="输入或选择关注点"
                no-data-text="暂无预设，可直接输入后按回车添加"
              >
                <el-option
                  v-for="item in concernOptions"
                  :key="item"
                  :label="item"
                  :value="item"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="本次服务总结" required>
              <el-input
                v-model="careForm.serviceSummary"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="护理观察" required>
              <el-input
                v-model="careForm.observations"
                type="textarea"
                :rows="4"
                maxlength="4000"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="居家护理建议">
              <el-input v-model="careForm.homeCareAdvice" type="textarea" :rows="3" maxlength="4000" />
            </el-form-item>
            <el-form-item label="下次到店建议">
              <el-input
                v-model="careForm.nextVisitRecommendation"
                type="textarea"
                :rows="2"
                maxlength="1000"
              />
            </el-form-item>
          </el-form>

          <el-form
            v-if="editorMode === 'care'"
            label-position="top"
            class="correction-reason-form"
          >
            <el-form-item label="更正原因" required>
              <el-input
                v-model="correctionReason"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                placeholder="说明为何需要更正，提交后不可覆盖原始护理事实"
              />
            </el-form-item>
          </el-form>

          <section v-if="editorMode === 'complete'" class="consumption-editor">
            <div class="subsection-heading">
              <div>
                <h3>本次耗材领用</h3>
                <span>保存服务时同步生成不可修改的出库流水。</span>
              </div>
              <el-button :icon="Plus" @click="addConsumptionLine">添加耗材</el-button>
            </div>
            <div v-if="!consumptionLines.length" class="inline-empty">
              本次没有耗材可直接完成；如有领用，请先添加明细。
            </div>
            <div v-for="line in consumptionLines" :key="line.key" class="consumption-line">
              <el-select v-model="line.optionKey" filterable placeholder="耗材与领用位置">
                <el-option
                  v-for="item in availableConsumables"
                  :key="consumableKey(item)"
                  :label="`${item.productName} · ${item.locationName} · 可用 ${item.quantityAvailable}`"
                  :value="consumableKey(item)"
                />
              </el-select>
              <el-input-number v-model="line.quantity" :min="0.001" :precision="3" />
              <el-button
                class="consumption-remove"
                text
                type="danger"
                :icon="Delete"
                aria-label="移除这条耗材"
                title="移除耗材"
                @click="removeConsumptionLine(line.key)"
              />
            </div>
          </section>

          <section class="knowledge-panel">
            <div class="subsection-heading">
              <div>
                <h3>护理知识参考</h3>
                <span>点击一项，将观察重点和护理建议带入当前档案。</span>
              </div>
            </div>
            <div class="knowledge-list">
              <button
                v-for="item in resources?.knowledge"
                :key="item.id"
                type="button"
                @click="applyKnowledge(item)"
              >
                <strong>{{ item.title }}</strong>
                <span>{{ item.category }} · {{ item.cause }}</span>
              </button>
            </div>
          </section>

          <div class="editor-actions">
            <el-button @click="editorMode = undefined">取消编辑</el-button>
            <el-button type="primary" :icon="Check" :loading="saving" @click="saveCare">
              {{ editorMode === 'complete' ? '完成服务' : '追加更正' }}
            </el-button>
          </div>
        </template>

        <template v-else>
          <section class="care-readonly">
            <div class="subsection-heading">
              <div>
                <h3>本次护理摘要</h3>
                <span>{{ selected.skinType || '未记录肤质' }}</span>
              </div>
              <el-button
                v-if="selected.status === 'COMPLETED'"
                link
                type="primary"
                @click="editorMode = 'care'"
              >
                追加更正
              </el-button>
            </div>
            <div class="care-copy">
              <span>服务总结</span>
              <p>{{ selected.serviceSummary || '尚未填写' }}</p>
              <span>护理观察</span>
              <p>{{ selected.observations || '尚未填写' }}</p>
              <span>居家建议</span>
              <p>{{ selected.homeCareAdvice || '尚未填写' }}</p>
            </div>
            <div v-if="selected.concerns?.length" class="concern-list">
              <span v-for="item in selected.concerns" :key="item">{{ item }}</span>
            </div>
          </section>

          <section class="correction-readonly">
            <div class="subsection-heading">
              <div>
                <h3>护理更正记录</h3>
                <span>共 {{ corrections.length }} 条追加事实，原始护理记录不被覆盖</span>
              </div>
            </div>
            <div v-if="corrections.length" class="correction-list">
              <article v-for="item in corrections" :key="item.id">
                <div>
                  <strong>{{ item.reason }}</strong>
                  <time>{{ formatDateTime(item.createdAt) }}</time>
                </div>
                <p>基于服务记录版本 {{ item.baseVersion }} 追加</p>
              </article>
            </div>
            <div v-else class="inline-empty">当前没有护理更正记录。</div>
          </section>

          <section class="consumption-readonly">
            <div class="subsection-heading">
              <div>
                <h3>耗材流水</h3>
                <span>已关联 {{ selected.consumptions.length }} 条库存流水</span>
              </div>
            </div>
            <div v-if="selected.consumptions.length" class="consumption-list">
              <div v-for="item in selected.consumptions" :key="item.id">
                <div>
                  <strong>{{ item.productName }}</strong>
                  <span>{{ item.locationName }}</span>
                </div>
                <span>× {{ item.quantity }}</span>
              </div>
            </div>
            <div v-else class="inline-empty">本次服务没有记录耗材领用。</div>
          </section>
        </template>

        <section class="history-panel">
          <div class="subsection-heading">
            <div>
              <h3>会员历史护理</h3>
              <span>最近 {{ memberHistory.length }} 次已完成服务</span>
            </div>
            <el-icon><UserFilled /></el-icon>
          </div>
          <div class="history-list">
            <article v-for="item in memberHistory.slice(0, 6)" :key="item.id">
              <time>{{ formatDateTime(item.actualStartAt) }}</time>
              <strong>{{ item.serviceNames || item.recordNo }}</strong>
              <p>{{ item.observations || item.serviceSummary || '未填写护理观察' }}</p>
            </article>
          </div>
        </section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.correction-reason-form {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-soft);
}

.correction-readonly {
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.correction-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.correction-list article {
  padding: 13px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
}

.correction-list article > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.correction-list time,
.correction-list p {
  color: var(--text-muted);
  font-size: 12px;
}

.correction-list p {
  margin: 7px 0 0;
}
.service-center {
  display: grid;
  gap: 18px;
}

.page-heading,
.start-panel,
.filter-panel,
.panel-heading,
.workspace-heading,
.subsection-heading,
.editor-title,
.editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h1,
.panel-heading h2,
.workspace-heading h2,
.subsection-heading h3,
.editor-title h3 {
  margin: 0;
}

.page-heading h1 {
  margin-top: 4px;
  font-size: clamp(26px, 3vw, 38px);
  line-height: 1.12;
}

.page-heading p {
  margin: 8px 0 0;
  color: var(--oc-text-muted);
}

.section-kicker,
.editor-title span {
  color: var(--oc-copper);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.shop-select {
  width: min(320px, 38vw);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--oc-border);
  border-radius: var(--oc-radius-md);
  overflow: hidden;
  background: var(--oc-surface-1);
}

.summary-card {
  min-height: 116px;
  padding: 20px 22px;
  display: grid;
  align-content: space-between;
  border-right: 1px solid var(--oc-border);
}

.summary-card:last-child {
  border-right: 0;
}

.summary-card span,
.summary-card small {
  color: var(--oc-text-muted);
}

.summary-card strong {
  font-size: 32px;
  line-height: 1;
}

.summary-card--ready strong {
  color: var(--oc-copper);
}

.start-panel,
.filter-panel,
.records-panel,
.record-workspace {
  border: 1px solid var(--oc-border);
  border-radius: var(--oc-radius-md);
  background: var(--oc-surface-1);
}

.start-panel {
  padding: 16px 18px;
}

.start-panel__copy {
  min-width: 230px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.start-panel__copy .el-icon {
  width: 36px;
  height: 36px;
  border: 1px solid var(--oc-border-strong);
  border-radius: 50%;
  color: var(--oc-copper);
}

.start-panel__copy div,
.person-cell,
.trace-cell {
  display: grid;
  gap: 4px;
}

.start-panel__copy span,
.person-cell span,
.trace-cell small,
.panel-heading span,
.privacy-note,
.workspace-heading span,
.record-facts span,
.subsection-heading span {
  color: var(--oc-text-soft);
  font-size: 12px;
}

.appointment-select {
  flex: 1;
  min-width: 260px;
}

.filter-panel {
  padding: 14px 16px;
  display: grid;
  grid-template-columns: minmax(380px, 1.35fr) minmax(150px, 0.55fr) minmax(250px, 1fr) auto auto;
}

.date-range {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 10px;
}

.date-range > span {
  color: var(--oc-text-soft);
}

.page-alert {
  margin: 0;
}

.workspace-grid {
  min-width: 0;
}

.workspace-grid--open {
  display: grid;
  grid-template-columns: minmax(620px, 1.35fr) minmax(390px, 0.85fr);
  align-items: start;
  gap: 18px;
}

.records-panel {
  min-width: 0;
  overflow: hidden;
}

.panel-heading,
.workspace-heading {
  padding: 18px 20px;
  border-bottom: 1px solid var(--oc-border);
}

.panel-heading > div {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.service-table {
  width: 100%;
}

.record-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  display: grid;
  gap: 4px;
}

.record-link:hover strong {
  color: var(--oc-accent-strong);
}

.record-link span {
  color: var(--oc-text-soft);
  font-size: 12px;
}

.status-mark {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--oc-text-muted);
  font-size: 13px;
}

.status-mark::before {
  content: "";
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--oc-text-soft);
}

.status-mark--in_progress::before {
  background: var(--oc-copper);
  box-shadow: 0 0 0 4px rgb(215 161 140 / 12%);
}

.status-mark--completed::before {
  background: var(--oc-success);
}

.status-mark--void::before {
  background: var(--oc-danger);
}

.table-pagination {
  justify-content: flex-end;
  padding: 16px 20px;
  border-top: 1px solid var(--oc-border);
}

.record-workspace {
  position: sticky;
  top: 18px;
  max-height: calc(100vh - 104px);
  overflow: auto;
}

.workspace-heading h2 {
  margin-top: 4px;
  font-size: 22px;
}

.record-facts {
  padding: 16px 20px;
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 14px;
  border-bottom: 1px solid var(--oc-border);
}

.record-facts div {
  display: grid;
  gap: 4px;
}

.record-facts div:last-child {
  grid-column: 1 / -1;
}

.editor-title,
.care-form,
.consumption-editor,
.knowledge-panel,
.care-readonly,
.consumption-readonly,
.history-panel {
  padding: 18px 20px;
  border-bottom: 1px solid var(--oc-border);
}

.editor-title .el-icon {
  font-size: 28px;
  color: var(--oc-copper);
}

.editor-guard {
  margin: 0;
  padding: 12px 20px;
  border-bottom: 1px solid var(--oc-border);
  color: var(--oc-text-soft);
  background: var(--oc-surface-2);
  font-size: 13px;
  line-height: 1.6;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.care-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.care-form :deep(.el-select),
.care-form :deep(.el-date-editor) {
  width: 100%;
}

.subsection-heading {
  align-items: flex-start;
  margin-bottom: 14px;
}

.subsection-heading h3 {
  margin-bottom: 4px;
  font-size: 16px;
}

.inline-empty {
  padding: 14px;
  border: 1px dashed var(--oc-border-strong);
  border-radius: var(--oc-radius-sm);
  color: var(--oc-text-soft);
  font-size: 13px;
}

.consumption-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px 44px;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.consumption-line :deep(.el-input-number) {
  width: 100%;
}

.consumption-remove {
  width: 44px;
  height: 44px;
  margin: 0;
  padding: 0;
}

.knowledge-list {
  display: grid;
  gap: 8px;
  max-height: 210px;
  overflow: auto;
}

.knowledge-list button {
  padding: 11px 12px;
  border: 1px solid var(--oc-border);
  border-radius: var(--oc-radius-sm);
  background: var(--oc-surface-2);
  color: var(--oc-text);
  text-align: left;
  cursor: pointer;
  display: grid;
  gap: 4px;
  transition:
    border-color var(--oc-motion-fast),
    background var(--oc-motion-fast);
}

.knowledge-list button:hover {
  border-color: var(--oc-border-strong);
  background: var(--oc-surface-3);
}

.knowledge-list button span {
  color: var(--oc-text-soft);
  font-size: 12px;
}

.editor-actions {
  padding: 16px 20px;
}

.care-copy {
  display: grid;
  gap: 6px;
}

.care-copy span {
  margin-top: 8px;
  color: var(--oc-copper);
  font-size: 12px;
  font-weight: 700;
}

.care-copy p {
  margin: 0;
  color: var(--oc-text-muted);
  line-height: 1.7;
  white-space: pre-wrap;
}

.concern-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.concern-list span {
  padding: 5px 9px;
  border: 1px solid var(--oc-border-strong);
  border-radius: 999px;
  color: var(--oc-text-muted);
  font-size: 12px;
}

.consumption-list {
  display: grid;
  gap: 8px;
}

.consumption-list > div {
  padding: 10px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--oc-border);
}

.consumption-list > div:last-child {
  border-bottom: 0;
}

.consumption-list > div > div {
  display: grid;
  gap: 3px;
}

.consumption-list span {
  color: var(--oc-text-soft);
  font-size: 12px;
}

.history-list {
  display: grid;
  gap: 12px;
}

.history-list article {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--oc-border-strong);
}

.history-list time,
.history-list p {
  color: var(--oc-text-soft);
  font-size: 12px;
}

.history-list strong {
  display: block;
  margin: 3px 0;
}

.history-list p {
  margin: 0;
  line-height: 1.55;
}

@media (max-width: 1240px) {
  .workspace-grid--open {
    grid-template-columns: 1fr;
  }

  .record-workspace {
    position: static;
    max-height: none;
  }
}

@media (max-width: 880px) {
  .summary-grid {
    grid-template-columns: 1fr 1fr;
  }

  .summary-card:nth-child(2) {
    border-right: 0;
  }

  .summary-card:nth-child(-n + 2) {
    border-bottom: 1px solid var(--oc-border);
  }

  .start-panel,
  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .appointment-select,
  .shop-select {
    width: 100%;
  }

  .filter-panel {
    grid-template-columns: 1fr 1fr;
  }

  .date-range,
  .filter-panel .el-input {
    grid-column: 1 / -1;
  }
}

@media (max-width: 560px) {
  .summary-grid,
  .form-grid,
  .record-facts,
  .filter-panel {
    grid-template-columns: 1fr;
  }

  .summary-card {
    border-right: 0;
    border-bottom: 1px solid var(--oc-border);
  }

  .summary-card:last-child {
    border-bottom: 0;
  }

  .consumption-line {
    grid-template-columns: 1fr;
  }
}
</style>
