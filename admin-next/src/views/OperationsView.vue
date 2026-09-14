<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  actOnAfterSaleCase,
  changeCommissionEntryStatus,
  changeCommissionRuleStatus,
  createCommissionSettlement,
  decideApproval,
  getAfterSaleCases,
  getApprovals,
  getCommissionEntries,
  getCommissionRules,
  getCommissionSettlements,
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  requestCommissionAdjustment,
  transitionCommissionSettlement,
  type AfterSaleCaseSummary,
  type ApprovalSummary,
  type CommissionEntrySummary,
  type CommissionRuleSummary,
  type CommissionSettlementSummary,
  type NotificationSummary,
} from '../services/api'
import { useAuthStore } from '../stores/auth'

type TabKey = 'rules' | 'entries' | 'settlements' | 'after-sales' | 'approvals' | 'notifications'

const auth = useAuthStore()
const activeTab = ref<TabKey>('entries')
const shopId = ref<number>()
const loading = ref(false)
const actingId = ref<number>()
const error = ref('')
const rules = ref<CommissionRuleSummary[]>([])
const entries = ref<CommissionEntrySummary[]>([])
const settlements = ref<CommissionSettlementSummary[]>([])
const afterSales = ref<AfterSaleCaseSummary[]>([])
const approvals = ref<ApprovalSummary[]>([])
const notifications = ref<NotificationSummary[]>([])
const unreadCount = ref(0)
const settlementForm = ref({
  periodStart: new Date().toISOString().slice(0, 8) + '01',
  periodEnd: new Date().toISOString().slice(0, 10),
})
const adjustmentEntry = ref<CommissionEntrySummary>()
const adjustmentAmount = ref<number>()
const adjustmentReason = ref('')

const permissions = computed(() => auth.context?.permissions ?? [])
const can = (permission: string) => permissions.value.includes(permission)
const tabDefinitions: Array<{ key: TabKey; label: string; permission: string }> = [
  { key: 'rules', label: '提成规则', permission: 'commission:rule:view' },
  { key: 'entries', label: '提成流水', permission: 'commission:entry:view' },
  { key: 'settlements', label: '结算批次', permission: 'commission:settlement:view' },
  { key: 'after-sales', label: '售后工单', permission: 'aftersale:view' },
  { key: 'approvals', label: '审批中心', permission: 'approval:view' },
  { key: 'notifications', label: '我的通知', permission: 'notification:view:self' },
]
const tabs = computed(() => tabDefinitions.filter((item) => can(item.permission)))

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  shopId.value = auth.context?.homeShopId ?? auth.shops[0]?.id
  activeTab.value = tabs.value[0]?.key ?? 'notifications'
  await loadActive()
})

watch(shopId, async (next, previous) => {
  if (next && previous && next !== previous && activeTab.value !== 'notifications') {
    await loadActive()
  }
})

