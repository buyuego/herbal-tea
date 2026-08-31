import type { ApiResult, TokenPair } from '@/types/api'
import { BizError, ERROR_MESSAGES, REAUTH_CODES } from '@/utils/error'

/**
 * 类型化 fetch 封装
 * - 统一 baseURL（VITE_API_BASE，开发走 Vite 代理 /api → 8080）
 * - Authorization 头自动附加（token 由 auth store 经注入函数提供，避免循环依赖）
 * - 401（40100/40101）自动 refresh 单飞重放：多个并发请求共享一次刷新
 * - 网络失败 / 超时 / 5xx 自动重试最多 3 次（指数退避）；4xx 业务错误不重试
 * - 超时 10s（AbortController）
 */

const BASE_URL = import.meta.env.VITE_API_BASE || '/api'
const TIMEOUT_MS = 10_000
const MAX_RETRY = 3

// ---- token 注入（由 auth store 注册，解耦循环依赖） ----
let accessTokenGetter: () => string | null = () => null
let refreshTokenGetter: () => string | null = () => null
let onSessionExpired: () => void = () => {}
let onTokensRefreshed: (pair: TokenPair) => void = () => {}

export function setAccessTokenGetter(fn: () => string | null) {
  accessTokenGetter = fn
}
export function setRefreshTokenGetter(fn: () => string | null) {
  refreshTokenGetter = fn
}
export function setOnSessionExpired(fn: () => void) {
  onSessionExpired = fn
}
export function setOnTokensRefreshed(fn: (pair: TokenPair) => void) {
  onTokensRefreshed = fn
}

/** 刷新单飞：并发 401 只触发一次 refresh，其余请求等待同一 promise */
let refreshing: Promise<TokenPair> | null = null

async function doRefresh(): Promise<TokenPair> {
  const refreshToken = refreshTokenGetter()
  if (!refreshToken) {
    throw new BizError(40100, '缺少刷新令牌')
  }
  const res = await fetch(`${BASE_URL}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  const body = (await res.json()) as ApiResult<TokenPair>
  if (body.code !== 0 || !body.data) {
    throw new BizError(body.code, body.message)
  }
  return body.data
}

function refreshOnce(): Promise<TokenPair> {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  /** JSON body；FormData 请自行传入 raw body */
  body?: unknown
  /** 是否跳过自动 refresh 重放（refresh 接口自身必须跳过） */
  skipAuthRetry?: boolean
  /** 业务层可关闭 5xx/网络自动重试（如轮询） */
  noRetry?: boolean
  headers?: Record<string, string>
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, skipAuthRetry = false, noRetry = false, headers = {} } = options
  const url = `${BASE_URL}${path.startsWith('/') ? path : `/${path}`}`

  const doFetch = async (withAuth: boolean, attempt: number): Promise<ApiResult<T>> => {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
    const init: RequestInit = {
      method,
      signal: controller.signal,
      headers: { ...headers },
    }
    if (withAuth) {
      const token = accessTokenGetter()
      if (token) {
        init.headers = { ...init.headers, Authorization: `Bearer ${token}` }
      }
    }
    if (body !== undefined && !(body instanceof FormData)) {
      init.headers = { ...init.headers, 'Content-Type': 'application/json' }
      init.body = JSON.stringify(body)
    } else if (body instanceof FormData) {
      init.body = body
    }

    let res: Response | null = null
    try {
      res = await fetch(url, init)
      if (res.status === 204) {
        return { code: 0, message: 'success', data: null as T }
      }
      const json = (await res.json()) as ApiResult<T>
      // 401 家族：先刷新令牌再重放一次（refresh 接口自身与显式跳过场景不触发）
      if (!skipAuthRetry && json.code !== undefined && REAUTH_CODES.includes(json.code)) {
        try {
          const pair = await refreshOnce()
          onTokensRefreshed(pair)
          return await doFetch(true, attempt + 1)
        } catch {
          onSessionExpired()
          throw new BizError(json.code, json.message)
        }
      }
      return json
    } catch (e) {
      if (e instanceof BizError) throw e
      // 网络失败 / 超时（AbortError）/ 服务端 5xx：自动重试（最多 3 次，指数退避）
      if (attempt < MAX_RETRY && !noRetry) {
        await new Promise((r) => setTimeout(r, 300 * 2 ** attempt))
        clearTimeout(timer)
        return doFetch(withAuth, attempt + 1)
      }
      throw new BizError(50000, '网络异常或服务不可用，请稍后重试')
    } finally {
      clearTimeout(timer)
    }
  }

  const result = await doFetch(true, 0)
  if (result.code === 0) return result.data
  throw new BizError(result.code, result.message || ERROR_MESSAGES[result.code])
}

// 便捷方法
export const http = {
  get: <T>(path: string, opts?: RequestOptions) => request<T>(path, { ...opts, method: 'GET' }),
  post: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    request<T>(path, { ...opts, method: 'PUT', body }),
  delete: <T>(path: string, opts?: RequestOptions) => request<T>(path, { ...opts, method: 'DELETE' }),
}
