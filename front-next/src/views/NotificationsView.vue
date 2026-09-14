<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import type { NotificationItem } from '../types/domain'

const status = ref<'ALL' | 'UNREAD' | 'READ'>('ALL')
const notifications = ref<NotificationItem[]>([])
const unreadCount = ref(0)
const loading = ref(true)
const actingId = ref<number>()
const error = ref('')
const statusOptions: Array<{ value: 'ALL' | 'UNREAD' | 'READ'; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'UNREAD', label: '未读' },
  { value: 'READ', label: '已读' },
]

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await api.notifications(status.value)
    notifications.value = result.records
    unreadCount.value = result.unreadCount
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '通知加载失败'
  } finally {
    loading.value = false
  }
}

async function markRead(item: NotificationItem) {
  if (item.status === 'READ') return
  actingId.value = item.id
  error.value = ''
  try {
    await api.markNotificationRead(item.id, item.version)
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '通知已读更新失败'
  } finally {
    actingId.value = undefined
  }
}

async function markAllRead() {
  actingId.value = -1
  error.value = ''
  try {
    await api.markAllNotificationsRead()
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '全部已读更新失败'
  } finally {
    actingId.value = undefined
  }
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="notifications-page page-container page-section">
    <header class="page-heading">
      <div>
        <span class="page-context">业务通知</span>
        <h1>消息中心</h1>
        <p>审批、售后、提成和结算进度由门店系统同步；通知内容只显示安全摘要。</p>
      </div>
      <button
        v-if="unreadCount"
        class="button button-secondary button-small"
        type="button"
        :disabled="actingId === -1"
        @click="markAllRead"
      >
        {{ actingId === -1 ? '正在更新…' : `全部已读（${unreadCount}）` }}
      </button>
    </header>

    <div class="filter-bar" aria-label="通知状态筛选">
      <button
        v-for="option in statusOptions"
        :key="option.value"
        :class="{ active: status === option.value }"
        type="button"
        @click="status = option.value; load()"
      >
        {{ option.label }}
      </button>
    </div>

    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>

    <div v-if="loading" class="message-skeletons" aria-label="正在加载通知">
      <span v-for="index in 4" :key="index" class="skeleton" />
    </div>

    <div v-else-if="notifications.length" class="message-list">
      <button
        v-for="item in notifications"
        :key="item.id"
        :class="['message-row', { unread: item.status === 'UNREAD' }]"
        type="button"
        :disabled="actingId === item.id"
        @click="markRead(item)"
      >
        <span class="state-dot" aria-hidden="true" />
        <span class="message-copy">
          <span class="message-title">
            <strong>{{ item.title }}</strong>
            <small>{{ item.category }}</small>
          </span>
          <span>{{ item.safeSummary }}</span>
          <small>
            {{ formatDate(item.createdAt) }} · 站内已送达 ·
            外部通道{{ item.externalStatus === 'UNAVAILABLE' ? '不可用' : '未请求' }}
          </small>
        </span>
      </button>
    </div>

    <EmptyState
      v-else
      title="这里暂时没有通知"
      description="预约、售后、审批或提成状态变化后，与你相关的消息会出现在这里。"
      action-label="返回个人中心"
      to="/profile"
    />
  </div>
</template>

<style scoped>
.notifications-page {
  padding-bottom: 92px;
}

.page-context {
  color: var(--copper);
  font-size: 13px;
  font-weight: 700;
}

.filter-bar {
  display: flex;
  gap: 6px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line-strong);
}

.filter-bar button {
  min-height: 38px;
  padding: 0 15px;
  border: 1px solid transparent;
  border-radius: 8px;
  color: var(--text-soft);
  background: transparent;
  cursor: pointer;
}

.filter-bar button:hover,
.filter-bar button.active {
  color: var(--rose-strong);
  background: var(--surface-soft);
}

.message-skeletons {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.message-skeletons .skeleton {
  min-height: 98px;
}

.message-list {
  border-bottom: 1px solid var(--line);
}

.message-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 14px;
  width: 100%;
  padding: 20px 2px;
  border: 0;
  border-bottom: 1px solid var(--line);
  color: inherit;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.message-row:hover {
  background: var(--surface-strong);
}

.state-dot {
  width: 8px;
  height: 8px;
  margin-top: 7px;
  border-radius: 50%;
  background: var(--line-strong);
}

.message-row.unread .state-dot {
  background: var(--rose);
}

.message-copy {
  display: grid;
  gap: 6px;
}

.message-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.message-title small,
.message-copy > small {
  color: var(--text-muted);
  font-size: 12px;
}

.message-copy > span:not(.message-title) {
  color: var(--text-soft);
  line-height: 1.6;
}

@media (max-width: 620px) {
  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .message-title {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