async function loadActive() {
  if (activeTab.value !== 'notifications' && !shopId.value) {
    error.value = '当前账号没有可访问门店'
    return
  }
  loading.value = true
  error.value = ''
  try {
    switch (activeTab.value) {
      case 'rules':
        rules.value = await getCommissionRules(shopId.value!)
        break
      case 'entries':
        entries.value = (await getCommissionEntries(shopId.value!)).records
        break
      case 'settlements':
        settlements.value = (await getCommissionSettlements(shopId.value!)).records
        break
      case 'after-sales':
        afterSales.value = (await getAfterSaleCases(shopId.value!)).records
        break
      case 'approvals':
        approvals.value = (await getApprovals(shopId.value!)).records
        break
      case 'notifications': {
        const result = await getNotifications()
        notifications.value = result.records
        unreadCount.value = result.unreadCount
        break
      }
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '经营协同数据加载失败'
  } finally {
    loading.value = false
  }
}

async function changeRule(rule: CommissionRuleSummary, action: 'publish' | 'retire') {
  const verb = action === 'publish' ? '发布' : '停用'
  await runAction(rule.id, async () => {
    await ElMessageBox.confirm(
      `${verb}后将生成不可覆盖的规则版本，确认${verb}“${rule.ruleName}”？`,
      `${verb}提成规则`,
      { confirmButtonText: `确认${verb}`, cancelButtonText: '返回', type: 'warning' },
    )
    await changeCommissionRuleStatus(rule.id, action, shopId.value!, rule.version)
    ElMessage.success(`提成规则已${verb}`)
    await loadActive()
  })
}

async function changeEntry(entry: CommissionEntrySummary, action: 'freeze' | 'unfreeze') {
  const verb = action === 'freeze' ? '冻结' : '解冻'
  await runAction(entry.id, async () => {
    const { value } = await ElMessageBox.prompt(
      `请输入${verb}原因，操作会写入不可变历史。`,
      `${verb}提成流水`,
      {
        confirmButtonText: `确认${verb}`,
        cancelButtonText: '返回',
        inputValidator: (text) => Boolean(text?.trim()) || '必须填写原因',
      },
    )
    await changeCommissionEntryStatus(entry.id, action, {
      shop_id: shopId.value!,
      version: entry.version,
      reason: value.trim(),
    })
    ElMessage.success(`提成流水已${verb}`)
    await loadActive()
  })
}

function openAdjustment(entry: CommissionEntrySummary) {
  adjustmentEntry.value = entry
  adjustmentAmount.value = undefined
  adjustmentReason.value = ''
}

async function submitAdjustment() {
  if (!adjustmentEntry.value || !adjustmentAmount.value || !adjustmentReason.value.trim()) {
    error.value = '调整金额不能为 0，且必须填写调整原因'
    return
  }
  await runAction(adjustmentEntry.value.id, async () => {
    await requestCommissionAdjustment(adjustmentEntry.value!.id, {
      shop_id: shopId.value!,
      amount: adjustmentAmount.value!,
      reason: adjustmentReason.value.trim(),
    })
    adjustmentEntry.value = undefined
    ElMessage.success('调整申请已提交，等待职责分离审批')
    await loadActive()
  })
}

async function createSettlement() {
  if (!settlementForm.value.periodStart || !settlementForm.value.periodEnd) {
    error.value = '请选择完整结算周期'
    return
  }
  await runAction(-1, async () => {
    await createCommissionSettlement({
      shop_id: shopId.value!,
      period_start: settlementForm.value.periodStart,
      period_end: settlementForm.value.periodEnd,
      currency_code: 'CNY',
    })
    ElMessage.success('结算批次已创建，请继续计算并由另一职责人员确认')
    await loadActive()
  })
}

async function advanceSettlement(batch: CommissionSettlementSummary) {
  const transitions: Partial<Record<
    CommissionSettlementSummary['status'],
    { action: 'calculate' | 'confirm' | 'mark-paid' | 'close'; label: string }
  >> = {
    DRAFT: { action: 'calculate', label: '计算' },
    CALCULATED: { action: 'confirm', label: '确认' },
    CONFIRMED: { action: 'mark-paid', label: '标记支付' },
    PAID: { action: 'close', label: '关闭' },
  }
  const transition = transitions[batch.status]
  if (!transition) return
  await runAction(batch.id, async () => {
    let paymentReference: string | undefined
    if (transition.action === 'mark-paid') {
      const { value } = await ElMessageBox.prompt(
        '请输入可追溯的支付参考号。系统只记录参考号，不记录支付凭据。',
        '标记提成结算已支付',
        {
          confirmButtonText: '确认支付状态',
          cancelButtonText: '返回',
          inputValidator: (text) => Boolean(text?.trim()) || '必须填写支付参考号',
        },
      )
      paymentReference = value.trim()
    } else {
      await ElMessageBox.confirm(
        `确认对结算单 ${batch.settlementNo} 执行“${transition.label}”？`,
        '更新结算状态',
        { confirmButtonText: '确认执行', cancelButtonText: '返回', type: 'warning' },
      )
    }
    await transitionCommissionSettlement(batch.id, transition.action, {
      shop_id: shopId.value!,
      version: batch.version,
      payment_reference: paymentReference,
    })
    ElMessage.success(`结算单已${transition.label}`)
    await loadActive()
  })
}

function nextAfterSaleStatus(item: AfterSaleCaseSummary) {
  return {
    OPEN: 'TRIAGED',
    TRIAGED: 'PROCESSING',
    REOPENED: 'PROCESSING',
    PROCESSING: 'RESOLVED',
    WAITING_CUSTOMER: 'PROCESSING',
    RESOLVED: 'CLOSED',
  }[item.status]
}

async function advanceAfterSale(item: AfterSaleCaseSummary) {
  const target = nextAfterSaleStatus(item)
  if (!target) return
  await runAction(item.id, async () => {
    let note = `状态更新为 ${statusLabel(target)}`
    if (['RESOLVED', 'CLOSED', 'REJECTED'].includes(target)) {
      const result = await ElMessageBox.prompt(
        '请填写处理结论。内容会进入售后追加式日志，请勿填写健康隐私或支付凭据。',
        `将工单更新为${statusLabel(target)}`,
        {
          confirmButtonText: '确认更新',
          cancelButtonText: '返回',
          inputValidator: (text) => Boolean(text?.trim()) || '必须填写处理结论',
        },
      )
      note = result.value.trim()
    }
    await actOnAfterSaleCase(item.id, {
      shop_id: shopId.value!,
      version: item.version,
      target_status: target,
      note,
    })
    ElMessage.success('售后工单已更新')
    await loadActive()
  })
}

async function decide(item: ApprovalSummary, action: 'APPROVE' | 'REJECT') {
  await runAction(item.id, async () => {
    let reason: string | undefined
    if (action === 'REJECT') {
      const result = await ElMessageBox.prompt(
        '请填写拒绝原因。决定一经提交不能覆盖。',
        '拒绝审批',
        {
          confirmButtonText: '确认拒绝',
          cancelButtonText: '返回',
          inputValidator: (text) => Boolean(text?.trim()) || '必须填写拒绝原因',
        },
      )
      reason = result.value.trim()
    } else {
      await ElMessageBox.confirm(
        `确认通过审批单 ${item.approvalNo}？申请人与审批人必须分离。`,
        '通过审批',
        { confirmButtonText: '确认通过', cancelButtonText: '返回', type: 'warning' },
      )
    }
    await decideApproval(item.id, {
      shop_id: shopId.value!,
      version: item.version,
      action,
      reason,
    })
    ElMessage.success(action === 'APPROVE' ? '审批已通过' : '审批已拒绝')
    await loadActive()
  })
}

async function markRead(item: NotificationSummary) {
  if (item.status === 'READ') return
  await runAction(item.id, async () => {
    await markNotificationRead(item.id, item.version)
    await loadActive()
  })
}

async function markAllRead() {
  await runAction(-2, async () => {
    await markAllNotificationsRead()
    ElMessage.success('水位线之前的通知已全部标记为已读')
    await loadActive()
  })
}

async function runAction(id: number, action: () => Promise<void>) {
  actingId.value = id
  error.value = ''
  try {
    await action()
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') {
      error.value = reason instanceof Error ? reason.message : '操作失败，请刷新后重试'
    }
  } finally {
    actingId.value = undefined
  }
}

