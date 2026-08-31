import { http } from '@/utils/request'
import type { PageResult } from '@/types/product'
import type {
  StaffCreatePayload,
  StaffItem,
  StaffPageQuery,
  StaffPasswordPayload,
  StaffUpdatePayload,
} from '@/types/staff'

/** 本店员工分页（store:staff:manage） */
export const pageStaffApi = (query: StaffPageQuery) =>
  http.get<PageResult<StaffItem>>('/store/staff', { params: query })

/** 创建员工（store:staff:manage，强制 STORE_STAFF 角色并绑定本店） */
export const createStaffApi = (data: StaffCreatePayload) =>
  http.post<number>('/store/staff', data)

/** 更新员工（store:staff:manage，姓名/手机号/启用禁用） */
export const updateStaffApi = (adminId: number, data: StaffUpdatePayload) =>
  http.put<void>(`/store/staff/${adminId}`, data)

/** 重置员工密码（store:staff:manage，改密后旧令牌全部失效） */
export const resetStaffPasswordApi = (adminId: number, data: StaffPasswordPayload) =>
  http.put<void>(`/store/staff/${adminId}/password`, data)

/** 移除员工（store:staff:manage，软删绑定并即时吊销令牌；账号保留可复绑） */
export const removeStaffApi = (adminId: number) =>
  http.delete<void>(`/store/staff/${adminId}`)

export type { StaffCreatePayload, StaffItem, StaffPageQuery, StaffPasswordPayload, StaffUpdatePayload }
