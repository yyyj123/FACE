<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Calendar,
  ChatDotRound,
  HomeFilled,
  List,
  ShoppingBag,
  UserFilled,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const mainContent = ref<HTMLElement>()

watch(
  () => route.fullPath,
  async () => {
    document.title = `${String(route.meta.title ?? '会员服务')} · FACE`
    await nextTick()
    mainContent.value?.focus({ preventScroll: true })
  },
  { immediate: true },
)

async function signOut() {
  auth.signOut()
  await router.push('/')
}
</script>

<template>
  <div class="client-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <header class="site-header">
      <router-link class="brand" to="/" aria-label="FACE 首页">
        <span class="brand-mark">F</span>
        <span class="brand-copy">
          <strong>FACE</strong>
          <small>BEAUTY & CARE</small>
        </span>
      </router-link>

      <nav class="desktop-nav" aria-label="主导航">
        <router-link to="/">首页</router-link>
        <router-link to="/services">护理项目</router-link>
        <router-link to="/staff-schedule">技师排班</router-link>
        <router-link to="/booking">立即预约</router-link>
        <router-link v-if="auth.isSignedIn" to="/appointments">预约记录</router-link>
        <router-link v-if="auth.isSignedIn" to="/benefits">我的资产</router-link>
        <router-link v-if="auth.isSignedIn" to="/points-store">积分商城</router-link>
        <router-link v-if="auth.isSignedIn" to="/care-feedback">评价与售后</router-link>
        <router-link v-if="auth.isSignedIn" to="/notifications">消息</router-link>
      </nav>

      <div class="header-actions">
        <router-link v-if="!auth.isSignedIn" class="text-link" to="/login">登录</router-link>
        <router-link v-else class="profile-link" to="/profile">
          <span class="profile-dot" aria-hidden="true" />
          {{ auth.profile?.name || auth.session?.username }}
        </router-link>
        <button v-if="auth.isSignedIn" class="text-button" type="button" @click="signOut">退出</button>
        <router-link v-else class="button button-primary button-small" to="/booking">预约护理</router-link>
      </div>
    </header>

    <main id="main-content" ref="mainContent" class="page-content" tabindex="-1">
      <router-view />
    </main>

    <footer class="site-footer">
      <div>
        <strong>FACE</strong>
        <span>让每一次护理都有清晰记录</span>
      </div>
      <p>项目、预约与护理进度统一同步到门店系统</p>
    </footer>

    <nav :class="['mobile-nav', { 'mobile-nav--member': auth.isSignedIn }]" aria-label="移动端主导航">
      <router-link to="/">
        <HomeFilled />
        <span>首页</span>
      </router-link>
      <router-link to="/services">
        <List />
        <span>项目</span>
      </router-link>
      <router-link to="/staff-schedule">
        <Calendar />
        <span>排班</span>
      </router-link>
      <router-link v-if="auth.isSignedIn" to="/points-store">
        <ShoppingBag />
        <span>商城</span>
      </router-link>
      <router-link v-if="auth.isSignedIn" to="/care-feedback?section=reviews">
        <ChatDotRound />
        <span>评价</span>
      </router-link>
      <router-link :to="auth.isSignedIn ? '/profile' : '/login'">
        <UserFilled />
        <span>我的</span>
      </router-link>
    </nav>
  </div>
</template>