function money(value: unknown) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
  }).format(Number(value || 0))
}

function dateTime(value: unknown) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(String(value)))
}

function statusLabel(value: string) {
  return {
    DRAFT: '草稿',
    ACTIVE: '生效中',
    RETIRED: '已停用',
    PENDING: '待处理',
    FROZEN: '已冻结',
    SETTLED: '已结算',
    REVERSED: '已冲正',
    CALCULATED: '已计算',
    CONFIRMED: '已确认',
    PAID: '已支付',
    CLOSED: '已关闭',
    VOIDED: '已作废',
    OPEN: '待受理',
    TRIAGED: '已分派',
    PROCESSING: '处理中',
    WAITING_CUSTOMER: '等待顾客',
    RESOLVED: '已解决',
    REJECTED: '已拒绝',
    REOPENED: '已重开',
    APPROVED: '已通过',
    CANCELLED: '已取消',
    EXPIRED: '已过期',
    UNREAD: '未读',
    READ: '已读',
  }[value] ?? value
}
</script>

<template>
  <section class="operations-page">
    <header class="page-header">
      <div>
        <span class="page-kicker">M5 经营协同</span>
        <h1>提成、售后与审批</h1>
        <p>在同一处跟进规则、不可变流水、职责分离审批和本人通知。</p>
      </div>
      <div class="header-controls">
        <el-select
          v-if="activeTab !== 'notifications'"
          v-model="shopId"
          aria-label="选择门店"
          placeholder="选择门店"
          style="width: 220px"
        >
          <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
        </el-select>
        <el-button :loading="loading" @click="loadActive">刷新</el-button>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />

    <el-tabs v-model="activeTab" class="operations-tabs" @tab-change="loadActive">
      <el-tab-pane v-for="tab in tabs" :key="tab.key" :name="tab.key">
        <template #label>
          <span>{{ tab.label }}</span>
          <span v-if="tab.key === 'notifications' && unreadCount" class="unread-badge">
            {{ unreadCount }}
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <div v-if="loading" class="loading-list" aria-label="正在加载经营协同数据">
      <el-skeleton v-for="index in 4" :key="index" animated :rows="2" />
    </div>

    <template v-else>
      <div v-if="activeTab === 'rules'" class="data-list">
        <article v-for="rule in rules" :key="rule.id" class="data-row">
          <div class="row-main">
            <div>
              <div class="title-line">
                <strong>{{ rule.ruleName }}</strong>
                <el-tag size="small" effect="plain">{{ statusLabel(rule.status) }}</el-tag>
              </div>
              <p>{{ rule.ruleCode }} · {{ rule.sourceType }} · 版本 {{ rule.versionNo }}</p>
            </div>
            <div class="numeric">
              <strong>{{ Number(rule.rateValue || 0) ? `${Number(rule.rateValue) * 100}%` : money(rule.fixedAmount) }}</strong>
              <span>提成参数</span>
            </div>
          </div>
          <div class="row-actions">
            <el-button
              v-if="rule.status === 'DRAFT' && can('commission:rule:manage')"
              type="primary"
              plain
              :loading="actingId === rule.id"
              @click="changeRule(rule, 'publish')"
            >
              发布
            </el-button>
            <el-button
              v-if="rule.status === 'ACTIVE' && can('commission:rule:manage')"
              :loading="actingId === rule.id"
              @click="changeRule(rule, 'retire')"
            >
              停用
            </el-button>
          </div>
        </article>
        <el-empty v-if="!rules.length" description="当前门店还没有提成规则" />
      </div>

      <div v-if="activeTab === 'entries'">
        <div v-if="adjustmentEntry" class="inline-editor">
          <div>
            <strong>调整 {{ adjustmentEntry.entryNo }}</strong>
            <span>仅提交调整申请；审批通过后追加新流水，不覆盖原流水。</span>
          </div>
          <el-input-number v-model="adjustmentAmount" :precision="2" :step="10" />
          <el-input v-model="adjustmentReason" maxlength="500" placeholder="调整原因" />
          <el-button type="primary" :loading="actingId === adjustmentEntry.id" @click="submitAdjustment">
            提交审批
          </el-button>
          <el-button @click="adjustmentEntry = undefined">取消</el-button>
        </div>
        <div class="data-list">
          <article v-for="entry in entries" :key="entry.id" class="data-row">
            <div class="row-main">
              <div>
                <div class="title-line">
                  <strong>{{ entry.staffName }}</strong>
                  <el-tag size="small" effect="plain">{{ statusLabel(entry.status) }}</el-tag>
                </div>
                <p>{{ entry.entryNo }} · {{ entry.businessNo }} · {{ dateTime(entry.createdAt) }}</p>
              </div>
              <div class="numeric">
                <strong :class="{ negative: Number(entry.amount) < 0 }">{{ money(entry.amount) }}</strong>
                <span>{{ entry.entryType }}</span>
              </div>
            </div>
            <div class="row-actions">
              <el-button
                v-if="entry.status === 'PENDING' && can('commission:entry:manage')"
                :loading="actingId === entry.id"
                @click="changeEntry(entry, 'freeze')"
              >
                冻结
              </el-button>
              <el-button
                v-if="entry.status === 'FROZEN' && can('commission:entry:manage')"
                :loading="actingId === entry.id"
                @click="changeEntry(entry, 'unfreeze')"
              >
                解冻
              </el-button>
              <el-button
                v-if="can('commission:entry:adjust') && !['REVERSED', 'SETTLED'].includes(entry.status)"
                type="primary"
                plain
                @click="openAdjustment(entry)"
              >
                申请调整
              </el-button>
            </div>
          </article>
          <el-empty v-if="!entries.length" description="还没有提成流水；支付与服务完成后由事件自动入账" />
        </div>
      </div>

      <div v-if="activeTab === 'settlements'">
        <div v-if="can('commission:settlement:manage')" class="inline-editor settlement-editor">
          <div>
            <strong>新建结算周期</strong>
            <span>创建后先计算，再由不同职责人员确认与标记支付。</span>
          </div>
          <el-date-picker v-model="settlementForm.periodStart" value-format="YYYY-MM-DD" type="date" placeholder="开始日期" />
          <el-date-picker v-model="settlementForm.periodEnd" value-format="YYYY-MM-DD" type="date" placeholder="结束日期" />
          <el-button type="primary" :loading="actingId === -1" @click="createSettlement">创建批次</el-button>
        </div>
        <div class="data-list">
          <article v-for="batch in settlements" :key="batch.id" class="data-row">
            <div class="row-main">
              <div>
                <div class="title-line">
                  <strong>{{ batch.settlementNo }}</strong>
                  <el-tag size="small" effect="plain">{{ statusLabel(batch.status) }}</el-tag>
                </div>
                <p>{{ batch.periodStart }} 至 {{ batch.periodEnd }} · {{ batch.itemCount }} 条明细</p>
              </div>
              <div class="numeric">
                <strong>{{ money(batch.totalAmount) }}</strong>
                <span>结算金额</span>
              </div>
            </div>
            <div class="row-actions">
              <el-button
                v-if="['DRAFT', 'CALCULATED', 'CONFIRMED', 'PAID'].includes(batch.status)"
                type="primary"
                plain
                :loading="actingId === batch.id"
                @click="advanceSettlement(batch)"
              >
                继续处理
              </el-button>
            </div>
          </article>
          <el-empty v-if="!settlements.length" description="当前周期还没有结算批次" />
        </div>
      </div>

      <div v-if="activeTab === 'after-sales'" class="data-list">
        <article v-for="item in afterSales" :key="item.id" class="data-row">
          <div class="row-main">
            <div>
              <div class="title-line">
                <strong>{{ item.caseNo }}</strong>
                <el-tag size="small" effect="plain">{{ statusLabel(item.status) }}</el-tag>
                <el-tag v-if="item.priority === 'URGENT'" size="small" type="danger">紧急</el-tag>
              </div>
              <p class="safe-summary">{{ item.summary }}</p>
              <span class="meta-line">{{ item.category }} · {{ dateTime(item.updatedAt) }}</span>
            </div>
          </div>
          <div class="row-actions">
            <el-button
              v-if="nextAfterSaleStatus(item) && can('aftersale:manage')"
              type="primary"
              plain
              :loading="actingId === item.id"
              @click="advanceAfterSale(item)"
            >
              更新为{{ statusLabel(nextAfterSaleStatus(item)!) }}
            </el-button>
          </div>
        </article>
        <el-empty v-if="!afterSales.length" description="当前门店没有售后工单" />
      </div>

      <div v-if="activeTab === 'approvals'" class="data-list">
        <article v-for="item in approvals" :key="item.id" class="data-row">
          <div class="row-main">
            <div>
              <div class="title-line">
                <strong>{{ item.approvalNo }}</strong>
                <el-tag size="small" effect="plain">{{ statusLabel(item.status) }}</el-tag>
              </div>
              <p class="safe-summary">{{ item.safeSummary }}</p>
              <span class="meta-line">{{ item.approvalType }} · {{ dateTime(item.createdAt) }}</span>
            </div>
          </div>
          <div v-if="item.status === 'PENDING' && can('approval:decide')" class="row-actions">
            <el-button type="primary" plain :loading="actingId === item.id" @click="decide(item, 'APPROVE')">
              通过
            </el-button>
            <el-button :loading="actingId === item.id" @click="decide(item, 'REJECT')">拒绝</el-button>
          </div>
        </article>
        <el-empty v-if="!approvals.length" description="当前没有待查看的审批" />
      </div>

      <div v-if="activeTab === 'notifications'">
        <div class="notification-toolbar">
          <p>{{ unreadCount ? `有 ${unreadCount} 条未读通知` : '通知已全部阅读' }}</p>
          <el-button v-if="unreadCount" :loading="actingId === -2" @click="markAllRead">全部已读</el-button>
        </div>
        <div class="data-list">
          <button
            v-for="item in notifications"
            :key="item.id"
            :class="['notification-row', { unread: item.status === 'UNREAD' }]"
            type="button"
            :disabled="actingId === item.id"
            @click="markRead(item)"
          >
            <span class="notification-dot" aria-hidden="true" />
            <span>
              <strong>{{ item.title }}</strong>
              <span>{{ item.safeSummary }}</span>
              <small>
                {{ dateTime(item.createdAt) }} · 站内已送达 · 外部通道{{ item.externalStatus === 'UNAVAILABLE' ? '不可用' : '未请求' }}
              </small>
            </span>
          </button>
          <el-empty v-if="!notifications.length" description="没有新的业务通知" />
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.operations-page {
  display: grid;
  gap: 20px;
}

