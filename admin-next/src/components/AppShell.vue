<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Calendar, ChatDotRound, Close, CreditCard, DataAnalysis, Document, Menu, SetUp, ShoppingBag, SwitchButton, UploadFilled, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mobileOpen = ref(false)
const menuButton = ref<HTMLButtonElement>()
const sidebar = ref<HTMLElement>()
const mainContent = ref<HTMLElement>()
const currentPath = computed(() => route.path)
const currentTitle = computed(() => String(route.meta.title ?? '运营管理'))
const permissions = computed(() => auth.context?.permissions ?? [])
const isSuperAdmin = computed(() => auth.context?.roles.includes('SUPER_ADMIN'))
const canViewContent = computed(() => permissions.value.includes('content:view'))
const canViewMasterData = computed(() => permissions.value.some((item) => ['service:view', 'staff:view'].includes(item)))
const canViewStaff = computed(() => permissions.value.includes('staff:view'))
const canViewBooking = computed(() => permissions.value.some((item) => ['schedule:view', 'appointment:view', 'waitlist:view'].includes(item)))
const canViewBenefits = computed(() => permissions.value.some((item) => ['benefit:view', 'payment_config:view'].includes(item)))
const canViewPointsMall = computed(() => permissions.value.some((item) => ['points:view', 'mall:view'].includes(item)))
const canViewFulfillment = computed(() => permissions.value.some((item) => ['fulfillment:view', 'review:moderate', 'aftersale:view'].includes(item)))
const canViewWorkbench = computed(() => permissions.value.includes('dashboard:view'))
const canViewImport = computed(() => permissions.value.includes('import:view'))

onMounted(async () => {
  if (!auth.context) {
    try {
      await auth.refreshContext()
    } catch {
      auth.signOut()
      await router.replace({ name: 'login' })
    }
  }
})

watch(
  () => route.fullPath,
  async () => {
    mobileOpen.value = false
    document.title = `${currentTitle.value} · FACE 运营管理平台`
    await nextTick()
    mainContent.value?.focus({ preventScroll: true })
  },
  { immediate: true },
)

watch(mobileOpen, async (open) => {
  document.body.classList.toggle('mobile-nav-open', open)
  if (open) {
    await nextTick()
    sidebar.value?.querySelector<HTMLElement>('a, button')?.focus()
  }
})

onBeforeUnmount(() => document.body.classList.remove('mobile-nav-open'))

function closeMobile() {
  mobileOpen.value = false
}

async function closeMobileFromKeyboard() {
  closeMobile()
  await nextTick()
  menuButton.value?.focus()
}

