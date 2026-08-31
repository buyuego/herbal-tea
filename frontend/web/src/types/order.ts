import type { PageResult } from './product'

/** 订单状态映射（与后端 OrderStatus 对齐） */
export const ORDER_STATUS: Record<number, string> = {
  10: '待支付',
  20: '已支付',
  30: '待发货',
  40: '已发货',
  50: '已签收',
  60: '退款中',
  70: '已关闭',
  80: '已退款',
  90: '已完结',
  95: '回退失败-待人工',
}

/** 订单状态 tag 类型 */
export const ORDER_STATUS_TAG: Record<number, 'warning' | 'info' | 'primary' | 'success' | 'danger'> = {
  10: 'warning',
  20: 'info',
  30: 'primary',
  40: 'info',
  50: 'success',
  60: 'danger',
  70: 'info',
  80: 'danger',
  90: 'success',
  95: 'danger',
}

/** 订单（orders 表，分页记录） */
export interface Order {
  id: number
  orderNo: string
  userId: number
  storeId: number
  status: number
  warehouseStatus: number | null
  totalAmount: number
  couponAmount: number
  pointsDeduct: number | null
  pointsDeductAmount: number
  pointsEarned: number | null
  pointsSource: number | null
  payAmount: number
  commissionRate: number | null
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  remark: string | null
  trackingNo: string | null
  carrier: string | null
  shippedBy: number | null
  shippedAt: string | null
  paidAt: string | null
  finishedAt: string | null
  refundApprovedBy: number | null
  refundApprovedAt: string | null
  expireAt: string | null
  autoCloseStatus: number
  urgeCount: number
  urgedAt: string | null
  shipTimeoutWarned: number | null
  autoSignedAt: string | null
  version: number
  createdAt: string
  updatedAt: string
}

/** 订单明细（order_items 快照 + 规格 JSON 已解析） */
export interface OrderItem {
  skuId: number
  name: string
  specs: Record<string, string> | null
  image: string | null
  price: number
  qty: number
  subtotal: number
}

/** 订单详情 VO（订单头 + 明细 + 支付单） */
export interface OrderDetail {
  id: number
  orderNo: string
  userId: number
  storeId: number
  status: number
  statusDesc: string
  warehouseStatus: number | null
  totalAmount: number
  couponAmount: number
  pointsDeductAmount: number
  pointsEarned: number | null
  payAmount: number
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  remark: string | null
  trackingNo: string | null
  carrier: string | null
  paidAt: string | null
  shippedAt: string | null
  finishedAt: string | null
  expireAt: string | null
  createdAt: string
  payNo: string | null
  payStatus: number | null
  items: OrderItem[]
}

/** 物流轨迹（order_shipping_logs） */
export interface ShippingLog {
  id: number
  orderId: number
  status: string
  trackingNo: string | null
  carrier: string | null
  operatorId: number | null
  note: string | null
  createdAt: string
}

/** 订单分页查询参数 */
export type OrderPageQuery = {
  orderNo?: string
  userId?: number
  storeId?: number
  status?: number
  page: number
  size: number
}

export type { PageResult }
