/** 优惠券（v28，B 端 105 menu:marketing / 211 marketing:coupon） */

/** 券类型 */
export const COUPON_TYPE: Record<number, string> = {
  1: '满减券',
  2: '折扣券',
}

/** 券归属（决定成本承担方） */
export const COUPON_SCOPE: Record<number, string> = {
  1: '平台券',
  2: '本店券',
}

export const COUPON_SCOPE_TAG: Record<number, 'warning' | 'success'> = {
  1: 'warning', // 平台券：平台承担
  2: 'success', // 本店券：店铺承担
}

/** 券模板状态 */
export const COUPON_STATUS: Record<number, string> = {
  0: '未发布',
  1: '发放中',
  2: '已停止',
}

export const COUPON_STATUS_TAG: Record<number, 'info' | 'success' | 'danger'> = {
  0: 'info',
  1: 'success',
  2: 'danger',
}

/** 持券状态 */
export const USER_COUPON_STATUS: Record<number, string> = {
  0: '未使用',
  1: '已使用',
  2: '已过期',
  3: '退款退回',
}

export const USER_COUPON_STATUS_TAG: Record<number, 'success' | 'info' | 'warning'> = {
  0: 'success',
  1: 'info',
  2: 'info',
  3: 'warning',
}

/** 券模板 */
export interface CouponVO {
  id: number
  name: string
  /** 1满减券 / 2折扣券 */
  type: number
  typeDesc: string
  /** 1平台券 / 2本店券 */
  scope: number
  scopeDesc: string
  storeId: number | null
  storeName: string | null
  /** 使用门槛 */
  thresholdAmount: number
  /** 优惠金额（满减券） */
  discountAmount: number
  /** 扩展规则 JSON（折扣券：{discountRate, maxDiscount}） */
  rules: string | null
  totalCount: number
  receivedCount: number
  /** 剩余可领 */
  remainCount: number
  perUserLimit: number
  startTime: string
  endTime: string
  /** 0未发布 / 1发放中 / 2已停止 */
  status: number
  statusDesc: string
  createdAt: string
  updatedAt: string
}

/** 用户持券 */
export interface UserCouponVO {
  id: number
  userId: number
  couponId: number
  couponName: string
  type: number
  typeDesc: string
  scope: number
  scopeDesc: string
  storeId: number | null
  storeName: string | null
  thresholdAmount: number
  discountAmount: number
  rules: string | null
  /** 0未使用 / 1已使用 / 2已过期 / 3退款退回 */
  status: number
  statusDesc: string
  orderId: number | null
  orderNo: string | null
  receivedAt: string
  usedAt: string | null
  expireAt: string
}

/** 券模板查询参数（用 type 而非 interface：需能赋值给 Record<string, unknown> 作为 query params） */
export type CouponQuery = {
  keyword?: string
  type?: number
  scope?: number
  storeId?: number
  status?: number
  page: number
  size: number
}

/** 券模板新建 / 编辑请求 */
export interface CouponSavePayload {
  name: string
  type: number
  scope: number
  storeId?: number | null
  thresholdAmount?: number
  discountAmount?: number
  rules?: string | null
  totalCount: number
  perUserLimit: number
  startTime: string
  endTime: string
}
