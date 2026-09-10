// 数据看板 API（v31）
import { http } from '@/utils/request'
import type {
  OverviewVO,
  TrendPoint,
  FunnelItem,
  CategoryShareItem,
  TopSkuItem,
  PointsTrendPoint,
  PromoStatItem,
  RefundRateVO,
} from '@/types/report'

export const overviewApi = (period = 'today') =>
  http.get<OverviewVO>('/report/overview', { params: { period } })

export const salesTrendApi = (days = 14) =>
  http.get<TrendPoint[]>('/report/sales-trend', { params: { days } })

export const orderFunnelApi = (days = 30) =>
  http.get<FunnelItem[]>('/report/order-funnel', { params: { days } })

export const categoryShareApi = (days = 30) =>
  http.get<CategoryShareItem[]>('/report/category-share', { params: { days } })

export const topSkusApi = (days = 30, limit = 10) =>
  http.get<TopSkuItem[]>('/report/top-skus', { params: { days, limit } })

export const pointsTrendApi = (days = 14) =>
  http.get<PointsTrendPoint[]>('/report/points-trend', { params: { days } })

export const promoStatsApi = (days = 30) =>
  http.get<PromoStatItem[]>('/report/promo-stats', { params: { days } })

export const refundRateApi = (days = 30) =>
  http.get<RefundRateVO>('/report/refund-rate', { params: { days } })