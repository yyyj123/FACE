<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import { useAuthStore } from '../stores/auth'
import type { AfterSaleCase, Appointment } from '../types/domain'

const auth = useAuthStore()
const cases = ref<AfterSaleCase[]>([])
const appointments = ref<Appointment[]>([])
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const success = ref('')
const form = ref({
  appointmentId: undefined as number | undefined,
  category: 'SERVICE_QUALITY',
  priority: 'NORMAL',
  summary: '',
})

const selectedAppointment = computed(() =>
  appointments.value.find((item) => item.id === form.value.appointmentId),
)

onMounted(load)

async function load() {
  if (!auth.session?.memberId) {
    error.value = '当前账号未绑定会员资料'
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [casePage, appointmentList] = await Promise.all([
      api.afterSaleCases(auth.session.shopId),
      api.appointments(),
    ])
    cases.value = casePage.records
    appointments.value = appointmentList
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '售后记录加载失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!auth.session?.memberId || !form.value.summary.trim()) {
    error.value = '请填写需要门店处理的问题'
    return
  }
  submitting.value = true
  error.value = ''
  success.value = ''
  try {
    await api.createAfterSaleCase({
      shop_id: auth.session.shopId,
      member_id: auth.session.memberId,
      order_id: selectedAppointment.value?.orderId ?? undefined,
      service_record_id: selectedAppointment.value?.serviceRecordId ?? undefined,
      category: form.value.category,
      priority: form.value.priority,
      summary: form.value.summary.trim(),
    })
    form.value = {
      appointmentId: undefined,
      category: 'SERVICE_QUALITY',
      priority: 'NORMAL',
      summary: '',
    }
    success.value = '售后工单已提交，门店处理进度会同步到消息中心。'
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '售后工单提交失败'
  } finally {
    submitting.value = false
  }
}

async function reopen(item: AfterSaleCase) {
  const reason = window.prompt('请填写重开原因，避免填写健康隐私或支付凭据。')
  if (!reason?.trim()) return
  error.value = ''
  try {
    await api.reopenAfterSaleCase(item.id, {
      shop_id: auth.session!.shopId,
      version: item.version,
      reason: reason.trim(),
    })
    success.value = '工单已重开。'
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '工单重开失败'
  }
}

