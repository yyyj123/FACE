<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Calendar, Edit, Phone, Shop, UserFilled } from '@element-plus/icons-vue'
import { api } from '../api/client'
import AppointmentCard from '../components/AppointmentCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { useAuthStore } from '../stores/auth'
import type {
  Appointment,
  ClientDashboard,
  ClientProfile,
  MarketingConsentSettings,
} from '../types/domain'
import { handleMediaError, mediaUrl } from '../utils/format'

const auth = useAuthStore()
const dashboard = ref<ClientDashboard | null>(null)
const loading = ref(true)
const editing = ref(false)
const saving = ref(false)
const busyId = ref<number | null>(null)
const error = ref('')
const success = ref('')
const consentSettings = ref<MarketingConsentSettings | null>(null)
const consentBusyChannel = ref('')
const form = reactive({
  name: '',
  phone: '',
  gender: '',
  avatarUrl: '',
  bio: '',
})

const isTechnician = computed(() => auth.isTechnician)
const profile = computed(() => dashboard.value?.profile ?? auth.profile)
const recentAppointments = computed(() => dashboard.value?.appointments?.slice(0, 5) ?? [])
const summaryCards = computed(() => {
  const values = dashboard.value?.summary ?? {}
  if (isTechnician.value) {
    return [
      { label: '今日预约', value: values.todayAppointments ?? 0 },
      { label: '今日完成', value: values.todayCompleted ?? 0 },
      { label: '本月服务', value: values.monthAppointments ?? 0 },
      { label: '累计顾客', value: values.servedMembers ?? 0 },
    ]
  }
  return [
    { label: '全部预约', value: values.allAppointments ?? 0 },
    { label: '已完成', value: values.completed ?? 0 },
    { label: '待到店', value: values.upcoming ?? 0 },
    { label: '会员积分', value: values.points ?? 0 },
  ]
})

function applyProfile(value: ClientProfile | null | undefined) {
  if (!value) return
  form.name = value.name || ''
  form.phone = value.phone || ''
  form.gender = value.gender || ''
  form.avatarUrl = value.avatarUrl || ''
  form.bio = value.bio || ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    dashboard.value = await api.dashboard()
    auth.profile = dashboard.value.profile
    applyProfile(dashboard.value.profile)
    if (!isTechnician.value) {
      consentSettings.value = await api.marketingConsents()
    }
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '个人中心加载失败'
  } finally {
    loading.value = false
  }
}

async function changeConsent(channel: string, currentStatus: 'GRANTED' | 'REVOKED', version: number) {
  consentBusyChannel.value = channel
  error.value = ''
  success.value = ''
  const target = currentStatus === 'GRANTED' ? 'REVOKED' : 'GRANTED'
  try {
    await api.updateMarketingConsent(channel, target, version)
    consentSettings.value = await api.marketingConsents()
    success.value = target === 'GRANTED'
      ? '营销站内通知已开启。你可以随时在这里撤回。'
      : '营销同意已撤回；你不会进入之后新执行的营销活动。'
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '营销偏好更新失败'
  } finally {
    consentBusyChannel.value = ''
  }
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const updated = await api.updateMe({
      name: form.name,
      phone: form.phone,
      gender: form.gender,
      avatarUrl: form.avatarUrl,
      bio: form.bio,
    })
    auth.profile = updated
    if (dashboard.value) dashboard.value.profile = updated
    editing.value = false
    success.value = '个人资料已保存，并同步到门店员工/会员档案。'
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '资料保存失败'
  } finally {
    saving.value = false
  }
}

