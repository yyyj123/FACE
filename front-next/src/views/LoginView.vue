<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Check } from '@element-plus/icons-vue'
import { api } from '../api/client'
import { useAuthStore } from '../stores/auth'

type Mode = 'password' | 'sms' | 'register' | 'reset'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mode = ref<Mode>('password')
const form = reactive({ phone: '', password: '', code: '', name: '' })
const submitting = ref(false)
const sending = ref(false)
const error = ref('')
const notice = ref('')
const countdown = ref(0)
let timer: number | undefined

const title = computed(() => ({
  password: '手机号密码登录',
  sms: '短信验证码登录',
  register: '创建会员账号',
  reset: '重置登录密码',
})[mode.value])

const phoneValid = computed(() => /^1[3-9]\d{9}$/.test(form.phone.trim()))
const needsPassword = computed(() => mode.value !== 'sms')
const needsCode = computed(() => mode.value !== 'password')

function switchMode(next: Mode) {
  mode.value = next
  error.value = ''
  notice.value = ''
  form.code = ''
}

async function sendCode() {
  if (!phoneValid.value || countdown.value > 0) return
  sending.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await api.requestSms({
      phone: form.phone.trim(),
      purpose: mode.value === 'reset' ? 'PASSWORD_RESET' : 'REGISTER_LOGIN',
    })
    notice.value = result.demo_code
      ? `${result.message}，演示验证码：${result.demo_code}`
      : result.message
    countdown.value = result.retry_after_seconds || 60
    timer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) window.clearInterval(timer)
    }, 1000)
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '验证码发送失败'
  } finally {
    sending.value = false
  }
}

