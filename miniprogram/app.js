/**
 * 养生茶小程序（C 端）入口
 *
 * 全局职责：
 * - 启动时静默登录（wx.login → 后端换 openid/令牌）
 * - 维护全局数据：当前门店、购物车（本地存储）
 */
const auth = require('./utils/auth')
const STORE_KEY = 'ht_current_store'
const CART_KEY = 'ht_cart'

App({
  globalData: {
    /** 当前选中门店 {id, storeName}；C 端始终从具体门店购买 */
    store: null,
    /** 购物车：{ [skuId]: { skuId, productId, name, image, specs, price, qty } } */
    cart: {},
    userInfo: null,
  },

  onLaunch() {
    const store = wx.getStorageSync(STORE_KEY)
    if (store && store.id) {
      this.globalData.store = store
    }
    this.globalData.cart = wx.getStorageSync(CART_KEY) || {}

    // 静默登录：失败不阻塞（页面请求时会自动重试）
    auth.login().catch((err) => {
      console.warn('[app] 静默登录失败：', err.message)
    })
  },

  // ==================== 门店 ====================

  setStore(store) {
    this.globalData.store = store
    wx.setStorageSync(STORE_KEY, store)
    // 换店清空购物车：本店价与可售性随门店变化
    this.clearCart()
  },

  getStoreId() {
    const s = this.globalData.store
    return s && s.id ? s.id : 0
  },

  // ==================== 购物车 ====================

  addToCart(item, qty) {
    const n = qty || 1
    const cart = this.globalData.cart || {}
    const exist = cart[item.skuId]
    if (exist) {
      exist.qty = Math.min((exist.qty || 0) + n, 99)
    } else {
      cart[item.skuId] = Object.assign({}, item, { qty: n })
    }
    this.globalData.cart = cart
    wx.setStorageSync(CART_KEY, cart)
    return cart
  },

  updateQty(skuId, qty) {
    const cart = this.globalData.cart || {}
    if (!cart[skuId]) return cart
    if (qty <= 0) {
      delete cart[skuId]
    } else {
      cart[skuId].qty = Math.min(qty, 99)
    }
    this.globalData.cart = cart
    wx.setStorageSync(CART_KEY, cart)
    return cart
  },

  clearCart() {
    this.globalData.cart = {}
    wx.setStorageSync(CART_KEY, {})
  },

  cartCount() {
    const cart = this.globalData.cart || {}
    return Object.keys(cart).reduce((sum, k) => sum + (cart[k].qty || 0), 0)
  },
})
