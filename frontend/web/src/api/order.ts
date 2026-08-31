import { http } from '@/utils/request'
import type { Order, OrderDetail, OrderPageQuery, ShippingLog } from '@/types/order'
import type { PageResult } from '@/types/product'

/** 订单分页查询（menu:order） */
export const pageOrdersApi = (query: OrderPageQuery) =>
  http.get<PageResult<Order>>('/order/admin/page', { params: query })

/** 订单详情（menu:order） */
export const getOrderDetailApi = (orderId: number) =>
  http.get<OrderDetail>(`/order/admin/${orderId}`)

/** 物流轨迹（menu:order） */
export const shippingLogsApi = (orderId: number) =>
  http.get<ShippingLog[]>(`/order/admin/${orderId}/shipping-logs`)

/** 订单发货（order:ship，30待发货→40已发货） */
export const shipOrderApi = (
  orderId: number,
  data: { logisticsNo: string; carrier: string; note?: string },
) => http.post<void>(`/order/admin/${orderId}/ship`, data)

export type { Order, OrderDetail, OrderPageQuery, ShippingLog }
