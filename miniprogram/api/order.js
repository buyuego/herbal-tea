/** 订单接口 */
const request = require('../utils/request')

/**
 * 下单（幂等键 24h 窗口：同一 key 重复提交返回同一订单）
 * @param {object} payload {storeId, skuId, qty, addressId, remark, usePoints, userCouponId}
 * @param {string} idempotencyKey 客户端生成的随机键
 */
function create(payload, idempotencyKey) {
  return request.post('/api/order/create', payload, {
    header: { 'Idempotency-Key': idempotencyKey },
    // 下单失败自动释放幂等键，重试需换新 key，故此处不做自动重试
    noRetry: true,
  })
}

/** 我的订单（status 不传查全部） */
function mine(params) {
  return request.get('/api/order/mine', params)
}

/** 订单详情（归属校验） */
function detail(orderId) {
  return request.get('/api/order/' + orderId)
}

/** 发起支付（dev 直通模拟；生产替换为微信支付统一下单） */
function pay(orderId) {
  return request.post('/api/order/' + orderId + '/pay')
}

/** 取消订单（仅待支付） */
function cancel(orderId) {
  return request.post('/api/order/' + orderId + '/cancel')
}

/** 确认签收 */
function sign(orderId) {
  return request.post('/api/order/' + orderId + '/sign')
}

module.exports = { create, mine, detail, pay, cancel, sign }
