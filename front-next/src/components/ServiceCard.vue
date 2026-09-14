<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import type { ServiceItem } from '../types/domain'
import { handleMediaError, mediaUrl, money } from '../utils/format'

defineProps<{ service: ServiceItem }>()
</script>

<template>
  <article class="service-card">
    <router-link class="service-image-link" :to="`/services/${service.id}`">
      <img
        class="service-image"
        :src="mediaUrl(service.coverUrl, service.id)"
        :alt="`${service.name}护理项目`"
        @error="handleMediaError($event, service.id)"
      />
      <span class="category-pill">{{ service.categoryName }}</span>
    </router-link>
    <div class="service-card-body">
      <div>
        <h3>{{ service.name }}</h3>
        <p>{{ service.subtitle || service.description || '到店后由专业技师为你确认护理方案。' }}</p>
      </div>
      <div class="service-card-meta">
        <span>{{ service.durationMinutes }} 分钟</span>
        <strong>{{ money(service.memberPrice ?? service.listPrice) }}</strong>
      </div>
      <router-link class="card-link" :to="`/services/${service.id}`">
        查看项目
        <ArrowRight />
      </router-link>
    </div>
  </article>
</template>