async function updateStatus(appointment: Appointment, status: string) {
  busyId.value = appointment.id
  error.value = ''
  try {
    await api.updateAppointmentStatus(appointment.id, status, appointment.version)
    await load()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '状态更新失败'
  } finally {
    busyId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container profile-page">
    <div v-if="loading" class="profile-skeleton skeleton" />
    <template v-else-if="profile">
      <section class="profile-banner panel">
        <img
          class="profile-avatar"
          :src="mediaUrl(profile.avatarUrl, profile.accountId + 4)"
          :alt="profile.name"
          @error="handleMediaError($event, profile.accountId + 4)"
        />
        <div class="profile-identity">
          <span class="eyebrow">{{ isTechnician ? 'TECHNICIAN CENTER' : 'MEMBER CENTER' }}</span>
          <h1>{{ profile.name }}</h1>
          <p>{{ isTechnician ? (profile.levelName || profile.jobRole || '护理技师') : `会员编号 ${profile.memberNo || '—'}` }}</p>
        </div>
        <dl class="profile-contact">
          <div><dt><UserFilled />登录账号</dt><dd>{{ profile.username }}</dd></div>
          <div><dt><Phone />联系电话</dt><dd>{{ profile.phone || '未填写' }}</dd></div>
          <div><dt><Shop />所属门店</dt><dd>{{ profile.shopName }}</dd></div>
        </dl>
        <button class="button button-secondary button-small edit-button" type="button" @click="editing = true">
          <Edit />
          编辑资料
        </button>
      </section>

      <div v-if="error" class="notice profile-notice">{{ error }}</div>
      <div v-if="success" class="notice success profile-notice">{{ success }}</div>

      <section class="summary-grid" aria-label="个人服务统计">
        <article v-for="card in summaryCards" :key="card.label">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
        </article>
      </section>

      <section v-if="isTechnician && dashboard?.todaySchedule" class="profile-section">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">NEXT 7 DAYS</span>
            <h2>近 7 天排班</h2>
            <p>排班由管理端统一维护，这里只展示与你关联的班次。</p>
          </div>
        </div>
        <div v-if="dashboard.todaySchedule.length" class="schedule-grid">
          <article v-for="schedule in dashboard.todaySchedule" :key="schedule.id">
            <Calendar />
            <div>
              <strong>{{ schedule.scheduleDate }}</strong>
              <span>{{ schedule.startTime }}–{{ schedule.endTime }}</span>
            </div>
            <small>{{ schedule.scheduleType === 'WORK' ? '正常班' : schedule.scheduleType }}</small>
          </article>
        </div>
        <EmptyState v-else title="近 7 天暂无排班" description="请联系店长在管理端补充排班后再接收预约。" />
      </section>

      <section v-if="!isTechnician && consentSettings" class="profile-section consent-section">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">PRIVACY & PREFERENCES</span>
            <h2>营销信息偏好</h2>
            <p>默认关闭。授权仅由你本人操作，撤回后不会进入之后新执行的活动。</p>
          </div>
        </div>
        <div class="consent-disclosure" role="note">
          <strong>授权说明</strong>
          <p>{{ consentSettings.consentText }}</p>
        </div>
        <div class="consent-list">
          <article v-for="item in consentSettings.records" :key="item.channel">
            <div>
              <strong>{{ { IN_APP: '站内通知', SMS: '短信', EMAIL: '邮件', WECHAT: '微信' }[item.channel] }}</strong>
              <span v-if="item.available">通道可用 · 仅发送活动安全摘要</span>
              <span v-else>服务商尚未配置，无法发送</span>
            </div>
            <button
              class="consent-switch"
              :class="{ active: item.status === 'GRANTED' }"
              type="button"
              role="switch"
              :aria-checked="item.status === 'GRANTED'"
              :aria-label="`${item.channel}营销信息${item.status === 'GRANTED' ? '已开启' : '已关闭'}`"
              :disabled="!item.available || consentBusyChannel === item.channel"
              @click="changeConsent(item.channel, item.status, item.version)"
            >
              <span aria-hidden="true" />
              <b>{{ item.status === 'GRANTED' ? '已同意' : '未同意' }}</b>
            </button>
          </article>
        </div>
        <p class="consent-version">授权文本版本：{{ consentSettings.records[0]?.consentTextVersion }}</p>
      </section>

      <section class="profile-section">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">{{ isTechnician ? 'SERVICE QUEUE' : 'RECENT VISITS' }}</span>
            <h2>{{ isTechnician ? '最近分配给我的预约' : '最近预约' }}</h2>
          </div>
          <router-link class="button button-secondary button-small" to="/appointments">查看全部</router-link>
        </div>
        <div v-if="recentAppointments.length" class="appointment-list">
          <AppointmentCard
            v-for="appointment in recentAppointments"
            :key="appointment.id"
            :appointment="appointment"
            :technician="isTechnician"
            :busy="busyId === appointment.id"
            @status="updateStatus"
          />
        </div>
        <EmptyState
          v-else
          :title="isTechnician ? '还没有分配预约' : '还没有预约记录'"
          :description="isTechnician ? '新预约分配给你后会显示在这里。' : '完成首次预约后，可在这里跟踪状态。'"
          :action-label="isTechnician ? '' : '预约护理'"
          :to="isTechnician ? '' : '/booking'"
        />
      </section>
    </template>

    <div v-if="editing" class="dialog-backdrop" role="presentation" @click.self="editing = false">
      <section class="profile-dialog panel" role="dialog" aria-modal="true" aria-labelledby="profile-dialog-title">
        <div class="dialog-heading">
          <div>
            <span class="eyebrow">PROFILE</span>
            <h2 id="profile-dialog-title">编辑个人资料</h2>
          </div>
          <button type="button" aria-label="关闭" @click="editing = false">×</button>
        </div>
        <form class="form-grid" @submit.prevent="save">
          <div class="field">
            <label for="profile-name">姓名</label>
            <input id="profile-name" v-model="form.name" class="form-control" required />
          </div>
          <div class="field">
            <label for="profile-phone">联系电话</label>
            <input id="profile-phone" v-model="form.phone" class="form-control" required />
          </div>
          <div v-if="!isTechnician" class="field">
            <label for="profile-gender">性别</label>
            <select id="profile-gender" v-model="form.gender" class="form-control">
              <option value="">未填写</option>
              <option value="F">女</option>
              <option value="M">男</option>
              <option value="OTHER">其他</option>
            </select>
          </div>
          <div class="field" :class="{ full: isTechnician }">
            <label for="profile-avatar">头像地址</label>
            <input id="profile-avatar" v-model="form.avatarUrl" class="form-control" placeholder="可填写管理端上传后的图片地址" />
          </div>
          <div v-if="isTechnician" class="field full">
            <label for="profile-bio">个人简介</label>
            <textarea id="profile-bio" v-model="form.bio" class="form-control" maxlength="1000"></textarea>
          </div>
          <div class="dialog-actions full">
            <button class="button button-quiet" type="button" @click="editing = false">取消</button>
            <button class="button button-primary" type="submit" :disabled="saving">
              {{ saving ? '保存中…' : '保存资料' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  padding-top: 42px;
  padding-bottom: 76px;
}

.profile-skeleton {
  min-height: 650px;
}

.profile-banner {
  position: relative;
  display: grid;
  grid-template-columns: 112px 1fr 1.1fr;
  gap: 24px;
  align-items: center;
  min-height: 230px;
  padding: 34px;
  overflow: hidden;
}

.profile-banner::after {
  content: "";
  position: absolute;
  top: -100px;
  right: -80px;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  background: rgba(201, 106, 136, 0.08);
  filter: blur(2px);
}

.profile-avatar {
  width: 112px;
  height: 136px;
  border-radius: 8px;
  object-fit: cover;
}

.profile-identity h1 {
  margin: 8px 0 6px;
  font-size: 34px;
}

.profile-identity p {
  margin: 0;
  color: var(--text-muted);
}

.profile-contact {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 13px;
  margin: 0;
  padding: 20px 22px;
  border-left: 1px solid var(--line);
}

.profile-contact div {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 12px;
}

.profile-contact dt {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--text-muted);
  font-size: 12px;
}

.profile-contact svg {
  width: 14px;
}

.profile-contact dd {
  margin: 0;
  font-size: 13px;
}

.edit-button {
  position: absolute;
  z-index: 2;
  top: 18px;
  right: 18px;
}

.edit-button svg {
  width: 14px;
}

.profile-notice {
  margin-top: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin: 22px 0 48px;
}

.summary-grid article {
  display: grid;
  gap: 8px;
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
}

.summary-grid span {
  color: var(--text-muted);
  font-size: 12px;
}

.summary-grid strong {
  font-size: 28px;
}

.profile-section {
  margin-top: 45px;
}

.consent-section {
  padding: 28px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
}

.consent-disclosure {
  padding: 16px 18px;
  margin: 20px 0 12px;
  border: 1px solid var(--line);
  border-top-color: var(--rose);
  background: var(--surface-strong);
}

.consent-disclosure p {
  margin: 7px 0 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.consent-list {
  border-top: 1px solid var(--line);
}

.consent-list article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
}

.consent-list article > div {
  display: grid;
  gap: 5px;
}

.consent-list span,
.consent-version {
  color: var(--text-muted);
  font-size: 12px;
}

.consent-switch {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  min-width: 104px;
  padding: 7px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  color: var(--text-muted);
  background: var(--surface-soft);
  cursor: pointer;
}

.consent-switch > span {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--surface);
  box-shadow: 0 1px 4px rgba(37, 27, 33, 0.18);
}

.consent-switch.active {
  border-color: var(--rose);
  color: var(--surface);
  background: var(--rose);
}

.consent-switch:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.consent-switch:focus-visible {
  outline: 3px solid var(--focus);
  outline-offset: 2px;
}

.consent-version {
  margin: 12px 0 0;
}

.compact-heading h2 {
  font-size: 30px;
}

.schedule-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.schedule-grid article {
  display: grid;
  grid-template-columns: 38px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 17px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
}

.schedule-grid svg {
  width: 20px;
  color: var(--copper);
}

.schedule-grid div {
  display: grid;
  gap: 5px;
}

.schedule-grid span,
.schedule-grid small {
  color: var(--text-muted);
  font-size: 11px;
}

.dialog-backdrop {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(5, 3, 5, 0.72);
  backdrop-filter: blur(8px);
}

.profile-dialog {
  width: min(680px, 100%);
  max-height: calc(100vh - 40px);
  overflow-y: auto;
  padding: 28px;
  box-shadow: var(--shadow);
}

.dialog-heading {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

.dialog-heading h2 {
  margin: 7px 0 0;
}

.dialog-heading > button {
  width: 36px;
  height: 36px;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--text-soft);
  background: transparent;
  cursor: pointer;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 5px;
}

@media (max-width: 900px) {
  .profile-banner {
    grid-template-columns: 100px 1fr;
  }

  .profile-contact {
    grid-column: 1 / -1;
    border-top: 1px solid var(--line);
    border-left: 0;
  }

  .summary-grid,
  .schedule-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .profile-page {
    padding-top: 20px;
  }

  .profile-banner {
    grid-template-columns: 82px 1fr;
    padding: 58px 18px 22px;
  }

  .profile-avatar {
    width: 82px;
    height: 100px;
  }

  .profile-identity h1 {
    font-size: 27px;
  }

  .profile-contact {
    padding: 18px 0 0;
  }

  .profile-contact div {
    grid-template-columns: 110px 1fr;
  }

  .summary-grid,
  .schedule-grid {
    grid-template-columns: 1fr 1fr;
  }

  .summary-grid article {
    padding: 17px;
  }

  .schedule-grid article {
    grid-column: 1 / -1;
  }
}
</style>
