/** 会员管理（v26，B 端 109 menu:member） */

/** 会员状态 */
export const MEMBER_STATUS: Record<number, string> = {
  0: '禁用',
  1: '正常',
}

export const MEMBER_STATUS_TAG: Record<number, 'success' | 'info' | 'danger'> = {
  0: 'danger',
  1: 'success',
}

/** 会员列表行（users × 积分账户 × 订单聚合） */
export interface MemberVO {
  id: number
  openid: string
  nickname: string | null
  avatarUrl: string | null
  /** 脱敏手机号（138****1234） */
  phone: string | null
  /** 0禁用 / 1正常 */
  status: number
  pointsBalance: number
  totalEarned: number
  totalUsed: number
  /** 有效订单数（已支付及之后） */
  orderCount: number
  /** 累计消费金额 */
  payTotalAmount: number
  lastOrderAt: string | null
  createdAt: string
}

/** 收货地址 */
export interface MemberAddress {
  id: number
  userId: number
  receiverName: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault: number
  createdAt: string
}

/** 积分流水行（changeTypeDesc / sourceTypeDesc 由服务端填充） */
export interface PointRecordItem {
  id: number
  userId: number
  storeId: number | null
  storeName: string | null
  orderId: number | null
  orderNo: string | null
  /** 1发放 / 2抵扣 / 3退款回收 / 4过期清零 / 5签到 */
  changeType: number
  changeTypeDesc: string
  /** 1门店营销 / 2平台活动 */
  sourceType: number
  sourceTypeDesc: string
  points: number
  bizKey: string
  createdAt: string
}

/** 会员详情 */
export interface MemberDetail {
  member: MemberVO
  addresses: MemberAddress[]
  /** 最近 20 条积分流水 */
  pointRecords: PointRecordItem[]
}

/** 会员分页查询参数（用 type 而非 interface：需能赋值给 Record<string, unknown> 作为 query params） */
export type MemberQuery = {
  keyword?: string
  status?: number
  page: number
  size: number
}
