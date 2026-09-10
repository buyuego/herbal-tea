// 数据看板类型（v31）
export interface OverviewVO {
  salesAmount: string
  orderCount: number
  avgOrderAmount: string
  memberCount: number
  periodLabel: string
  salesAmountChange: string | null
}

export interface TrendPoint {
  date: string
  value: string
}

export interface PointsTrendPoint {
  date: string
  granted: number
  used: number
}

export interface FunnelItem {
  status: number
  label: string
  count: number
}

export interface CategoryShareItem {
  name: string
  value: string
  percent: string
}

export interface TopSkuItem {
  skuId: number
  skuName: string
  productId: number
  productName: string
  qty: number
  amount: string
}

export interface PromoStatItem {
  promotionId: number
  name: string
  scope: number
  orderCount: number
  discountTotal: string
}

export interface RefundRateVO {
  rate: string
  refundCount: number
  paidCount: number
}