.page-header,
.header-controls,
.row-main,
.title-line,
.row-actions,
.notification-toolbar,
.inline-editor {
  display: flex;
  align-items: center;
}

.page-header {
  justify-content: space-between;
  gap: 24px;
}

.page-header h1 {
  margin: 4px 0 7px;
  font-size: 28px;
  letter-spacing: -0.025em;
}

.page-header p,
.inline-editor span,
.data-row p,
.meta-line,
.numeric span,
.notification-toolbar p {
  color: var(--oc-text-muted);
}

.page-header p,
.notification-toolbar p {
  margin: 0;
}

.page-kicker {
  color: var(--oc-accent);
  font-size: 13px;
  font-weight: 700;
}

.header-controls {
  gap: 10px;
}

.operations-tabs {
  border-bottom: 1px solid var(--oc-border);
}

.unread-badge {
  min-width: 20px;
  height: 20px;
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 999px;
  color: white;
  background: var(--oc-accent);
  font-size: 11px;
  line-height: 20px;
  text-align: center;
}

.loading-list,
.data-list {
  display: grid;
}

.loading-list {
  gap: 16px;
}

.data-list {
  border-top: 1px solid var(--oc-border);
}

.data-row {
  padding: 18px 2px;
  border-bottom: 1px solid var(--oc-border);
}

