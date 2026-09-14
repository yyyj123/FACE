<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowLeft, ArrowRight, Calendar, Check, Clock, Location } from '@element-plus/icons-vue'
import { api } from '../api/client'
import CircularGallery from '../components/CircularGallery.vue'
import type { Banner, ServiceItem, Staff } from '../types/domain'
import { handleMediaError, mediaUrl } from '../utils/format'

const banners = ref<Banner[]>([])
const services = ref<ServiceItem[]>([])
const staff = ref<Staff[]>([])
const current = ref(0)
const servicesLoading = ref(true)
const staffLoading = ref(true)
const servicesError = ref('')
const staffError = ref('')
const staffPage = ref(1)
const STAFF_PAGE_SIZE = 5
let timer: number | undefined

const activeBanner = computed(() => banners.value[current.value])
const bannerTarget = computed(() => {
  const banner = activeBanner.value
  if (banner?.targetType === 'SERVICE' && banner.targetValue) return `/services/${banner.targetValue}`
  return '/services'
})
const serviceGalleryItems = computed(() =>
  services.value.map((service) => ({
    image: mediaUrl(service.coverUrl, service.id),
    text: `${service.name} · ¥${Number(service.memberPrice ?? service.listPrice).toFixed(0)}`,
  })),
)
const staffPageCount = computed(() => Math.max(1, Math.ceil(staff.value.length / STAFF_PAGE_SIZE)))
const staffPages = computed(() => Array.from({ length: staffPageCount.value }, (_, index) => index + 1))
const pagedStaff = computed(() => {
  const start = (staffPage.value - 1) * STAFF_PAGE_SIZE
  return staff.value.slice(start, start + STAFF_PAGE_SIZE)
})

function selectBanner(index: number) {
  current.value = index
}

function selectStaffPage(page: number) {
  staffPage.value = Math.min(Math.max(page, 1), staffPageCount.value)
}

function startCarousel() {
  window.clearInterval(timer)
  if (banners.value.length < 2) return
  timer = window.setInterval(() => {
    current.value = (current.value + 1) % banners.value.length
  }, 6000)
}

