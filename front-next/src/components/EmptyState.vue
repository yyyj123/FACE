<script setup lang="ts">
import { Calendar } from '@element-plus/icons-vue'

withDefaults(defineProps<{ title: string; description: string; actionLabel?: string; to?: string }>(), {
  actionLabel: '',
  to: '',
})
</script>

<template>
  <div class="empty-state" role="status" aria-live="polite">
    <span class="empty-icon"><Calendar /></span>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <router-link v-if="actionLabel && to" class="button button-primary button-small" :to="to">
      {{ actionLabel }}
    </router-link>
  </div>
</template>
