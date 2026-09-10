const app = getApp()
const fmt = require('../../utils/format')

Page({
  data: {
    items: [],
    total: '0.00',
    count: 0,
  },

  onShow() {
    this.sync()
  },

  sync() {
    const cart = app.globalData.cart || {}
    const items = Object.keys(cart).map((k) => {
      const it = cart[k]
      return Object.assign({}, it, {
        specsText: it.specsText || fmt.specs(it.specs),
        priceText: fmt.price(it.price),
        subtotalText: fmt.price(Number(it.price) * Number(it.qty || 0)),
      })
    })
    const total = items.reduce((s, i) => s + Number(i.price) * Number(i.qty || 0), 0)
    this.setData({ items, total: fmt.price(total), count: app.cartCount() })
  },

  onQty(e) {
    const { sku, delta } = e.currentTarget.dataset
    const cur = (app.globalData.cart[sku] || {}).qty || 0
    app.updateQty(sku, cur + Number(delta))
    this.sync()
  },

  onRemove(e) {
    const sku = e.currentTarget.dataset.sku
    wx.showModal({
      title: '移除商品',
      content: '确定从购物车移除该商品？',
      success: (res) => {
        if (res.confirm) {
          app.updateQty(sku, 0)
          this.sync()
        }
      },
    })
  },

  onClear() {
    wx.showModal({
      title: '清空购物车',
      content: '确定清空购物车？',
      success: (res) => {
        if (res.confirm) {
          app.clearCart()
          this.sync()
        }
      },
    })
  },

  toProduct(e) {
    wx.navigateTo({ url: '/pages/product/product?productId=' + e.currentTarget.dataset.pid })
  },

  toIndex() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  checkout() {
    if (!this.data.items.length) return
    wx.navigateTo({ url: '/pages/order/confirm?from=cart' })
  },
})