onMounted(async () => {
  const [bannerResult, serviceResult, staffResult] = await Promise.allSettled([
    api.homeContent(),
    api.services({ featured: true }),
    api.staff(),
  ])

  if (bannerResult.status === 'fulfilled') {
    banners.value = bannerResult.value.content
      .filter((entry) => entry.contentType === 'BANNER')
      .map((entry) => ({
        id: entry.id,
        title: entry.title,
        imageUrl: entry.imageUrl || '',
        targetType: entry.targetType,
        targetValue: entry.targetValue,
      }))
    startCarousel()
  }

  if (serviceResult.status === 'fulfilled') {
    services.value = serviceResult.value.slice(0, 6)
  } else {
    servicesError.value = serviceResult.reason instanceof Error
      ? serviceResult.reason.message
      : '护理项目暂时无法加载'
  }
  servicesLoading.value = false

  if (staffResult.status === 'fulfilled') {
    staff.value = staffResult.value
    staffPage.value = 1
  } else {
    staffError.value = staffResult.reason instanceof Error
      ? staffResult.reason.message
      : '护理技师暂时无法加载'
  }
  staffLoading.value = false
})

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <div>
    <section class="hero-section">
      <div v-if="activeBanner" class="hero-media">
        <img
          :src="mediaUrl(activeBanner.imageUrl, activeBanner.id)"
          :alt="activeBanner.title"
          @error="handleMediaError($event, activeBanner.id)"
        />
      </div>
      <div v-else class="hero-media hero-fallback" />
      <div class="hero-overlay" />
      <div class="page-container hero-content">
        <span class="eyebrow">FACE BEAUTY CARE</span>
        <h1>{{ activeBanner?.title || '把每一次护理，安排得从容而清晰' }}</h1>
        <p>从项目选择、技师排期到到店护理，信息与门店实时同步，不再反复确认。</p>
        <div class="hero-actions">
          <router-link class="button button-primary" :to="bannerTarget">
            了解本期护理
            <ArrowRight />
          </router-link>
          <router-link class="button button-secondary" to="/booking">选择到店时间</router-link>
        </div>
      </div>
      <div v-if="banners.length > 1" class="hero-dots" aria-label="轮播图切换">
        <button
          v-for="(banner, index) in banners"
          :key="banner.id"
          type="button"
          :class="{ active: current === index }"
          :aria-label="`查看第 ${index + 1} 张：${banner.title}`"
          :aria-current="current === index"
          @click="selectBanner(index)"
        />
      </div>
    </section>

    <section class="confidence-strip" aria-label="服务承诺">
      <div class="page-container confidence-grid">
        <div>
          <Calendar />
          <span><strong>实时预约</strong>门店排班同步</span>
        </div>
        <div>
          <Clock />
          <span><strong>清晰时长</strong>到店节奏可预期</span>
        </div>
        <div>
          <Check />
          <span><strong>护理留档</strong>服务过程可追溯</span>
        </div>
        <div>
          <Location />
          <span><strong>到店服务</strong>项目技师一一对应</span>
        </div>
      </div>
    </section>

    <section class="care-path-section">
      <div class="page-container care-path-grid">
        <div class="care-path-copy">
          <span class="eyebrow">A CLEAR PATH</span>
          <h2>从选择项目到完成护理，只需要四步</h2>
          <p>预约成功后，顾客和技师看到的是同一条记录。状态变化会进入门店业务数据，不再依赖纸质登记。</p>
          <router-link class="card-link care-link" to="/booking">
            开始预约
            <ArrowRight />
          </router-link>
        </div>
        <ol class="care-steps">
          <li><span>01</span><div><strong>选择项目</strong><p>查看时长、价格与护理说明</p></div></li>
          <li><span>02</span><div><strong>匹配技师</strong><p>只展示具备项目技能的在岗技师</p></div></li>
          <li><span>03</span><div><strong>确认时间</strong><p>自动避开已预约与不可用时段</p></div></li>
          <li><span>04</span><div><strong>到店护理</strong><p>从确认到完成，全程状态可查</p></div></li>
        </ol>
      </div>
    </section>

    <section class="page-section gallery-showcase">
      <div class="page-container section-heading">
        <div>
          <span class="eyebrow">POPULAR CARE</span>
          <h2>本店热门护理项目</h2>
          <p>以下内容直接来自管理端项目库，价格、时长和上下架状态保持一致。</p>
        </div>
        <router-link class="button button-secondary button-small" to="/services">查看全部项目</router-link>
      </div>

      <div v-if="servicesError" class="page-container notice">{{ servicesError }}</div>
      <div v-else-if="servicesLoading" class="page-container gallery-loading">
        <span v-for="index in 3" :key="index" class="gallery-skeleton" />
      </div>
      <div v-else-if="services.length === 0" class="page-container notice">本店暂未上架护理项目</div>
      <div v-else class="gallery-stage">
        <CircularGallery
          :items="serviceGalleryItems"
          :bend="1"
          text-color="#251b21"
          :border-radius="0.05"
          font="bold 30px Orbitron"
          font-url=""
          :scroll-speed="2"
          :scroll-ease="0.05"
          aria-label="热门护理项目弧形画廊，可拖拽、滚动或使用左右方向键浏览"
        />
      </div>
    </section>

    <section id="staff-team" class="page-section gallery-showcase staff-section">
      <div class="page-container section-heading">
        <div>
          <span class="eyebrow">OUR SPECIALISTS</span>
          <h2>认识为你服务的护理技师</h2>
          <p>技师信息与管理端美容师档案来自同一张员工表，新增、停用或改名都会同步显示。</p>
        </div>
      </div>
      <div v-if="staffError" class="page-container notice">{{ staffError }}</div>
      <div v-else-if="staffLoading" class="page-container gallery-loading">
        <span v-for="index in 3" :key="index" class="gallery-skeleton" />
      </div>
      <div v-else-if="staff.length === 0" class="page-container notice">暂无可预约护理技师</div>
      <div v-else class="page-container staff-grid" aria-label="护理技师列表">
        <router-link
          v-for="person in pagedStaff"
          :key="person.id"
          class="staff-card"
          :to="{ name: 'staff-detail', params: { id: person.id } }"
          :aria-label="`查看${person.name}的详细资料`"
        >
          <img
            :src="mediaUrl(person.avatarUrl, person.id + 5)"
            :alt="person.name"
            loading="lazy"
            decoding="async"
            @error="handleMediaError($event, person.id + 5)"
          />
          <div>
            <h3>{{ person.name }}</h3>
            <p>{{ person.levelName || person.jobRole || '护理技师' }}</p>
            <span>查看详细资料 <ArrowRight /></span>
          </div>
        </router-link>
      </div>
      <nav v-if="staffPageCount > 1" class="page-container staff-pagination" aria-label="护理技师分页">
        <button
          class="staff-page-control"
          type="button"
          aria-label="上一页技师"
          :disabled="staffPage === 1"
          @click="selectStaffPage(staffPage - 1)"
        >
          <ArrowLeft />
        </button>
        <div class="staff-page-position">
          <div class="staff-page-numbers">
            <button
              v-for="page in staffPages"
              :key="page"
              type="button"
              :aria-label="`第 ${page} 页`"
              :aria-current="staffPage === page ? 'page' : undefined"
              @click="selectStaffPage(page)"
            >
              {{ page }}
            </button>
          </div>
          <span aria-live="polite">第 {{ staffPage }} / {{ staffPageCount }} 页 · 共 {{ staff.length }} 名技师</span>
        </div>
        <button
          class="staff-page-control"
          type="button"
          aria-label="下一页技师"
          :disabled="staffPage === staffPageCount"
          @click="selectStaffPage(staffPage + 1)"
        >
          <ArrowRight />
        </button>
      </nav>
    </section>
  </div>