.row-main {
  justify-content: space-between;
  gap: 24px;
}

.title-line {
  flex-wrap: wrap;
  gap: 8px;
}

.data-row p {
  max-width: 72ch;
  margin: 7px 0 0;
  line-height: 1.55;
}

.safe-summary {
  color: var(--oc-text) !important;
}

.meta-line {
  display: block;
  margin-top: 7px;
  font-size: 12px;
}

.numeric {
  display: grid;
  flex: 0 0 auto;
  gap: 3px;
  text-align: right;
}

.numeric strong {
  font-size: 20px;
}

.numeric strong.negative {
  color: var(--oc-danger);
}

.row-actions {
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.inline-editor {
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px;
  background: var(--oc-surface-2);
  border-radius: 10px;
}

.inline-editor > div:first-child {
  display: grid;
  flex: 1 1 260px;
  gap: 4px;
}

.inline-editor .el-input {
  width: min(360px, 100%);
}

.settlement-editor {
  margin-bottom: 18px;
}

.notification-toolbar {
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
}

.notification-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  padding: 17px 2px;
  border: 0;
  border-bottom: 1px solid var(--oc-border);
  color: inherit;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.notification-row:focus-visible {
  outline: 3px solid var(--oc-focus);
  outline-offset: 2px;
}

.notification-row > span:last-child {
  display: grid;
  gap: 5px;
}

.notification-row span span,
.notification-row small {
  color: var(--oc-text-muted);
}

.notification-dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--oc-border-strong);
}

.notification-row.unread .notification-dot {
  background: var(--oc-accent);
}

.notification-row.unread strong {
  color: var(--oc-accent-strong);
}

@media (max-width: 760px) {
  .page-header,
  .row-main {
    align-items: stretch;
    flex-direction: column;
  }

  .header-controls {
    align-items: stretch;
  }

  .numeric {
    text-align: left;
  }

  .row-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}

@media (prefers-reduced-motion: reduce) {
  .notification-row {
    scroll-behavior: auto;
  }
}
</style>
