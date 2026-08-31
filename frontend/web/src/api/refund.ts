import { http } from '@/utils/request'
import type { PageResult } from '@/types/product'
import type {
  InspectPayload,
  RefundApplyPayload,
  RefundDetail,
  RefundItem,
  RefundPageQuery,
  RejectPayload,
} from '@/types/refund'

/** 退款单分页（menu:refund；门店管理员仅本店） */
export const pageRefundsApi = (query: RefundPageQuery) =>
  http.get<PageResult<RefundItem>>('/refund/admin/page', { params: query })

/** 退款单详情（menu:refund） */
export const getRefundDetailApi = (refundId: number) =>
  http.get<RefundDetail>(`/refund/admin/${refundId}`)

/** 退款申请（refund:submit，订单 20/30/40/50 → 60 退款中，分支 3 自动建退货单） */
export const applyRefundApi = (data: RefundApplyPayload) =>
  http.post<number>('/refund/admin/apply', data)

/** 退款审批通过（refund:approve，10→20；未发货/在途直退，已签收等待验货） */
export const approveRefundApi = (refundId: number) =>
  http.post<void>(`/refund/admin/${refundId}/approve`)

/** 退款驳回（refund:approve，10→50；订单按分支恢复原阶段） */
export const rejectRefundApi = (refundId: number, data: RejectPayload) =>
  http.post<void>(`/refund/admin/${refundId}/reject`, data)

/** 退货总部收货（return:inspect；1 待收货 → 2 已收货，之后方可验货） */
export const receiveRefundApi = (refundId: number) =>
  http.post<void>(`/refund/admin/${refundId}/return/receive`)

/** 退货验货（return:inspect；验货通过推进退款，不通过退款驳回） */
export const inspectRefundApi = (refundId: number, data: InspectPayload) =>
  http.post<void>(`/refund/admin/${refundId}/return/inspect`, data)

export type { InspectPayload, RefundApplyPayload, RefundDetail, RefundItem, RefundPageQuery, RejectPayload }
