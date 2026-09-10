/** 促销活动（v30，B 端 105 menu:marketing / 212 marketing:promotion） */

/** 活动类型 */
export const PROMOTION_TYPE: Record<number, string> = {
  1: '满减',
  2: '折扣',
  3: '限时购',
}

/** 活动归属（决定成本承担方） */
export const PROMOTION_SCOPE: Record<number, string> = {
  1: '平台活动',
  2: '本店活动',
}

export const PROMOTION_SCOPE_TAG: Record<number, 'warning' | 'success'> = {
  1: 'warning', // 平台活动：平台承担
  2: 'success', // 本店活动：店铺承担
}

/** 活动状态 */
export const PROMOTION_STATUS: Record<number, string> = {
  0: '草稿',
  1: '进行中',
  2: '已结束',
}

export const PROMOTION_STATUS_TAG: Record<number, 'info' | 'success' | 'danger'> = {
  0: 'info',
  1: 'success',
  2: 'danger',
}

/** 活动视图 */
export interface PromotionVO {
  id: number
  title: string
  type: number
  typeDesc: string
  scope: number
  scopeDesc: string
  storeId: number | null
  storeName: string | null
  /** 规则 JSON 原文 */
  rules: string
  /** 规则中文摘要 */
  ruleDesc: string
  thresholdAmount: string
  discountAmount: string
  discountRate: string | null
  startTime: string
  endTime: string
  status: number
  statusDesc: string
  /** 当前是否处于可命中窗口 */
  active: boolean
  createdAt: string
  updatedAt: string
}

/** 活动分页查询 */
export type PromotionQuery = {
  keyword?: string
  type?: number
  scope?: number
  status?: number
  page?: number
  size?: number
}

/**
 * 活动新建 / 编辑请求
 *
 * rules 由页面按类型拼装：
 * - 满减/限时购：{ thresholdAmount, discountAmount }
 * - 折扣：{ thresholdAmount, discountRate, maxDiscount }
 */
export type PromotionSavePayload = {
  title: string
  type: number
  scope: number
  storeId?: number
  rules: string
  startTime: string
  endTime: string
}
