/**
 * 登录态管理：token 持久化（storage）+ 静默登录
 *
 * 说明：accessToken 2h、refreshToken 30d 轮换，与 B 端同一套 JwtUtil；
 * 后端 users.token_version 可实现服务端即时吊销（R9），本地不缓存用户信息过久。
 */
const ACCESS_KEY = 'ht_access_token'
const REFRESH_KEY = 'ht_refresh_token'

function getAccessToken() {
  return wx.getStorageSync(ACCESS_KEY) || ''
}

function getRefreshToken() {
  return wx.getStorageSync(REFRESH_KEY) || ''
}

function saveTokens(accessToken, refreshToken) {
  if (accessToken) wx.setStorageSync(ACCESS_KEY, accessToken)
  if (refreshToken) wx.setStorageSync(REFRESH_KEY, refreshToken)
}

function clearTokens() {
  wx.removeStorageSync(ACCESS_KEY)
  wx.removeStorageSync(REFRESH_KEY)
}

function isLoggedIn() {
  return !!getAccessToken()
}

/**
 * 静默登录：wx.login 取 code → 后端换 openid 并签发双令牌
 * 已登录则直接返回，避免每次启动都调用 wx.login（有频率限制）
 */
function login(force) {
  if (!force && isLoggedIn()) {
    return Promise.resolve(getAccessToken())
  }
  return new Promise((resolve, reject) => {
    wx.login({
      success: (res) => {
        if (!res.code) {
          reject(new Error('wx.login 失败：' + res.errMsg))
          return
        }
        // 设备指纹：小程序端用系统信息做近似（仅登记，不做强校验）
        let fingerprint = 'wx'
        try {
          const info = wx.getDeviceInfo ? wx.getDeviceInfo() : wx.getSystemInfoSync()
          fingerprint = [info.brand, info.model, info.system, info.platform]
            .filter(Boolean)
            .join('|')
        } catch (e) {
          fingerprint = 'wx-unknown'
        }
        require('./request')
          .post('/api/user/wx-login', { code: res.code, deviceFingerprint: fingerprint }, { auth: false })
          .then((data) => {
            saveTokens(data.accessToken, data.refreshToken)
            resolve(data.accessToken)
          })
          .catch(reject)
      },
      fail: (err) => reject(new Error('wx.login 调用失败：' + err.errMsg)),
    })
  })
}

module.exports = {
  getAccessToken,
  getRefreshToken,
  saveTokens,
  clearTokens,
  isLoggedIn,
  login,
}
