import { http } from '@/utils/request'
import type {
  PageResult,
  Product,
  ProductCategory,
  ProductDetail,
  ProductPageQuery,
  ProductSku,
  StoreProduct,
} from '@/types/product'

// ==================== 分类 ====================

/** 分类列表（登录即可） */
export const listCategoriesApi = () => http.get<ProductCategory[]>('/product/categories')

/** 创建分类（product:edit） */
export const createCategoryApi = (data: { name: string; sort: number }) =>
  http.post<number>('/product/categories', data)

/** 分类启停用（product:edit） */
export const updateCategoryStatusApi = (id: number, status: number) =>
  http.put<void>(`/product/categories/${id}/status`, undefined, { params: { status } })

// ==================== 平台商品目录（总部） ====================

/** 商品目录分页（menu:product） */
export const pageProductsApi = (query: ProductPageQuery) =>
  http.get<PageResult<Product>>('/product/admin/products', { params: query })

/** 商品详情（含 SKU，menu:product；costPrice 受 product:cost:view 保护） */
export const getProductDetailApi = (id: number) =>
  http.get<ProductDetail>(`/product/admin/products/${id}`)

/** 创建平台商品（product:edit） */
export const createProductApi = (data: Record<string, unknown>) =>
  http.post<number>('/product/admin/products', data)

/** 更新商品目录（product:edit） */
export const updateCatalogApi = (id: number, data: Record<string, unknown>) =>
  http.put<void>(`/product/admin/products/${id}`, data)

/** 商品上下架（目录层，product:edit） */
export const updateProductStatusApi = (id: number, status: number) =>
  http.put<void>(`/product/admin/products/${id}/status`, undefined, { params: { status } })

/** 追加 SKU（product:edit） */
export const addSkuApi = (productId: number, data: Record<string, unknown>) =>
  http.post<number>(`/product/admin/products/${productId}/skus`, data)

/** SKU 启停用（product:edit） */
export const updateSkuStatusApi = (id: number, status: number) =>
  http.put<void>(`/product/admin/skus/${id}/status`, undefined, { params: { status } })

// 注：库存相关接口（总览/流水/调整/预警阈值）已统一迁移至 @/api/inventory

// ==================== 本店上架（门店） ====================

/** 本店上架列表（menu:product） */
export const listStoreProductsApi = (status?: number) =>
  http.get<StoreProduct[]>('/product/store/listings', { params: { status } })

/** 本店上架（product:edit） */
export const createStoreProductApi = (data: {
  productId: number
  skuId: number
  price: number
  dailyQuota: number
}) => http.post<number>('/product/store/listings', data)

/** 本店改价（product:edit） */
export const updateStorePriceApi = (id: number, price: number) =>
  http.put<void>(`/product/store/listings/${id}/price`, { price })

/** 本店上下架开关（product:edit） */
export const updateStoreProductStatusApi = (id: number, status: number) =>
  http.put<void>(`/product/store/listings/${id}/status`, undefined, { params: { status } })

export type { Product, ProductCategory, ProductDetail, ProductSku, StoreProduct }
