<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, Calendar, Check } from '@element-plus/icons-vue'
import { api } from '../api/client'
import type { Staff } from '../types/domain'
import { handleMediaError, mediaUrl } from '../utils/format'

const route = useRoute()
const staff = ref<Staff | null>(null)
const loading = ref(true)
const error = ref('')

const specialties = computed(() => (staff.value?.specialties ?? '')
  .split(/[、,，]/)
  .map((item) => item.trim())
  .filter(Boolean))

onMounted(async () => {
  try {
    const id = Number(route.params.id)
    if (!Number.isInteger(id) || id <= 0) throw new Error('这位技师的资料不存在')
    staff.value = (await api.staff()).find((item) => item.id === id) ?? null
    if (!staff.value) throw new Error('这位技师当前不在可预约名单中')
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '技师资料暂时无法加载'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main class="staff-detail page-section">
    <div class="page-container">
      <router-link class="staff-back" to="/#staff-team"><ArrowLeft /> 返回技师列表</router-link>

      <div v-if="loading" class="staff-detail-loading" aria-label="正在加载技师资料">
        <span /><span />
      </div>

      <section v-else-if="error || !staff" class="empty-state" aria-live="polite">
        <h1>暂时无法查看</h1>
        <p>{{ error }}</p>
        <router-link class="button button-secondary" to="/">返回首页</router-link>
      </section>

      <article v-else class="staff-profile">
        <div class="staff-portrait">
          <img
            :src="mediaUrl(staff.avatarUrl, staff.id + 5)"
            :alt="staff.name"
            @error="handleMediaError($event, staff.id + 5)"
          />
        </div>

        <div class="staff-story">
          <header>
            <p>{{ staff.levelName || staff.jobRole || '护理技师' }}</p>
            <h1>{{ staff.name }}</h1>
            <span>在店服务 · 可在线预约</span>
          </header>

          <section class="staff-introduction">
            <h2>关于我</h2>
            <p>{{ staff.bio || '会在护理前了解你的近期肤况与护理偏好，并根据实际情况沟通适合的护理方案。' }}</p>
          </section>

          <section class="staff-specialties">
            <h2>擅长项目</h2>
            <ul v-if="specialties.length">
              <li v-for="item in specialties" :key="item"><Check />{{ item }}</li>
            </ul>
            <p v-else>擅长项目正在整理中，预约时可先选择护理项目。</p>
          </section>

          <div class="staff-actions">
            <router-link class="button button-primary" :to="{ name: 'booking', query: { staffId: staff.id } }">
              <Calendar /> 预约这位技师
            </router-link>
            <router-link class="card-link" to="/services">先看护理项目 <ArrowRight /></router-link>
          </div>
        </div>
      </article>
    </div>
  </main>
</template>

<style scoped>
.staff-detail{padding-top:42px}.staff-back{display:inline-flex;align-items:center;gap:7px;margin-bottom:24px;color:var(--text-soft);font-size:13px;font-weight:700}.staff-back svg{width:16px}.staff-profile{display:grid;grid-template-columns:minmax(320px,.85fr) minmax(0,1.15fr);min-height:680px;overflow:hidden;border:1px solid var(--line);border-radius:14px;background:var(--surface);box-shadow:0 16px 44px rgba(47,29,39,.08)}.staff-portrait{min-height:620px;overflow:hidden;background:var(--surface-soft)}.staff-portrait img{width:100%;height:100%;object-fit:cover}.staff-story{display:flex;flex-direction:column;padding:clamp(34px,5vw,72px)}.staff-story header{padding-bottom:30px;border-bottom:1px solid var(--line)}.staff-story header p{margin:0 0 10px;color:var(--copper);font-weight:700}.staff-story h1{margin:0;font-size:clamp(42px,6vw,72px);line-height:1;letter-spacing:-.035em}.staff-story header span{display:block;margin-top:17px;color:var(--success);font-size:13px}.staff-introduction,.staff-specialties{padding-top:30px}.staff-story h2{margin:0 0 13px;font-size:20px}.staff-introduction p,.staff-specialties>p{max-width:65ch;margin:0;color:var(--text-soft);line-height:1.85}.staff-specialties ul{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px 18px;padding:0;margin:0;list-style:none}.staff-specialties li{display:flex;align-items:flex-start;gap:8px;color:var(--text-soft);line-height:1.55}.staff-specialties li svg{width:16px;flex:0 0 16px;margin-top:4px;color:var(--rose)}.staff-actions{display:flex;align-items:center;gap:24px;margin-top:auto;padding-top:38px}.staff-actions svg{width:17px}.staff-detail-loading{display:grid;grid-template-columns:.85fr 1.15fr;min-height:680px;overflow:hidden;border:1px solid var(--line);border-radius:14px}.staff-detail-loading span{background:linear-gradient(100deg,var(--surface) 20%,var(--surface-soft) 45%,var(--surface) 70%);background-size:300% 100%;animation:shimmer 1.4s infinite}.staff-detail-loading span+span{border-left:1px solid var(--line)}
@media(max-width:760px){.staff-detail{padding-top:24px}.staff-profile{grid-template-columns:1fr;min-height:0}.staff-portrait{min-height:0;aspect-ratio:4/5}.staff-story{padding:28px 22px}.staff-story h1{font-size:48px}.staff-specialties ul{grid-template-columns:1fr}.staff-actions{align-items:stretch;flex-direction:column;gap:18px;margin-top:8px}.staff-actions .card-link{justify-content:center}.staff-detail-loading{grid-template-columns:1fr;min-height:720px}.staff-detail-loading span+span{border-top:1px solid var(--line);border-left:0}}
</style>
