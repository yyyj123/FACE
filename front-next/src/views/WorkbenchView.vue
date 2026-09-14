<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import { useAuthStore } from '../stores/auth'
import type {
  ApprovalItem,
  CommissionEntry,
  NotificationItem,
  TechnicianCommissionSummary,
  TrainingRecord,
} from '../types/domain'

const auth = useAuthStore()
const summary = ref<TechnicianCommissionSummary>()
const entries = ref<CommissionEntry[]>([])
const approvals = ref<ApprovalItem[]>([])
const notifications = ref<NotificationItem[]>([])
const unreadCount = ref(0)
const trainingRecords = ref<TrainingRecord[]>([])
const trainingBusyId = ref<number>()
const evidenceDrafts = reactive<Record<number, string>>({})
const loading = ref(true)
const error = ref('')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const shopId = auth.session!.shopId
    const [summaryData, entryPage, approvalPage, notificationPage, trainingData] = await Promise.all([
      api.technicianCommissionSummary(shopId),
      api.commissionEntries(shopId),
      api.approvals(shopId),
      api.notifications('UNREAD'),
      api.trainingRecords(),
    ])
    summary.value = summaryData
    entries.value = entryPage.records
    approvals.value = approvalPage.records
    notifications.value = notificationPage.records.slice(0, 4)
    unreadCount.value = notificationPage.unreadCount
    trainingRecords.value = trainingData
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '技师工作台加载失败'
  } finally {
    loading.value = false
  }
}

function money(value: unknown) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
  }).format(Number(value || 0))
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function statusLabel(status: string) {
  return {
    PENDING: '待结算',
    FROZEN: '已冻结',
    SETTLED: '已结算',
    REVERSED: '已冲正',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    CANCELLED: '已取消',
    EXPIRED: '已过期',
    ASSIGNED: '待开始',
    IN_PROGRESS: '进行中',
    SUBMITTED: '待验证',
    PASSED: '已通过',
    FAILED: '未通过',
  }[status] ?? status
}

async function trainingAction(record: TrainingRecord, action: 'start' | 'submit') {
  if (action === 'submit' && !evidenceDrafts[record.id]?.trim()) {
    error.value = '提交培训前请填写不包含客户信息的完成说明'
    return
  }
  trainingBusyId.value = record.id
  error.value = ''
  try {
    await api.trainingAction(record.id, action, record.version, evidenceDrafts[record.id]?.trim())
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '培训状态更新失败'
  } finally {
    trainingBusyId.value = undefined
  }
}
</script>

<template>
  <div class="workbench-page page-container page-section">
    <header class="page-heading">
      <div>
        <span class="page-context">技师工作台</span>
        <h1>我的提成与协同事项</h1>
        <p>这里只展示本人提成流水、本人发起的审批和与本人相关的业务通知。</p>
      </div>
      <button class="button button-secondary button-small" type="button" :disabled="loading" @click="load">
        刷新
      </button>
    </header>

    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>

    <div v-if="loading" class="workbench-skeletons" aria-label="正在加载技师工作台">
      <span v-for="index in 4" :key="index" class="skeleton" />
    </div>

    <template v-else>
      <section class="summary-strip" aria-label="本人提成摘要">
        <div>
          <span>待结算</span>
          <strong>{{ money(summary?.pendingAmount) }}</strong>
        </div>
        <div>
          <span>冻结中</span>
          <strong>{{ money(summary?.frozenAmount) }}</strong>
        </div>
        <div>
          <span>累计结算</span>
          <strong>{{ money(summary?.settledAmount) }}</strong>
        </div>
        <div>
          <span>已支付</span>
          <strong>{{ money(summary?.paidAmount) }}</strong>
        </div>
      </section>

      <section class="work-section" aria-labelledby="entries-heading">
        <div class="section-heading">
          <div>
            <h2 id="entries-heading">提成流水</h2>
            <p>退款、调整和结算都以追加记录体现，不会修改原始入账。</p>
          </div>
        </div>
        <div v-if="entries.length" class="work-list">
          <article v-for="entry in entries" :key="entry.id" class="work-row">
            <div>
              <strong>{{ entry.entryNo }}</strong>
              <span>{{ entry.businessNo }} · {{ formatDate(entry.createdAt) }}</span>
            </div>
            <span class="status-text">{{ statusLabel(entry.status) }}</span>
            <strong :class="{ negative: Number(entry.amount) < 0 }">{{ money(entry.amount) }}</strong>
          </article>
        </div>
        <EmptyState
          v-else
          title="还没有提成流水"
          description="服务完成并满足规则后，系统会自动生成本人提成记录。"
        />
      </section>

      <section class="work-section" aria-labelledby="approvals-heading">
        <div class="section-heading">
          <div>
            <h2 id="approvals-heading">我的审批申请</h2>
            <p>申请人不能审批自己的申请，最终决定会保留不可覆盖记录。</p>
          </div>
        </div>
        <div v-if="approvals.length" class="work-list">
          <article v-for="item in approvals" :key="item.id" class="approval-row">
            <div>
              <strong>{{ item.approvalNo }}</strong>
              <span>{{ item.safeSummary }}</span>
            </div>
            <span class="status-text">{{ statusLabel(item.status) }}</span>
          </article>
        </div>
        <EmptyState v-else title="没有审批申请" description="需要审批的提成调整会显示在这里。" />
      </section>

      <section class="work-section" aria-labelledby="notifications-heading">
        <div class="section-heading">
          <div>
            <h2 id="notifications-heading">未读通知</h2>
            <p>{{ unreadCount ? `还有 ${unreadCount} 条未读消息` : '目前没有未读消息' }}</p>
          </div>
          <router-link class="text-button" to="/notifications">查看全部</router-link>
        </div>
        <div v-if="notifications.length" class="work-list">
          <router-link
            v-for="item in notifications"
            :key="item.id"
            class="notification-preview"
            to="/notifications"
          >
            <span aria-hidden="true" />
            <div>
              <strong>{{ item.title }}</strong>
              <small>{{ item.safeSummary }}</small>
            </div>
          </router-link>
        </div>
      </section>

      <section class="work-section" aria-labelledby="training-heading">
        <div class="section-heading">
          <div>
            <h2 id="training-heading">我的培训与资格</h2>
            <p>课程版本、提交记录、验证结果和证书有效期都会保留；只能操作本人培训。</p>
          </div>
        </div>
        <div v-if="trainingRecords.length" class="work-list">
          <article v-for="record in trainingRecords" :key="record.id" class="training-row">
            <div class="training-copy">
              <strong>{{ record.courseTitle }} · V{{ record.courseRevision }}</strong>
              <span>{{ record.courseSummary }}</span>
              <small>
                {{ record.recordNo }} · 通过分 {{ record.passScore }}
                <template v-if="record.validUntil"> · 有效至 {{ record.validUntil }}</template>
              </small>
            </div>
            <span class="status-text">{{ statusLabel(record.status) }}</span>
            <div class="training-action">
              <button
                v-if="record.status === 'ASSIGNED'"
                class="button button-secondary button-small"
                type="button"
                :disabled="trainingBusyId === record.id"
                @click="trainingAction(record, 'start')"
              >
                开始培训
              </button>
              <template v-if="record.status === 'IN_PROGRESS'">
                <label :for="`training-evidence-${record.id}`">完成说明</label>
                <textarea
                  :id="`training-evidence-${record.id}`"
                  v-model="evidenceDrafts[record.id]"
                  rows="2"
                  maxlength="500"
                  placeholder="只写培训完成情况，不填写客户、健康或支付信息"
                />
                <button
                  class="button button-primary button-small"
                  type="button"
                  :disabled="trainingBusyId === record.id"
                  @click="trainingAction(record, 'submit')"
                >
                  提交验证
                </button>
              </template>
              <span v-if="record.certificateNo" class="certificate-number">证书 {{ record.certificateNo }}</span>
            </div>
          </article>
        </div>
        <EmptyState
          v-else
          title="还没有培训任务"
          description="店长分配已发布课程后，培训任务会显示在这里。"
        />
      </section>
    </template>
  </div>
