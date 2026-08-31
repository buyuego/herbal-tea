/** 结算单状态（10待确认→20平台审核→30已结算→40已打款 / 90已冲正） */
export const SETTLEMENT_STATUS: Record<number, string> = {
  10: '待确认',
  20: '平台审核',
  30: '已结算',
  40: '已打款',
  90: '已冲正',
}

/** 结算单状态 tag 类型（Element Plus） */
export const SETTLEMENT_STATUS_TAG: Record<number, 'info' | 'warning' | 'primary' | 'success' | 'danger'> = {
  10: 'info',
  20: 'warning',
  30: 'primary',
  40: 'success',
  90: 'danger',
}

/** 结算单类型 */
export const SETTLEMENT_TYPE: Record<number, string> = {
  1: '日结 T+1',
  2: '周结',
  3: '调整单',
}

/** 确认维度（与 status 并行） */
export const CONFIRM_STATUS: Record<number, string> = {
  0: '待确认',
  1: '自动确认',
  2: '人工确认',
  3: '有异议',
}

/** 明细行类型（D15 分行） */
export const ITEM_TYPE: Record<number, string> = {
  1: '订单销售额',
  2: '平台佣金',
  3: '门店营销积分抵扣',
  4: '门店营销积分成本',
  5: '平台补贴积分',
  6: '本店券成本',
  7: '退款冲正',
  8: '调整单',
}

/** 明细行方向 */
export const ITEM_DIRECTION: Record<number, string> = {
  1: '店铺加项',
  2: '店铺减项',
  3: '平台承担',
}

/** 结算单分页行 */
export interface SettlementItem {
  id: number
  settlementId: number
  orderId: number | null
  orderNo: string | null
  itemType: number
  direction: number
  amount: string
  remark: string | null
}

/** 结算单详情（结算单 + 门店 + 明细分行） */
export interface SettlementDetail {
  id: number
  settleNo: string
  storeId: number
  storeName: string
  period: string
  type: number
  orderCount: number
  totalAmount: string
  commissionAmount: string
  pointsDeductAmount: string
  pointsCostStore: string
  pointsCostPlatform: string
  couponCostStore: string
  refundAdjust: string
  adjustAmount: string
  finalAmount: string
  confirmStatus: number
  status: number
  statusDesc: string
  autoConfirmAt: string | null
  confirmedAt: string | null
  disputeNote: string | null
  reviewedBy: number | null
  paidAt: string | null
  payoutNo: string | null
  createdAt: string
  version: number
  /** 关联原结算单（type=3 调整单用） */
  parentSettlementId: number | null
  items: SettlementItem[]
}

/** 结算单分页查询 */
export type SettlementPageQuery = {
  settleNo?: string
  storeId?: number
  status?: number
  period?: string
  page: number
  size: number
}
