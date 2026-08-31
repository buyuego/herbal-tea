import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { loginApi, logoutApi, meApi, refreshApi } from '@/api/auth'
import { myStoresApi, switchStoreApi } from '@/api/store'
import {
  setAccessTokenGetter,
  setOnSessionExpired,
  setOnTokensRefreshed,
  setRefreshTokenGetter,
} from '@/utils/request'
import type { AdminProfile, StoreBinding, TokenPair } from '@/types/api'

const REFRESH_KEY = 'ht_admin_refresh_token'

/**
 * 认证状态：
 * - accessToken 只存内存（短时效 2h，页面刷新后经 bootstrap 静默换新）
 * - refreshToken 存 localStorage（30d 轮换，后端已有 token_version 吊销兜底）
 */
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_KEY))
  const profile = ref<AdminProfile | null>(null)
  const stores = ref<StoreBinding[]>([])
  const bootstrapped = ref(false)

  const isLoggedIn = computed(() => accessToken.value !== null)
  /** 当前上下文门店（my-stores 中 current=true 项） */
  const currentStore = computed(() => stores.value.find((s) => s.current) ?? null)
  const hasPermission = (code: string) => !!profile.value?.permissionCodes.includes(code)

  // ---- request 层注入：token 读取 / 刷新成功回写 / 会话过期跳登录 ----
  setAccessTokenGetter(() => accessToken.value)
  setRefreshTokenGetter(() => refreshToken.value)
  setOnTokensRefreshed((pair) => applyTokenPair(pair))
  setOnSessionExpired(() => {
    reset()
  })

  function applyTokenPair(pair: TokenPair) {
    accessToken.value = pair.accessToken
    refreshToken.value = pair.refreshToken
    localStorage.setItem(REFRESH_KEY, pair.refreshToken)
  }

  async function loadProfile() {
    profile.value = await meApi()
  }

  async function loadStores() {
    stores.value = await myStoresApi()
  }

  /** 登录：换新令牌 + 拉取资料与门店列表 */
  async function login(username: string, password: string) {
    const pair = await loginApi({ username, password })
    applyTokenPair(pair)
    await Promise.all([loadProfile(), loadStores()])
  }

  /** 切换当前门店：后端重签令牌（sid=目标店、sids=全量）后刷新本地会话 */
  async function switchStore(storeId: number) {
    const pair = await switchStoreApi(storeId)
    applyTokenPair(pair)
    await Promise.all([loadProfile(), loadStores()])
  }

  /** 启动恢复：有 refreshToken 则静默刷新恢复会话（页面刷新不丢登录态与切店状态） */
  async function bootstrap() {
    if (bootstrapped.value) return
    bootstrapped.value = true
    if (!refreshToken.value) return
    try {
      const pair = await refreshApi(refreshToken.value)
      applyTokenPair(pair)
      await Promise.all([loadProfile(), loadStores()])
    } catch {
      reset()
    }
  }

  /** 登出：后端吊销（token_version +1）后清空本地会话 */
  async function logout() {
    try {
      await logoutApi()
    } finally {
      reset()
    }
  }

  function reset() {
    accessToken.value = null
    profile.value = null
    stores.value = []
    localStorage.removeItem(REFRESH_KEY)
    refreshToken.value = null
  }

  return {
    accessToken,
    refreshToken,
    profile,
    stores,
    bootstrapped,
    isLoggedIn,
    currentStore,
    hasPermission,
    login,
    switchStore,
    bootstrap,
    logout,
    reset,
  }
})
