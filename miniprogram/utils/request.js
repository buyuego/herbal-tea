/**
 * 网络请求封装（对齐后端统一响应 {code, message, data}）
 *
 * - 自动注入 Bearer token
 * - 401 时先用 refreshToken 轮换一次再重试原请求（D12 轮换语义）
 * - 业务码非 0 一律 reject（页面用 catch 提示）
 */
const config = require('../config')
const auth = require('./auth')

const BASE = config.baseUrl

/** 同时只允许一次刷新，避免并发 401 打爆刷新接口 */
let refreshing = null

function raw(url, method, data, opts) {
  const header = Object.assign({ 'Content-Type': 'application/json' }, opts.header)
  if (opts.auth !== false) {
    const token = auth.getAccessToken()
    if (token) header['Authorization'] = 'Bearer ' + token
  }
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE + url,
      method,
      data: data || {},
      header,
      timeout: 20000,
      success: (res) => {
        const body = res.data || {}
        if (res.statusCode === 401 || body.code === 40100 || body.code === 40101) {
          reject(Object.assign(new Error(body.message || '登录已过期'), { statusCode: 401 }))
          return
        }
        if (res.statusCode >= 500) {
          reject(new Error('服务异常，请稍后重试'))
          return
        }
        if (body.code === 0) {
          resolve(body.data)
        } else {
          reject(Object.assign(new Error(body.message || '请求失败'), { code: body.code }))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || '网络异常')),
    })
  })
}

/** 刷新令牌（旧 refreshToken 作废并换新，D12） */
function doRefresh() {
  if (refreshing) return refreshing
  refreshing = new Promise((resolve, reject) => {
    wx.request({
      url: BASE + '/api/user/refresh',
      method: 'POST',
      data: { refreshToken: auth.getRefreshToken() },
      header: { 'Content-Type': 'application/json' },
      success: (res) => {
        const body = res.data || {}
        if (body.code === 0 && body.data) {
          auth.saveTokens(body.data.accessToken, body.data.refreshToken)
          resolve(body.data.accessToken)
        } else {
          auth.clearTokens()
          reject(Object.assign(new Error('登录已过期，请重新登录'), { code: body.code }))
        }
      },
      fail: () => {
        auth.clearTokens()
        reject(new Error('网络异常，刷新登录失败'))
      },
    })
  }).finally(() => {
    refreshing = null
  })
  return refreshing
}

function request(url, method, data, opts) {
  const options = opts || {}
  return raw(url, method, data, options).catch((err) => {
    // 仅对「需要鉴权」且「401」的请求做一次刷新重试；refresh 自身不重试
    if (err.statusCode === 401 && options.auth !== false && options.noRetry !== true) {
      return doRefresh()
        .then(() => raw(url, method, data, options))
        .catch(() => {
          // 刷新也失败 → 重新走 wx.login 静默登录再试一次
          return auth.login(true).then(() => raw(url, method, data, options))
        })
    }
    throw err
  })
}

module.exports = {
  get: (url, data, opts) => request(url, 'GET', data, opts),
  post: (url, data, opts) => request(url, 'POST', data, opts),
  put: (url, data, opts) => request(url, 'PUT', data, opts),
  del: (url, data, opts) => request(url, 'DELETE', data, opts),
  request,
}
