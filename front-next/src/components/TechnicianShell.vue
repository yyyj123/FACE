<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Bell, Briefcase, Calendar, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

async function signOut() {
  auth.signOut()
  await router.push('/login')
}
</script>

<template>
  <div class="client-shell technician-shell">
    <header class="site-header technician-header">
      <router-link class="brand" to="/workbench" aria-label="FACE 技师工作台">
        <span class="brand-mark">F</span>
        <span class="brand-copy">
          <strong>FACE</strong>
          <small>技师工作台</small>
        </span>
      </router-link>

      <nav class="desktop-nav" aria-label="技师端主导航">
        <router-link to="/workbench">工作台</router-link>
        <router-link to="/appointments">我的预约</router-link>
        <router-link to="/notifications">消息</router-link>
        <router-link to="/profile">个人资料</router-link>
      </nav>

      <div class="header-actions">
        <router-link class="profile-link" to="/profile">
          <span class="profile-dot" aria-hidden="true" />
          {{ auth.profile?.name || auth.session?.username || '技师' }}
        </router-link>
        <button class="text-button" type="button" @click="signOut">退出</button>
      </div>
    </header>

    <main id="main-content" class="page-content">
      <router-view />
    </main>

    <footer class="site-footer technician-footer">
      <div>
        <strong>FACE 技师端</strong>
        <span>仅展示与你本人相关的工作数据</span>
      </div>
      <p>预约、护理进度、培训与业绩统一同步</p>
    </footer>

    <nav class="mobile-nav mobile-nav--member" aria-label="技师端移动导航">
      <router-link to="/workbench">
        <Briefcase />
        <span>工作台</span>
      </router-link>
      <router-link to="/appointments">
        <Calendar />
        <span>预约</span>
      </router-link>
      <router-link to="/notifications">
        <Bell />
        <span>消息</span>
      </router-link>
      <router-link to="/profile">
        <UserFilled />
        <span>我的</span>
      </router-link>
    </nav>
  </div>
</template>

<style scoped>
.technician-header {
  border-bottom-color: rgba(177, 79, 107, 0.22);
}

.technician-footer {
  border-top-color: rgba(177, 79, 107, 0.22);
}
</style>
