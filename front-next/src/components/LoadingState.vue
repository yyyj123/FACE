<script setup lang="ts">
withDefaults(defineProps<{ label?: string; count?: number }>(), {
  label: '内容加载中',
  count: 3,
})
</script>

<template>
  <div role="status" aria-live="polite" aria-busy="true" :aria-label="label">
    <span v-for="index in count" :key="index" class="skeleton" aria-hidden="true" />
  </div>
</template>
