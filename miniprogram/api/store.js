/** 门店接口 */
const request = require('../utils/request')

/** C 端门店列表（正常营业） */
function listStores() {
  return request.get('/api/store/list')
}

/** 分类列表（公开） */
function listCategories() {
  return request.get('/api/product/categories')
}

module.exports = { listStores, listCategories }
