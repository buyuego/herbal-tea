import { http } from '@/utils/request'
import type { PageResult } from '@/types/product'
import type { DepositItem, DepositPageQuery } from '@/types/deposit'

/** 保证金流水分页（store:deposit:confirm 敏感权限；type 1缴纳/2退还、status 0待处理/1完成） */
export const pageDepositsApi = (query: DepositPageQuery) =>
  http.get<PageResult<DepositItem>>('/store/admin/deposits', { params: query })

/** 确认收款（缴纳流水 0待处理 → 1完成 + paid_at；重复确认 40900） */
export const confirmDepositApi = (depositId: number) =>
  http.post<void>(`/store/admin/deposits/${depositId}/confirm`)

/** 退还保证金（对已确认收款的缴纳流水全额退还，写入退还流水 type=2；未确认/已退 40900） */
export const refundDepositApi = (depositId: number) =>
  http.post<void>(`/store/admin/deposits/${depositId}/refund`)
