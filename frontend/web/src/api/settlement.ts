import { http } from '@/utils/request'
import type { PageResult } from '@/types/product'
import type { SettlementDetail, SettlementPageQuery } from '@/types/settlement'

/** 结算单分页（menu:settlement；门店管理员仅本店） */
export const pageSettlementsApi = (query: SettlementPageQuery) =>
  http.get<PageResult<SettlementDetail>>('/settlement/admin/page', { params: query })

/** 结算单详情（含明细分行 D15） */
export const getSettlementDetailApi = (settlementId: number) =>
  http.get<SettlementDetail>(`/settlement/admin/${settlementId}`)

/** 生成结算单（menu:settlement；dev 造数/定时任务入口，storeId 空 = 全部门店） */
export const generateSettlementApi = (period: string, storeId?: number) =>
  http.post<void>('/settlement/admin/generate', null, { params: { period, storeId } })

/** 结算单确认（10→20；dev 手动触发，生产为 3 天自动确认） */
export const confirmSettlementApi = (settlementId: number) =>
  http.post<void>(`/settlement/admin/${settlementId}/confirm`)

/** 平台审核通过（20→30，settlement:review） */
export const reviewSettlementApi = (settlementId: number) =>
  http.post<void>(`/settlement/admin/${settlementId}/review`)

/** 打款确认（30→40，settlement:payout 敏感权限仅超管；dev 模拟分账） */
export const paySettlementApi = (settlementId: number) =>
  http.post<void>(`/settlement/admin/${settlementId}/pay`)

/** 结算异议申诉（店长本店，confirm_status→3 有异议；仅待确认/审核期） */
export const disputeSettlementApi = (settlementId: number, note: string) =>
  http.post<void>(`/settlement/admin/${settlementId}/dispute`, { note })

/** 复核生成调整单（settlement:reconcile；原单金额更新 + 生成 type=3 调整单） */
export const reconcileSettlementApi = (settlementId: number, adjustAmount: number, remark?: string) =>
  http.post<number>(`/settlement/admin/${settlementId}/reconcile`, { adjustAmount, remark })

export type { SettlementDetail, SettlementPageQuery }
