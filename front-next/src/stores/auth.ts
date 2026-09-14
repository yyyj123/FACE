import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api, SESSION_KEY } from '../api/client'
import { EXPECTED_PORTAL_ROLE, IS_TECHNICIAN_PORTAL } from '../config/portal'
import type { AuthSession, ClientProfile } from '../types/domain'

function readSession() {
  const raw = localStorage.getItem(SESSION_KEY)
  if (!raw) return null
  try {
    const session = JSON.parse(raw) as AuthSession
    if (session.role !== EXPECTED_PORTAL_ROLE) {
      localStorage.removeItem(SESSION_KEY)
      return null
    }
    return session
  } catch {
    localStorage.removeItem(SESSION_KEY)
    return null
  }
}

export const useAuthStore = defineStore('client-auth', () => {
  const session = ref<AuthSession | null>(readSession())
  const profile = ref<ClientProfile | null>(null)
  const isSignedIn = computed(() => Boolean(session.value?.token))
  const isTechnician = computed(() => session.value?.role === 'BEAUTICIAN')

  function saveSession(result: AuthSession) {
    if (result.role !== EXPECTED_PORTAL_ROLE) {
      const message = IS_TECHNICIAN_PORTAL
        ? '此入口仅限技师账号，请使用会员端或管理端登录'
        : '技师账号请使用独立技师端登录'
      throw new Error(message)
    }
    session.value = result
    localStorage.setItem(SESSION_KEY, JSON.stringify(result))
  }

  async function signIn(phone: string, password: string) {
    const result = await api.login({ phone, password })
    saveSession(result)
    profile.value = await api.me()
    return result
  }

  async function signInWithSms(phone: string, code: string) {
    const result = await api.smsLogin({ phone, code })
    saveSession(result)
    profile.value = await api.me()
    return result
  }

  async function register(phone: string, code: string, password: string, name: string) {
    const result = await api.register({ phone, code, password, name })
    saveSession(result)
    profile.value = await api.me()
    return result
  }

  async function refreshProfile() {
    if (!session.value) return null
    profile.value = await api.me()
    return profile.value
  }

  function signOut() {
    session.value = null
    profile.value = null
    localStorage.removeItem(SESSION_KEY)
  }

  return {
    session, profile, isSignedIn, isTechnician,
    signIn, signInWithSms, register, signOut, refreshProfile,
  }
})
