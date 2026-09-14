<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const form = reactive({
  username: 'admin',
  password: '',
})

async function submit() {
  if (!form.username.trim() || !form.password) return
  try {
    await auth.signIn(form.username.trim(), form.password)
    await router.replace({ name: 'overview' })
  } catch {
    // 错误由 store 显示并保留用户输入。
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-intro">
      <div class="brand-lockup login-brand">
        <span class="brand-mark" aria-hidden="true">F</span>
        <div>
          <strong>FACE</strong>
          <span>连锁管理平台</span>
        </div>
      </div>
      <div>
        <p class="environment-label">SC2 单店运营入口</p>
        <h1>内容、项目与公开资料，在一个清晰的工作台维护。</h1>
        <p>平台从登录身份中解析权限和默认门店，不接受前端自行指定业务范围。</p>
      </div>
      <ul class="login-proof-list">
        <li><span>01</span>ADMIN 与 SUPER_ADMIN 两级权限</li>
        <li><span>02</span>首页内容发布状态可追溯</li>
        <li><span>03</span>默认门店由服务端统一确认</li>
      </ul>
    </section>

    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-form-wrap">
        <p class="environment-label">管理员验证</p>
        <h2 id="login-title">登录运营管理平台</h2>
        <p class="form-helper">仅 ADMIN 或 SUPER_ADMIN 账号可以进入。</p>

        <el-alert
          v-if="auth.error"
          class="login-alert"
          :title="auth.error"
          type="error"
          :closable="false"
          show-icon
        />

        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input
              v-model="form.username"
              autocomplete="username"
              :prefix-icon="User"
              placeholder="请输入管理员账号"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              :prefix-icon="Lock"
              placeholder="请输入密码"
              show-password
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button
            class="login-submit"
            type="primary"
            native-type="submit"
            :loading="auth.loading"
            :disabled="!form.username.trim() || !form.password"
          >
            验证并进入
          </el-button>
        </el-form>
      </div>
    </section>
  </main>
</template>
