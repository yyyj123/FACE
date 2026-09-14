<script setup lang="ts">
import type { Appointment } from '../types/domain'
import { dateTime, money, statusClass, statusText } from '../utils/format'

defineProps<{
  appointment: Appointment
  technician?: boolean
  busy?: boolean
}>()

defineEmits<{
  cancel: [appointment: Appointment]
  status: [appointment: Appointment, status: string]
  start: [appointment: Appointment]
  care: [appointment: Appointment]
}>()

const nextStatus: Record<string, { value: string; label: string }> = {
  PENDING: { value: 'CONFIRMED', label: '确认预约' },
  CONFIRMED: { value: 'CHECKED_IN', label: '确认到店' },
}

function nextStatusValue(status: string) {
  return nextStatus[status]?.value || ''
}

function nextStatusLabel(status: string) {
  return nextStatus[status]?.label || ''
}
</script>

<template>
  <article class="appointment-card">
    <div class="appointment-status-row">
      <span class="status-badge" :class="statusClass[appointment.status]">
        {{ statusText[appointment.status] || appointment.status }}
      </span>
      <small>{{ appointment.appointmentNo }}</small>
    </div>
    <h3>{{ appointment.serviceNames || '护理服务' }}</h3>
    <dl class="appointment-facts">
      <div>
        <dt>到店时间</dt>
        <dd>{{ dateTime(appointment.startAt) }}</dd>
      </div>
      <div>
        <dt>{{ technician ? '顾客' : '护理技师' }}</dt>
        <dd>{{ technician ? appointment.memberName : appointment.staffName }}</dd>
      </div>
      <div>
        <dt>项目金额</dt>
        <dd>{{ money(appointment.totalPrice) }}</dd>
      </div>
    </dl>
    <div v-if="appointment.memberNote" class="appointment-note">
      备注：{{ appointment.memberNote }}
    </div>
    <div class="appointment-actions">
      <button
        v-if="!technician && ['PENDING', 'CONFIRMED'].includes(appointment.status)"
        class="button button-quiet button-small"
        type="button"
        :disabled="busy"
        @click="$emit('cancel', appointment)"
      >
        取消预约
      </button>
      <button
        v-if="technician && nextStatusValue(appointment.status)"
        class="button button-primary button-small"
        type="button"
        :disabled="busy"
        @click="$emit('status', appointment, nextStatusValue(appointment.status))"
      >
        {{ busy ? '处理中…' : nextStatusLabel(appointment.status) }}
      </button>
      <button
        v-if="technician && appointment.status === 'CHECKED_IN'"
        class="button button-primary button-small"
        type="button"
        :disabled="busy"
        @click="$emit('start', appointment)"
      >
        {{ busy ? '正在开工…' : '开始护理' }}
      </button>
      <button
        v-if="technician && appointment.status === 'IN_SERVICE' && appointment.serviceRecordId"
        class="button button-primary button-small"
        type="button"
        :disabled="busy"
        @click="$emit('care', appointment)"
      >
        填写护理记录
      </button>
    </div>
  </article>
</template>