</template>

<style scoped>
.workbench-page {
  padding-bottom: 92px;
}

.page-context {
  color: var(--copper);
  font-size: 13px;
  font-weight: 700;
}

.workbench-skeletons {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.workbench-skeletons .skeleton {
  min-height: 110px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-top: 1px solid var(--line-strong);
  border-bottom: 1px solid var(--line-strong);
}

.summary-strip > div {
  display: grid;
  gap: 8px;
  padding: 22px 18px;
  border-right: 1px solid var(--line);
}

.summary-strip > div:last-child {
  border-right: 0;
}

.summary-strip span {
  color: var(--text-muted);
  font-size: 13px;
}

.summary-strip strong {
  font-size: 20px;
}

.work-section {
  margin-top: 42px;
}

.work-section h2 {
  margin: 0;
}

.work-list {
  border-top: 1px solid var(--line-strong);
}

.work-row,
.approval-row,
.notification-preview {
  display: grid;
  align-items: center;
  gap: 18px;
  padding: 17px 2px;
  border-bottom: 1px solid var(--line);
}

.work-row {
  grid-template-columns: minmax(0, 1fr) 100px 130px;
}

.approval-row {
  grid-template-columns: minmax(0, 1fr) 100px;
}

.work-row > div,
.approval-row > div {
  display: grid;
  gap: 5px;
}

.work-row span,
.approval-row span,
.notification-preview small {
  color: var(--text-muted);
}

.work-row > strong {
  text-align: right;
}

.negative {
  color: var(--danger);
}

.status-text {
  font-size: 13px;
}

.notification-preview {
  grid-template-columns: 8px minmax(0, 1fr);
}

.notification-preview > span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--rose);
}

.notification-preview > div {
  display: grid;
  gap: 5px;
}

.training-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 90px minmax(190px, 280px);
  align-items: start;
  gap: 20px;
  padding: 18px 2px;
  border-bottom: 1px solid var(--line);
}

.training-copy,
.training-action {
  display: grid;
  gap: 7px;
}

.training-copy span,
.training-copy small,
.certificate-number,
.training-action label {
  color: var(--text-muted);
}

.training-action label { font-size: 12px; font-weight: 700; }
.training-action textarea {
  width: 100%;
  box-sizing: border-box;
  resize: vertical;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--surface);
  color: var(--text);
  font: inherit;
}
.training-action textarea:focus-visible { outline: 3px solid var(--focus); outline-offset: 2px; }
.certificate-number { font-size: 12px; overflow-wrap: anywhere; }

@media (max-width: 700px) {
  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-strip > div:nth-child(2) {
    border-right: 0;
  }

  .summary-strip > div:nth-child(-n + 2) {
    border-bottom: 1px solid var(--line);
  }

  .work-row,
  .approval-row,
  .training-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .work-row > strong {
    text-align: left;
  }
}
</style>
