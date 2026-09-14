import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getAccessibleShops,
  getTenantContext,
  login as loginRequest,
  type ShopSummary,
  type TenantContext,
} from '../services/api'

const TOKEN_KEY = 'face-chain-token'
const V3_ACCESS_TOKEN_KEY = 'face-chain-v3-access-token'
const V3_REFRESH_TOKEN_KEY = 'face-chain-v3-refresh-token'
let contextRefresh: Promise<void> | null = null

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) ?? '')
  const context = ref<TenantContext | null>(null)
  const shops = ref<ShopSummary[]>([])
  const loading = ref(false)
  const error = ref('')

  const signedIn = computed(() => Boolean(token.value))

  async function signIn(username: string, password: string) {
    loading.value = true
    error.value = ''
    try {
      const result = await loginRequest(username, password)
      token.value = result.token
      localStorage.setItem(TOKEN_KEY, result.token)
      localStorage.setItem(V3_ACCESS_TOKEN_KEY, result.v3AccessToken)
      localStorage.setItem(V3_REFRESH_TOKEN_KEY, result.v3RefreshToken)
      await refreshContext()
      if (!context.value?.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role))) {
        throw new Error('当前入口仅允许 ADMIN 或 SUPER_ADMIN 登录')
      }
    } catch (reason) {
      signOut()
      error.value = reason instanceof Error ? reason.message : '登录失败，请稍后重试'
      throw reason
    } finally {
      loading.value = false
    }
  }

  async function refreshContext() {
    if (contextRefresh) return contextRefresh
    contextRefresh = (async () => {
      const [nextContext, nextShops] = await Promise.all([
        getTenantContext(),
        getAccessibleShops(),
      ])
      context.value = nextContext
      shops.value = nextShops
    })()
    try {
      await contextRefresh
    } finally {
      contextRefresh = null
    }
  }

  function signOut() {
    token.value = ''
    context.value = null
    shops.value = []
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(V3_ACCESS_TOKEN_KEY)
    localStorage.removeItem(V3_REFRESH_TOKEN_KEY)
  }

  return {
    token,
    context,
    shops,
    loading,
    error,
    signedIn,
    signIn,
    refreshContext,
    signOut,
  }
})
