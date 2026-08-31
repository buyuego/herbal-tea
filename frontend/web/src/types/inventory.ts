/** 库存管理（v25，总部总仓维度） */

/** 变动类型：1入库 / 2出库 / 3盘点调整 / 4退款回库 */
export const CHANGE_TYPE: Record<number, string> = {
  1: '入库',
  2: '出库',
  3: '盘点调整',
  4: '退款回库',
}

/** 变动类型标签色（入库/回库=绿，出库=灰，盘点=橙） */
export const CHANGE_TYPE_TAG: Record<number, 'success' | 'info' | 'warning' | 'danger'> = {
  1: 'success',
  2: 'info',
  3: 'warning',
  4: 'success',
}

/** 库存总览行（SKU × 商品 × 分类） */
export interface InventoryVO {
  skuId: number
  productId: number
  productName: string
  categoryId: number
  categoryName: string | null
  skuCode: string
  specs: string
  stock: number
  alertStock: number
  /** stock <= alertStock */
  lowStock: boolean
  costPrice: number
  price: number
  /** 0停用 / 1启用 */
  status: number
  updatedAt: string
}

/** 库存流水行 */
export interface InventoryRecordVO {
  id: number
  skuId: number
  skuCode: string | null
  productName: string | null
  specs: string | null
  changeType: number
  changeQty: number
  beforeStock: number
  afterStock: number
  bizNo: string | null
  operatorId: number | null
  operatorName: string | null
  note: string | null
  createdAt: string
}

/** 库存总览查询参数（用 type 而非 interface：需能赋值给 Record<string, unknown> 作为 query params） */
export type InventoryQuery = {
  keyword?: string
  categoryId?: number
  status?: number
  /** 1 = 仅看低库存预警 */
  lowStockOnly?: number
  page: number
  size: number
}

/** 库存流水查询参数 */
export type InventoryRecordQuery = {
  skuId?: number
  bizNo?: string
  changeType?: number
  page: number
  size: number
}

/** 库存调整请求 */
export interface StockAdjustPayload {
  skuId: number
  /** 1入库（qty 为正） / 3盘点（qty 为实际差值，可正可负） */
  changeType: number
  changeQty: number
  bizNo?: string
  note?: string
}