</template>

<style scoped>
.hero-section {
  position: relative;
  min-height: min(720px, calc(100vh - var(--header-height)));
  overflow: hidden;
  display: grid;
  align-items: end;
}

.hero-media,
.hero-overlay {
  position: absolute;
  inset: 0;
}

.hero-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-fallback {
  background: url("/images/hero-facial.webp") center / cover;
}

.hero-overlay {
  background:
    linear-gradient(90deg, rgba(13, 9, 13, 0.96) 0%, rgba(13, 9, 13, 0.75) 42%, rgba(13, 9, 13, 0.18) 78%),
    linear-gradient(0deg, rgba(13, 9, 13, 0.9) 0%, transparent 45%);
}

.hero-content {
  position: relative;
  z-index: 2;
  padding-top: 120px;
  padding-bottom: 112px;
  color: #ffffff;
}

.hero-content .eyebrow {
  color: #efb59d;
}

.hero-content h1 {
  max-width: 700px;
  margin: 14px 0 20px;
  font-size: clamp(42px, 6.2vw, 78px);
  line-height: 1.05;
  letter-spacing: -0.035em;
}

.hero-content p {
  max-width: 580px;
  margin: 0;
  color: #f3e9ed;
  font-size: 17px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 34px;
}

.hero-actions svg {
  width: 17px;
}

.hero-actions .button-secondary {
  border-color: rgba(255, 255, 255, 0.5);
  color: #ffffff;
  background: rgba(13, 9, 13, 0.24);
}

.hero-actions .button-secondary:hover {
  border-color: rgba(255, 255, 255, 0.76);
  background: rgba(13, 9, 13, 0.38);
}

.hero-dots {
  position: absolute;
  z-index: 3;
  right: max(24px, calc((100vw - var(--content)) / 2));
  bottom: 38px;
  display: flex;
  gap: 8px;
}

.hero-dots button {
  width: 42px;
  height: 3px;
  padding: 0;
  border: 0;
  background: rgba(255, 255, 255, 0.24);
  cursor: pointer;
  transform: scaleX(0.62);
  transition: transform 180ms ease, background 180ms ease;
}

.hero-dots button.active {
  background: var(--rose-strong);
  transform: scaleX(1);
}

.confidence-strip {
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}

.confidence-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
}

.confidence-grid > div {
  min-height: 105px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 26px;
  border-right: 1px solid var(--line);
}

.confidence-grid > div:first-child {
  border-left: 1px solid var(--line);
}

.confidence-grid svg {
  width: 21px;
  color: var(--copper);
}

.confidence-grid span {
  display: grid;
  gap: 4px;
  color: var(--text-muted);
  font-size: 12px;
}

.confidence-grid strong {
  color: var(--text);
  font-size: 14px;
}

.care-path-section {
  padding: 78px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.98), rgba(255, 255, 255, 0.8)),
    url("/images/path-care.webp") center / cover;
}

.care-path-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(440px, 1.1fr);
  gap: 80px;
  align-items: center;
}

.care-path-copy h2 {
  margin: 12px 0 18px;
  font-size: clamp(30px, 4.6vw, 54px);
  line-height: 1.1;
  letter-spacing: -0.045em;
}

.care-path-copy p {
  color: var(--text-soft);
  line-height: 1.8;
}

.care-link {
  max-width: 190px;
  margin-top: 26px;
  padding-bottom: 9px;
  border-bottom: 1px solid var(--line-strong);
}

