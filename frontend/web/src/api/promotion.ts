import { http } from '@/utils/request'
import type { PageResult } from '@/types/product'
import type { PromotionQuery, PromotionSavePayload, PromotionVO } from '@/types/promotion'

/** 活动分页（menu:marketing） */
export const pagePromotionsApi = (query: PromotionQuery) =>
  http.get<PageResult<PromotionVO>>('/marketing/admin/promotions', { params: query })

/** 活动详情（menu:marketing） */
export const getPromotionApi = (id: number) => http.get<PromotionVO>(`/marketing/admin/promotions/${id}`)

/** 创建活动（marketing:promotion，门店账号只能建本店活动） */
export const createPromotionApi = (data: PromotionSavePayload) =>
  http.post<number>('/marketing/admin/promotions', data)

/** 编辑活动（marketing:promotion，仅草稿可编辑） */
export const updatePromotionApi = (id: number, data: PromotionSavePayload) =>
  http.put<void>(`/marketing/admin/promotions/${id}`, data)

/** 发布（0→1） */
export const publishPromotionApi = (id: number) =>
  http.post<void>(`/marketing/admin/promotions/${id}/publish`)

/** 结束（1→2） */
export const stopPromotionApi = (id: number) => http.post<void>(`/marketing/admin/promotions/${id}/stop`)

export type { PromotionQuery, PromotionSavePayload, PromotionVO }
