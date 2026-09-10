/** 营销接口（我的积分 / 我的券包） */
const request = require('../utils/request')

/** 我的积分概览 {balance, totalEarned, totalUsed, totalExpired} */
function myPoints() {
  return request.get('/api/marketing/points/my')
}

/** 我的积分明细 */
function myPointRecords(params) {
  return request.get('/api/marketing/points/my/records', params)
}

/**
 * 我的券包
 * @param {object} params {status, storeId, usableAmount, page, size}
 *   storeId + usableAmount 用于下单页筛「本单可用券」
 */
function myCoupons(params) {
  return request.get('/api/marketing/coupons/my', params)
}

/**
 * 门店生效活动（平台活动 + 该门店本店活动，进行中且时间窗口命中）
 * @param {number} storeId 不传仅返回平台活动
 */
function activePromotions(storeId) {
  return request.get('/api/marketing/promotions/active', { storeId })
}

module.exports = { myPoints, myPointRecords, myCoupons, activePromotions }