async function signOut() {
  auth.signOut()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app-shell" @keydown.esc="closeMobileFromKeyboard">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <button
      ref="menuButton"
      class="mobile-menu-button"
      type="button"
      :aria-label="mobileOpen ? '关闭主导航' : '打开主导航'"
      aria-controls="admin-sidebar"
      :aria-expanded="mobileOpen"
      @click="mobileOpen = !mobileOpen"
    >
      <el-icon><Menu /></el-icon>
    </button>
    <button v-if="mobileOpen" class="sidebar-backdrop" type="button" tabindex="-1" aria-label="关闭主导航" @click="closeMobile" />
    <aside id="admin-sidebar" ref="sidebar" :class="['app-sidebar', { 'app-sidebar--open': mobileOpen }]" aria-label="主导航">
      <div class="brand-lockup">
        <span class="brand-mark" aria-hidden="true">F</span>
        <div><strong>FACE</strong><span>运营管理平台</span></div>
      </div>
      <button class="sidebar-close-button" type="button" aria-label="关闭主导航" @click="closeMobileFromKeyboard">
        <el-icon><Close /></el-icon>
      </button>

      <nav class="sidebar-nav" @click="closeMobile">
        <router-link :class="{ active: currentPath === '/' }" to="/">
          <el-icon><DataAnalysis /></el-icon><span>运营总览</span>
        </router-link>
        <router-link v-if="canViewWorkbench" :class="['mobile-priority', { active: currentPath === '/workbench' }]" to="/workbench">
          <el-icon><Calendar /></el-icon><span>今日工作台</span>
        </router-link>
        <router-link v-if="canViewContent" :class="['desktop-only-nav', { active: currentPath === '/content' }]" to="/content">
          <el-icon><Document /></el-icon><span>内容运营</span>
        </router-link>
        <router-link v-if="canViewMasterData" :class="['desktop-only-nav', { active: currentPath === '/master-data' }]" to="/master-data">
          <el-icon><SetUp /></el-icon><span>护理项目</span>
        </router-link>
        <router-link v-if="canViewStaff" :class="['desktop-only-nav', { active: currentPath === '/staff-profiles' }]" to="/staff-profiles">
          <el-icon><User /></el-icon><span>技师公开资料</span>
        </router-link>
        <router-link v-if="canViewBooking" :class="{ active: currentPath === '/booking-operations' }" to="/booking-operations">
          <el-icon><Calendar /></el-icon><span>预约与排班</span>
        </router-link>
        <router-link v-if="canViewBenefits" :class="['desktop-only-nav', { active: currentPath === '/benefits' }]" to="/benefits">
          <el-icon><CreditCard /></el-icon><span>卡项与优惠</span>
        </router-link>
        <router-link v-if="canViewPointsMall" :class="['desktop-only-nav', { active: currentPath === '/points-mall' }]" to="/points-mall">
          <el-icon><ShoppingBag /></el-icon><span>积分与商城</span>
        </router-link>
        <router-link v-if="canViewFulfillment" :class="{ active: currentPath === '/fulfillment' }" to="/fulfillment">
          <el-icon><ChatDotRound /></el-icon><span>履约与售后</span>
        </router-link>
        <router-link v-if="canViewImport" :class="['desktop-only-nav', { active: currentPath === '/data-migration' }]" to="/data-migration">
          <el-icon><UploadFilled /></el-icon><span>数据迁移</span>
        </router-link>
        <router-link v-if="isSuperAdmin" :class="['desktop-only-nav', { active: currentPath === '/settings' }]" to="/settings">
          <el-icon><SetUp /></el-icon><span>系统设置</span>
        </router-link>
      </nav>

      <button class="sign-out-button" type="button" @click="signOut">
        <el-icon><SwitchButton /></el-icon><span>退出登录</span>
      </button>
    </aside>

    <div class="app-workspace">
      <header class="app-header">
        <div>
          <span class="environment-label">{{ currentTitle }}</span>
          <strong>{{ auth.context?.username ?? '正在验证身份' }}</strong>
        </div>
        <div class="scope-summary">
          <span>{{ isSuperAdmin ? '超级管理员' : '管理员' }}</span>
          <span>{{ auth.shops[0]?.name ?? '默认门店' }}</span>
        </div>
      </header>
      <main id="main-content" ref="mainContent" class="app-main" tabindex="-1"><router-view /></main>
    </div>
  </div>
</template>

<style scoped>
.mobile-menu-button,.sidebar-backdrop,.sidebar-close-button { display: none; }
@media (max-width: 900px) {
  .mobile-menu-button { position: fixed; z-index: 30; top: max(14px, env(safe-area-inset-top)); left: max(14px, env(safe-area-inset-left)); display: grid; place-items: center; width: 44px; height: 44px; border: 1px solid var(--oc-border); border-radius: 10px; background: var(--oc-surface-1); color: var(--oc-text); }
  .sidebar-backdrop { position: fixed; z-index: 18; inset: 0; display: block; width: 100%; border: 0; background: rgba(20,13,17,.42); }
  .sidebar-close-button { position: absolute; top: max(14px, env(safe-area-inset-top)); right: 12px; display: grid; width: 44px; height: 44px; place-items: center; border: 0; border-radius: var(--oc-radius-sm); background: transparent; color: var(--oc-text-muted); }
  .app-sidebar { position: fixed; inset: 0 auto 0 0; z-index: 24; width: min(300px, calc(100vw - 48px)); flex-basis: 300px; align-items: stretch; padding-top: max(24px, env(safe-area-inset-top)); padding-bottom: max(18px, env(safe-area-inset-bottom)); transform: translateX(-105%); transition: transform .2s ease; }
  .app-sidebar .brand-lockup div, .sidebar-nav span, .sign-out-button span { display: block; }
  .app-sidebar--open { transform: translateX(0); }
  .app-header { padding-left: 72px; }
  .desktop-only-nav { display: none !important; }
}
@media (prefers-reduced-motion: reduce) { .app-sidebar { transition: none; } }
</style>