function statusLabel(status: string) {
  return {
    OPEN: '待受理',
    TRIAGED: '已分派',
    PROCESSING: '处理中',
    WAITING_CUSTOMER: '等待回复',
    RESOLVED: '已解决',
    CLOSED: '已关闭',
    REJECTED: '已驳回',
    REOPENED: '已重开',
  }[status] ?? status
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="after-sales-page page-container page-section">
    <header class="page-heading">
      <div>
        <span class="page-context">会员售后</span>
        <h1>问题反馈与处理进度</h1>
        <p>提交服务问题后，可持续查看受理、处理和解决状态；退款仍走独立审批流程。</p>
      </div>
      <router-link class="button button-secondary button-small" to="/notifications">查看消息</router-link>
    </header>

    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
    <p v-if="success" class="notice notice-success" role="status">{{ success }}</p>

    <section class="submission-panel" aria-labelledby="submit-heading">
      <div>
        <h2 id="submit-heading">提交新问题</h2>
        <p>说明会进入售后工单，请不要填写身份证、健康病史或支付凭据。</p>
      </div>
      <form @submit.prevent="submit">
        <label>
          关联预约（可选）
          <select v-model="form.appointmentId">
            <option :value="undefined">不关联具体预约</option>
            <option v-for="item in appointments" :key="item.id" :value="item.id">
              {{ formatDate(item.startAt) }} · {{ item.serviceNames || item.appointmentNo }}
            </option>
          </select>
        </label>
        <label>
          问题类型
          <select v-model="form.category">
            <option value="SERVICE_QUALITY">服务质量</option>
            <option value="REFUND">退款咨询</option>
            <option value="PACKAGE">套餐问题</option>
            <option value="ACCOUNT">会员账户</option>
            <option value="PRODUCT">产品问题</option>
            <option value="OTHER">其他</option>
          </select>
        </label>
        <label>
          紧急程度
          <select v-model="form.priority">
            <option value="NORMAL">普通</option>
            <option value="HIGH">较急</option>
            <option value="URGENT">紧急</option>
            <option value="LOW">不着急</option>
          </select>
        </label>
        <label class="summary-field">
          问题说明
          <textarea
            v-model="form.summary"
            maxlength="500"
            rows="4"
            placeholder="请说明发生了什么，以及希望门店如何协助"
          />
          <small>{{ form.summary.length }}/500</small>
        </label>
        <button class="button button-primary" type="submit" :disabled="submitting">
          {{ submitting ? '正在提交…' : '提交售后工单' }}
        </button>
      </form>
    </section>

    <section class="history-section" aria-labelledby="history-heading">
      <div class="section-heading">
        <div>
          <h2 id="history-heading">处理记录</h2>
          <p>状态变化由门店系统记录，不会覆盖历史处理轨迹。</p>
        </div>
        <button class="text-button" type="button" :disabled="loading" @click="load">刷新</button>
      </div>

      <div v-if="loading" class="case-skeletons" aria-label="正在加载售后记录">
        <span v-for="index in 3" :key="index" class="skeleton" />
      </div>
      <div v-else-if="cases.length" class="case-list">
        <article v-for="item in cases" :key="item.id" class="case-row">
          <div class="case-status">
            <span aria-hidden="true" />
            <strong>{{ statusLabel(item.status) }}</strong>
          </div>
          <div class="case-copy">
            <div>
              <strong>{{ item.caseNo }}</strong>
              <span>{{ item.category }} · {{ formatDate(item.updatedAt) }}</span>
            </div>
            <p>{{ item.summary }}</p>
            <button
              v-if="item.status === 'RESOLVED'"
              class="text-button"
              type="button"
              @click="reopen(item)"
            >
              问题仍未解决，申请重开
            </button>
          </div>
        </article>
      </div>
      <EmptyState
        v-else
        title="还没有售后记录"
        description="如果护理或套餐使用遇到问题，可以在上方提交，门店会持续更新处理进度。"
      />
    </section>
  </div>
</template>

<style scoped>
.after-sales-page {
  padding-bottom: 92px;
}

.page-context {
  color: var(--copper);
  font-size: 13px;
  font-weight: 700;
}

.submission-panel {
  display: grid;
  grid-template-columns: minmax(220px, 0.7fr) minmax(0, 1.3fr);
  gap: 38px;
  padding: 28px;
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  background: var(--surface);
}

.submission-panel h2,
.history-section h2 {
  margin: 0;
}

.submission-panel p {
  max-width: 42ch;
  color: var(--text-soft);
  line-height: 1.7;
}

.submission-panel form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.submission-panel label {
  display: grid;
  gap: 7px;
  color: var(--text-soft);
  font-size: 13px;
  font-weight: 700;
}

.submission-panel select,
.submission-panel textarea {
  width: 100%;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  color: var(--text);
  background: var(--surface);
}

.submission-panel select {
  min-height: 44px;
  padding: 0 12px;
}

.submission-panel textarea {
  resize: vertical;
  min-height: 112px;
  padding: 12px;
  line-height: 1.6;
}

.summary-field {
  grid-column: 1 / -1;
}

.summary-field small {
  justify-self: end;
  color: var(--text-muted);
  font-weight: 400;
}

.submission-panel .button {
  justify-self: start;
}

.history-section {
  margin-top: 46px;
}

.case-skeletons {
  display: grid;
  gap: 12px;
}

.case-skeletons .skeleton {
  min-height: 112px;
}

.case-list {
  border-top: 1px solid var(--line-strong);
}

.case-row {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 24px;
  padding: 22px 0;
  border-bottom: 1px solid var(--line);
}

.case-status {
  display: flex;
  align-items: center;
  gap: 9px;
  align-self: start;
}

.case-status span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--rose);
}

.case-copy {
  display: grid;
  gap: 10px;
}

.case-copy > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.case-copy > div span {
  color: var(--text-muted);
  font-size: 12px;
}

.case-copy p {
  max-width: 72ch;
  margin: 0;
  color: var(--text-soft);
  line-height: 1.65;
}

@media (max-width: 760px) {
  .page-heading,
  .submission-panel {
    align-items: stretch;
    grid-template-columns: 1fr;
  }

  .page-heading {
    flex-direction: column;
  }

  .submission-panel form {
    grid-template-columns: 1fr;
  }

  .summary-field {
    grid-column: auto;
  }

  .case-row {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .case-copy > div {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