.care-steps {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.care-steps li {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 17px;
  padding: 20px 0;
  border-bottom: 1px solid var(--line);
}

.care-steps > li > span {
  color: var(--copper);
  font-size: 13px;
  font-weight: 700;
}

.care-steps strong {
  font-size: 17px;
}

.care-steps p {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.gallery-showcase {
  overflow: hidden;
}

.gallery-showcase .section-heading {
  margin-bottom: 0;
}

.gallery-stage {
  position: relative;
  width: 100%;
  height: 600px;
  margin-top: 6px;
}

.staff-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 18px;
  margin-top: 34px;
}

.staff-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  content-visibility: auto;
  contain-intrinsic-size: 360px;
  transition: transform 200ms ease, border-color 200ms ease, box-shadow 200ms ease;
}

.staff-card:hover {
  transform: translateY(-4px);
  border-color: var(--line-strong);
  box-shadow: var(--shadow);
}

.staff-card img {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 5;
  object-fit: cover;
  transition: transform 360ms ease;
}

.staff-card:hover img { transform: scale(1.02); }

.staff-card div { padding: 18px; }
.staff-card h3 { margin: 0 0 6px; font-size: 22px; }
.staff-card p { margin: 0 0 18px; color: var(--copper); }
.staff-card span { display:inline-flex;align-items:center;gap:7px;color:var(--text-soft);font-size:13px;font-weight:700; }
.staff-card span svg { width:15px;transition:transform 180ms ease; }
.staff-card:hover span svg { transform:translateX(3px); }

.staff-pagination {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 44px;
  align-items: center;
  gap: 16px;
  margin-top: 28px;
}

.staff-page-control,
.staff-page-numbers button {
  height: 44px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text);
  cursor: pointer;
}

.staff-page-control {
  display: grid;
  width: 44px;
  place-items: center;
}

.staff-page-control svg { width: 16px; }
.staff-page-control:disabled { opacity: 0.38; cursor: not-allowed; }

.staff-page-position {
  min-width: 0;
  display: grid;
  justify-items: center;
  gap: 8px;
}

.staff-page-position > span {
  color: var(--text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.staff-page-numbers { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; }
.staff-page-numbers button { min-width: 44px; padding: 0 12px; }
.staff-page-numbers button[aria-current="page"] { border-color: var(--rose); background: var(--rose); color: #fff; }
.staff-page-control:focus-visible,
.staff-page-numbers button:focus-visible { outline: 2px solid var(--rose-strong); outline-offset: 3px; }

.gallery-loading {
  min-height: 420px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: center;
  gap: 20px;
}

.gallery-skeleton {
  height: 310px;
  border-radius: 10px;
  background: linear-gradient(100deg, var(--surface) 20%, var(--surface-soft) 45%, var(--surface) 70%);
  background-size: 300% 100%;
  animation: shimmer 1.4s infinite;
}

@media (max-width: 900px) {
  .confidence-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .care-path-grid {
    grid-template-columns: 1fr;
    gap: 42px;
  }

  .staff-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

}

@media (max-width: 640px) {
  .hero-section {
    min-height: 650px;
  }

  .hero-overlay {
    background:
      linear-gradient(0deg, rgba(13, 9, 13, 0.98) 5%, rgba(13, 9, 13, 0.68) 68%, rgba(13, 9, 13, 0.24) 100%);
  }

  .hero-content {
    padding-bottom: 96px;
  }

  .hero-content h1 {
    font-size: 43px;
  }

  .hero-dots {
    right: 16px;
    bottom: 28px;
  }

  .confidence-grid {
    grid-template-columns: 1fr 1fr;
  }

  .confidence-grid > div {
    min-height: 92px;
    padding: 15px 14px;
  }

  .confidence-grid > div:nth-child(odd) {
    border-left: 1px solid var(--line);
  }

  .gallery-showcase .section-heading {
    margin-bottom: 4px;
  }

  .gallery-stage {
    height: 455px;
  }

  .staff-grid { grid-template-columns: 1fr 1fr; gap: 12px; }
  .staff-card div { padding: 14px; }
  .staff-pagination { gap: 8px; margin-top: 22px; }

  .gallery-loading {
    min-height: 350px;
    grid-template-columns: 1fr 1fr;
  }

  .gallery-loading .gallery-skeleton:last-child {
    display: none;
  }

  .gallery-skeleton {
    height: 270px;
  }
}
</style>
