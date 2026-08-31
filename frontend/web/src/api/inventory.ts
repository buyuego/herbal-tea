import { http } from '@/utils/request'
import type { PageResult } from '@/types/product'
import type {
  InventoryQuery,
  InventoryRecordQuery,
  InventoryRecordVO,
  InventoryVO,
  StockAdjustPayload,
} from '@/types/inventory'

/** 库存总览分页（menu:inventory，预警行优先） */
export const pageInventoryApi = (query: InventoryQuery) =>
  http.get<PageResult<InventoryVO>>('/product/inventory/skus', { params: query })

/** 库存流水分页（inventory:manage） */
export const pageInventoryRecordsApi = (params: InventoryRecordQuery) =>
  http.get<PageResult<InventoryRecordVO>>('/product/inventory/records', { params })

/** 库存调整：1入库 / 3盘点（inventory:manage） */
export const adjustStockApi = (data: StockAdjustPayload) => http.post<void>('/product/inventory/adjust', data)

/** 设置低库存预警阈值（inventory:manage） */
export const setAlertStockApi = (skuId: number, alertStock: number) =>
  http.put<void>(`/product/inventory/skus/${skuId}/alert`, undefined, { params: { alertStock } })

export type { InventoryQuery, InventoryRecordQuery, InventoryRecordVO, InventoryVO, StockAdjustPayload }
