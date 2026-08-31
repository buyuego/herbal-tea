/** 商品分类（product_categories） */
export interface ProductCategory {
  id: number
  name: string
  sort: number
  status: number
  createdAt: string
}

/** 平台商品（products） */
export interface Product {
  id: number
  categoryId: number
  name: string
  subtitle: string | null
  formula: string | null
  mainImage: string
  images: string | null
  detail: string | null
  suggestedPrice: number
  costPrice: number
  status: number
  version: number
  createdAt: string
  updatedAt: string
}

/** 商品 SKU（product_skus） */
export interface ProductSku {
  id: number
  productId: number
  skuCode: string
  /** 规格矩阵 JSON 字符串 */
  specs: string
  price: number
  costPrice: number
  stock: number
  status: number
  version: number
  createdAt: string
  updatedAt: string
}

/** 商品详情 VO（含 SKU 列表） */
export interface ProductDetail {
  id: number
  categoryId: number
  categoryName: string
  name: string
  subtitle: string | null
  formula: string | null
  mainImage: string
  images: string[] | null
  detail: string | null
  suggestedPrice: number
  costPrice: number
  status: number
  skus: ProductSku[]
}

/** 分页查询参数 */
export type ProductPageQuery = {
  keyword?: string
  categoryId?: number
  status?: number
  page: number
  size: number
}

/** 分页结果（MyBatis-Plus IPage） */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 本店上架（store_products 联查） */
export interface StoreProduct {
  id: number
  storeId: number
  productId: number
  skuId: number
  price: number
  status: number
  catalogDirty: number
  dailyQuota: number
  version: number
  createdAt: string
  updatedAt: string
  productName: string
  mainImage: string
  suggestedPrice: number
  skuCode: string
  specs: string
  stock: number
}

/** SKU 创建草稿（商品创建对话框内） */
export interface SkuDraft {
  skuCode: string
  specs: Record<string, string>
  price: number
  costPrice: number
  stock: number
}

/** 库存调整流水 */
export interface InventoryRecord {
  id: number
  skuId: number
  changeType: number
  changeQty: number
  stockBefore: number
  stockAfter: number
  bizNo: string | null
  note: string | null
  createdBy: number
  createdAt: string
}
