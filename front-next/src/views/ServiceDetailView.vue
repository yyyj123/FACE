<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Calendar, Check, Clock, UserFilled } from '@element-plus/icons-vue'
import { api } from '../api/client'
import type { ServiceItem, Staff } from '../types/domain'
import { handleMediaError, mediaUrl, money } from '../utils/format'

const route = useRoute()
const service = ref<ServiceItem | null>(null)
const staff = ref<Staff[]>([])
const loading = ref(true)
const error = ref('')
const serviceId = computed(() => Number(route.params.id))

onMounted(async () => {
  if (!Number.isFinite(serviceId.value)) {
    error.value = '项目编号无效'
    loading.value = false
    return
  }
  try {
    const [detail, specialists] = await Promise.all([
      api.service(serviceId.value),
      api.staff(serviceId.value),
      api.recordServiceView(serviceId.value).catch(() => false),
    ])
    service.value = detail
    staff.value = specialists
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '项目加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page-container detail-page">
    <div v-if="loading" class="detail-skeleton skeleton" />
    <div v-else-if="error" class="notice detail-error">{{ error }}</div>
    <template v-else-if="service">
      <section class="detail-hero">
        <div class="detail-image">
          <img
            :src="mediaUrl(service.coverUrl, service.id)"
            :alt="service.name"
            @error="handleMediaError($event, service.id)"
          />
        </div>
        <div class="detail-copy">
          <span class="eyebrow">{{ service.categoryName }}</span>
          <h1>{{ service.name }}</h1>
          <p class="subtitle">{{ service.subtitle || '一项按标准流程执行的到店护理服务。' }}</p>
          <div class="price-row">
            <strong>{{ money(service.memberPrice ?? service.listPrice) }}</strong>
            <span v-if="service.memberPrice && service.memberPrice !== service.listPrice">
              门市价 {{ money(service.listPrice) }}
            </span>
          </div>
          <div class="detail-facts">
            <span><Clock />护理约 {{ service.durationMinutes }} 分钟</span>
            <span><UserFilled />{{ staff.length }} 位技师可预约</span>
            <span><Check />到店后再次确认方案</span>
          </div>
          <router-link class="button button-primary" :to="`/booking?serviceId=${service.id}`">
            <Calendar />
            选择技师与时间
          </router-link>
        </div>
      </section>

      <section class="detail-content">
        <article class="panel description-panel">
          <span class="eyebrow">ABOUT THIS CARE</span>
          <h2>项目说明</h2>
          <p>{{ service.description || '到店后，护理技师会先了解你的近期状态，再按照项目流程完成护理。' }}</p>
          <div class="care-notes">
            <div><strong>护理时长</strong><span>{{ service.durationMinutes }} 分钟</span></div>
            <div><strong>清洁准备</strong><span>{{ service.cleanupMinutes || 0 }} 分钟</span></div>
            <div><strong>预约方式</strong><span>线上选择，到店确认</span></div>
          </div>
        </article>

        <aside class="panel booking-note">
          <span class="eyebrow">BEFORE YOU BOOK</span>
          <h2>预约前说明</h2>
          <ul>
            <li>可预约时间取自技师实际排班。</li>
            <li>已被占用的时段不会重复开放。</li>
            <li>如需取消，请尽量在到店前操作。</li>
          </ul>
        </aside>
      </section>

      <section class="specialist-section">
        <div class="section-heading">
          <div>
            <span class="eyebrow">AVAILABLE SPECIALISTS</span>
            <h2>可服务技师</h2>
          </div>
        </div>
        <div class="specialist-grid">
          <article v-for="person in staff" :key="person.id">
            <img
              :src="mediaUrl(person.avatarUrl, person.id + 3)"
              :alt="person.name"
              @error="handleMediaError($event, person.id + 3)"
            />
            <div>
              <h3>{{ person.name }}</h3>
              <span>{{ person.levelName || person.jobRole }}</span>
              <p>{{ person.bio || '熟悉本项目标准护理流程。' }}</p>
            </div>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.detail-page {
  padding-top: 42px;
  padding-bottom: 78px;
}

.detail-skeleton {
  min-height: 620px;
}

.detail-error {
  margin-top: 60px;
}

.detail-hero {
  min-height: 590px;
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  background: var(--surface);
}

.detail-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: start;
  padding: clamp(32px, 5vw, 70px);
}

.detail-copy h1 {
  margin: 12px 0 14px;
  font-size: clamp(38px, 5vw, 64px);
  line-height: 1.04;
  letter-spacing: -0.05em;
}

.subtitle {
  margin: 0;
  color: var(--text-soft);
  font-size: 16px;
  line-height: 1.75;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 28px 0;
}

.price-row strong {
  color: var(--copper);
  font-size: 29px;
}

.price-row span {
  color: var(--text-muted);
  font-size: 12px;
  text-decoration: line-through;
}

.detail-facts {
  display: grid;
  gap: 12px;
  margin-bottom: 28px;
  color: var(--text-soft);
  font-size: 13px;
}

.detail-facts span {
  display: flex;
  align-items: center;
  gap: 9px;
}

.detail-facts svg,
.detail-copy .button svg {
  width: 17px;
}

.detail-facts svg {
  color: var(--copper);
}

.detail-content {
  display: grid;
  grid-template-columns: 1.45fr 0.55fr;
  gap: 20px;
  padding: 40px 0;
}

.description-panel,
.booking-note {
  padding: 32px;
}

.detail-content h2 {
  margin: 9px 0 18px;
  font-size: 25px;
}

.description-panel > p {
  color: var(--text-soft);
  line-height: 1.85;
}

.care-notes {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 26px;
}

.care-notes div {
  display: grid;
  gap: 7px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--surface-strong);
}

.care-notes strong {
  font-size: 12px;
}

.care-notes span {
  color: var(--text-muted);
  font-size: 13px;
}

.booking-note ul {
  display: grid;
  gap: 13px;
  padding-left: 19px;
  color: var(--text-soft);
  line-height: 1.65;
}

.specialist-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}

.specialist-grid article {
  overflow: hidden;
  display: grid;
  grid-template-columns: 120px 1fr;
  min-height: 155px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface);
}

.specialist-grid img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.specialist-grid div {
  padding: 20px;
}

.specialist-grid h3 {
  margin: 0 0 5px;
}

.specialist-grid span {
  color: var(--copper);
  font-size: 12px;
}

.specialist-grid p {
  margin: 11px 0 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .detail-hero {
    grid-template-columns: 1fr;
  }

  .detail-image {
    height: 440px;
  }

  .detail-content {
    grid-template-columns: 1fr;
  }

  .specialist-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .detail-page {
    padding-top: 20px;
  }

  .detail-image {
    height: 320px;
  }

  .detail-copy {
    padding: 28px 22px;
  }

  .care-notes,
  .specialist-grid {
    grid-template-columns: 1fr;
  }
}
</style>
