import { http } from '@/utils/request'
import type { AdminProfile, TokenPair } from '@/types/api'

export interface LoginParams {
  username: string
  password: string
}

/** B 端管理员登录（失败 5 次锁 15 分钟） */
export const loginApi = (params: LoginParams) =>
  http.post<TokenPair>('/auth/admin/login', params, { skipAuthRetry: true })

/** 刷新令牌轮换（旧 refresh 作废） */
export const refreshApi = (refreshToken: string) =>
  http.post<TokenPair>('/auth/refresh', { refreshToken }, { skipAuthRetry: true })

/** 登出（token_version +1 即时吊销） */
export const logoutApi = () => http.post<void>('/auth/logout')

/** 当前管理员信息（角色 + 权限码列表，驱动菜单/路由过滤） */
export const meApi = () => http.get<AdminProfile>('/auth/admin/me')
