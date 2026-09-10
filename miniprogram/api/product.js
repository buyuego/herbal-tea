/** 商品（C 端货架）接口 */
const request = require('../utils/request')

/**
 * 门店在售商品分页
 * @param {object} params {storeId, categoryId, keyword, page, size}
 */
function pageShelf(params) {
  return request.get('/api/product/shelf/products', params)
}

/** 商品详情（含配方/图集/富文本与本店在售 SKU） */
function getProduct(productId, storeId) {
  return request.get('/api/product/shelf/products/' + productId, { storeId })
}

/**
 * SKU 详情（本店价 + 库存）
 * @param {number} skuId
 * @param {number} storeId
 */
function getSku(skuId, storeId) {
  return request.get('/api/product/shelf/skus/' + skuId, { storeId })
}

module.exports = { pageShelf, getProduct, getSku }
