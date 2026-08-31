/**
 * 保证金管理类型（对应后端 StoreController 保证金收退接口，权限 store:deposit:confirm 221 敏感仅超管）
 */

/** 流水类型：1缴纳 / 2退还 */
export const DEPOSIT_TYPE: Record<number, string> = {
  1: '缴纳',
  2: '退还',
}

/** 流水类型 tag 样式 */
export const DEPOSIT_TYPE_TAG: Record<number, 'success' | 'warning'> = {
  1: 'success',
  2: 'warning',
}

/** 流水状态：0待处理 / 1完成 */
export const DEPOSIT_STATUS: Record<number, string> = {
  0: '待处理',
  1: '完成',
}

/** 流水状态 tag 样式 */
export const DEPOSIT_STATUS_TAG: Record<number, 'warning' | 'success'> = {
  0: 'warning',
  1: 'success',
}

/** 保证金流水 VO（franchise_deposits 联查 stores） */
export interface DepositItem {
  /** franchise_deposits.id */
  id: number
  /** 门店 */
  storeId: number
  /** 门店编号 */
  storeNo: string | null
  /** 门店名称 */
  storeName: string | null
  /** 1缴纳 / 2退还 */
  type: number
  /** 金额 */
  amount: number | string
  /** 0待处理 / 1完成 */
  status: number
  /** 关联单号（缴纳 FR-{申请}；退还同号关联） */
  bizNo: string | null
  /** 缴纳时间（财务确认收款时落库） */
  paidAt: string | null
  /** 退还时间（财务确认退还时落库） */
  refundedAt: string | null
  /** 流水创建时间 */
  createdAt: string | null
}

/** 保证金流水分页查询参数（type/status/storeId 均可空 = 不过滤） */
export type DepositPageQuery = {
  type?: number
  status?: number
  storeId?: number
  page: number
  size: number
}