async function submit() {
  submitting.value = true
  error.value = ''
  notice.value = ''
  try {
    const phone = form.phone.trim()
    if (mode.value === 'password') await auth.signIn(phone, form.password)
    if (mode.value === 'sms') await auth.signInWithSms(phone, form.code)
    if (mode.value === 'register') {
      await auth.register(phone, form.code, form.password, form.name.trim())
    }
    if (mode.value === 'reset') {
      await api.resetPassword({ phone, code: form.code, password: form.password })
      switchMode('password')
      notice.value = '密码已重置，请使用新密码登录。'
      return
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/profile'
    await router.replace(redirect)
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '操作失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <main class="auth-page">
    <router-link class="auth-back" to="/">
      <ArrowLeft />
      返回首页
    </router-link>

    <section class="auth-visual" aria-label="FACE 到店护理">
      <div class="auth-brand">
        <span class="brand-mark">F</span>
        <div><strong>FACE</strong><small>BEAUTY & CARE</small></div>
      </div>
      <div class="auth-message">
        <span class="eyebrow">CARE, CLEARLY ARRANGED</span>
        <h1>护理、时间与会员记录，都在同一个手机号里。</h1>
        <ul>
          <li><Check />浏览项目与技师公开资料</li>
          <li><Check />门店信息由服务端统一确认</li>
          <li><Check />手机号身份可复用于后续微信小程序</li>
        </ul>
      </div>
    </section>

    <section class="auth-form-section" aria-labelledby="auth-title">
      <div class="auth-form-wrap">
        <div class="mode-tabs" role="tablist" aria-label="登录方式">
          <button type="button" :class="{ active: mode === 'password' }" @click="switchMode('password')">密码登录</button>
          <button type="button" :class="{ active: mode === 'sms' }" @click="switchMode('sms')">验证码登录</button>
        </div>

        <div class="auth-heading">
          <span class="eyebrow">MEMBER IDENTITY</span>
          <h2 id="auth-title">{{ title }}</h2>
          <p>使用中国大陆手机号登录。短信验证码在生产环境中由已配置的短信服务商发送。</p>
        </div>

        <p v-if="error" class="form-message form-message--error" role="alert">{{ error }}</p>
        <p v-if="notice" class="form-message form-message--success" role="status">{{ notice }}</p>

        <form class="auth-form" @submit.prevent="submit">
          <div class="field">
            <label for="identity-phone">手机号</label>
            <input
              id="identity-phone"
              v-model="form.phone"
              class="form-control"
              type="tel"
              inputmode="numeric"
              autocomplete="tel"
              maxlength="11"
              placeholder="请输入 11 位手机号"
              required
            />
          </div>

          <div v-if="mode === 'register'" class="field">
            <label for="identity-name">称呼</label>
            <input id="identity-name" v-model="form.name" class="form-control" autocomplete="name" maxlength="80" placeholder="选填" />
          </div>

          <div v-if="needsCode" class="field">
            <label for="identity-code">验证码</label>
            <div class="code-row">
              <input id="identity-code" v-model="form.code" class="form-control" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="6 位验证码" required />
              <button class="code-button" type="button" :disabled="!phoneValid || sending || countdown > 0" @click="sendCode">
                {{ countdown > 0 ? `${countdown}s` : sending ? '发送中' : '获取验证码' }}
              </button>
            </div>
          </div>

          <div v-if="needsPassword" class="field">
            <label for="identity-password">{{ mode === 'reset' ? '新密码' : '密码' }}</label>
            <input id="identity-password" v-model="form.password" class="form-control" type="password" :autocomplete="mode === 'password' ? 'current-password' : 'new-password'" minlength="8" maxlength="200" placeholder="至少 8 位字符" required />
          </div>

          <button class="button button-primary auth-submit" type="submit" :disabled="submitting || !phoneValid">
            {{ submitting ? '正在处理…' : title }}
            <ArrowRight />
          </button>
        </form>

        <div class="auth-links">
          <button v-if="mode !== 'register'" type="button" @click="switchMode('register')">首次使用？注册会员</button>
          <button v-if="mode !== 'reset'" type="button" @click="switchMode('reset')">忘记密码</button>
          <button v-if="mode === 'register' || mode === 'reset'" type="button" @click="switchMode('password')">返回登录</button>
        </div>

        <a class="admin-entry" href="http://127.0.0.1:8281">
          运营管理人员？前往运营管理平台
          <ArrowRight />
        </a>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-page { min-height: 100vh; display: grid; grid-template-columns: minmax(420px, 1.05fr) minmax(440px, .95fr); background: var(--page); }
.auth-back { position: fixed; z-index: 5; top: 24px; left: 24px; display: inline-flex; align-items: center; gap: 8px; padding: 9px 12px; border: 1px solid rgba(255,255,255,.2); border-radius: 8px; color: #fff; background: rgba(10,7,10,.48); backdrop-filter: blur(8px); font-size: 12px; }
.auth-back svg, .auth-submit svg, .admin-entry svg { width: 16px; }
.auth-visual { min-height: 100vh; display: flex; flex-direction: column; justify-content: space-between; padding: 40px clamp(42px,6vw,92px) 68px; color: #fff; background: linear-gradient(0deg,rgba(12,8,12,.94),rgba(12,8,12,.14) 78%),linear-gradient(90deg,rgba(12,8,12,.25),transparent),url('/images/auth-salon.webp') center/cover; }
.auth-brand { display: flex; align-items: center; gap: 11px; margin-left: auto; }
.auth-brand > div { display: grid; gap: 2px; }
.auth-brand strong { letter-spacing: .18em; }
.auth-brand small { color: rgba(255,255,255,.6); font-size: 9px; letter-spacing: .14em; }
.auth-message { max-width: 650px; }
.auth-message h1 { margin: 13px 0 28px; font-size: clamp(40px,5vw,68px); line-height: 1.08; letter-spacing: -.05em; }
.auth-message ul { display: grid; gap: 11px; margin: 0; padding: 0; list-style: none; color: #e2d6da; }
.auth-message li { display: flex; align-items: center; gap: 9px; }
.auth-message li svg { width: 16px; color: var(--copper); }
.auth-form-section { min-height: 100vh; display: grid; place-items: center; padding: 60px clamp(28px,6vw,84px); }
.auth-form-wrap { width: min(480px,100%); }
.mode-tabs { display: grid; grid-template-columns: 1fr 1fr; margin-bottom: 34px; border-bottom: 1px solid var(--line); }
.mode-tabs button { min-height: 44px; border: 0; border-bottom: 2px solid transparent; color: var(--text-muted); background: transparent; cursor: pointer; }
.mode-tabs button.active { border-color: var(--rose); color: var(--text); }
.auth-heading { margin-bottom: 24px; }
.auth-heading h2 { margin: 9px 0 11px; font-size: 34px; letter-spacing: -.035em; }
.auth-heading p { margin: 0; color: var(--text-muted); line-height: 1.75; }
.auth-form { display: grid; gap: 18px; }
.code-row { display: grid; grid-template-columns: 1fr 122px; gap: 10px; }
.code-button { min-height: 44px; border: 1px solid var(--line); border-radius: 8px; color: var(--text); background: var(--surface); cursor: pointer; }
.code-button:disabled { cursor: not-allowed; opacity: .55; }
.auth-submit { width: 100%; margin-top: 4px; }
.form-message { margin: 0 0 16px; padding: 12px 14px; border-radius: 8px; line-height: 1.5; }
.form-message--error { color: #8b2b37; background: #fff0f2; }
.form-message--success { color: #315f4a; background: #edf8f2; }
.auth-links { display: flex; flex-wrap: wrap; gap: 8px 20px; margin-top: 18px; }
.auth-links button { min-height: 36px; padding: 0; border: 0; color: var(--rose); background: transparent; cursor: pointer; }
.admin-entry { display: flex; align-items: center; justify-content: space-between; margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--line); color: var(--text-muted); font-size: 12px; }
@media (max-width:900px) { .auth-page { grid-template-columns: 1fr; } .auth-visual { min-height: 420px; } .auth-form-section { min-height: auto; } }
@media (max-width:640px) { .auth-visual { min-height: 320px; padding: 28px 20px 34px; } .auth-brand,.auth-message ul { display: none; } .auth-message h1 { font-size: 36px; } .auth-form-section { padding: 36px 20px 58px; } .code-row { grid-template-columns: minmax(0,1fr) 112px; } }
@media (prefers-reduced-motion: reduce) { * { scroll-behavior: auto !important; transition-duration: .01ms !important; } }
</style>
