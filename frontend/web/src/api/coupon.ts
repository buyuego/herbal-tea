import { http } from '@/utils/request'
import type { PageResult } from '@/types/product'
import type { CouponQuery, CouponSavePayload, CouponVO, UserCouponVO } from '@/types/coupon'

/** 券模板分页（menu:marketing） */
export const pageCouponsApi = (query: CouponQuery) =>
  http.get<PageResult<CouponVO>>('/marketing/admin/coupons', { params: query })

/** 券模板详情（menu:marketing） */
export const getCouponApi = (id: number) => http.get<CouponVO>(`/marketing/admin/coupons/${id}`)

/** 创建券模板（marketing:coupon，门店账号只能建本店券） */
export const createCouponApi = (data: CouponSavePayload) =>
  http.post<number>('/marketing/admin/coupons', data)

/** 编辑券模板（marketing:coupon，仅未发布且无人领取） */
export const updateCouponApi = (id: number, data: CouponSavePayload) =>
  http.put<void>(`/marketing/admin/coupons/${id}`, data)

/** 发布（0→1） */
export const publishCouponApi = (id: number) => http.post<void>(`/marketing/admin/coupons/${id}/publish`)

/** 停止发放（1→2） */
export const stopCouponApi = (id: number) => http.post<void>(`/marketing/admin/coupons/${id}/stop`)

/** 券领取记录（menu:marketing） */
export const pageCouponGrantsApi = (id: number, page: number, size: number) =>
  http.get<PageResult<UserCouponVO>>(`/marketing/admin/coupons/${id}/grants`, { params: { page, size } })

/** 发券给指定会员（marketing:coupon） */
export const grantCouponApi = (id: number, userId: number) =>
  http.post<number>(`/marketing/admin/coupons/${id}/grant`, undefined, { params: { userId } })

/** 会员持券列表（menu:marketing） */
export const pageUserCouponsApi = (userId: number, status?: number, page = 1, size = 10) =>
  http.get<PageResult<UserCouponVO>>(`/marketing/admin/members/${userId}/coupons`, {
    params: { status, page, size },
  })

export type { CouponQuery, CouponSavePayload, CouponVO, UserCouponVO }
