const orderApi = require('../../api/order')
const fmt = require('../../utils/format')

Page({
  data: {
    loading: true,
    order: null,
    items: [],
    canPay: false,
    canCancel: false,
    canSign: false,
  },

  onLoad(query) {
    this.orderId = Number(query.id)
  },

  onShow() {
    this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      const o = await orderApi.detail(this.orderId)
      const items = (o.items || []).map((it) => ({
        name: it.name,
        image: it.image,
        specsText: fmt.specs(it.specs),
        priceText: fmt.price(it.price),
        qty: it.qty,
        subtotalText: fmt.price(it.subtotal),
      }))
      this.setData({
        loading: false,
        order: Object.assign({}, o, {
          totalAmountText: fmt.price(o.totalAmount),
          couponAmountText: fmt.price(o.couponAmount),
          promotionDiscountText: fmt.price(o.promotionDiscount),
          hasPromotion: Number(o.promotionDiscount || 0) > 0,
          pointsDeductAmountText: fmt.price(o.pointsDeductAmount),
          payAmountText: fmt.price(o.payAmount),
          createdText: fmt.time(o.createdAt),
          paidText: fmt.time(o.paidAt),
          shippedText: fmt.time(o.shippedAt),
          finishedText: fmt.time(o.finishedAt),
          expireText: fmt.time(o.expireAt),
        }),
        items,
        canPay: o.status === 10,
        canCancel: o.status === 10,
        canSign: o.status === 40,
      })
    } catch (e) {
      this.setData({ loading: false })
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  pay() {
    wx.showLoading({ title: '支付中' })
    orderApi
      .pay(this.orderId)
      .then(() => {
        wx.hideLoading()
        wx.showToast({ title: '支付成功', icon: 'success' })
        this.load()
      })
      .catch((err) => {
        wx.hideLoading()
        wx.showToast({ title: err.message || '支付失败', icon: 'none' })
      })
  },

  cancel() {
    wx.showModal({
      title: '取消订单',
      content: '确定取消该订单？库存将被释放。',
      success: (res) => {
        if (!res.confirm) return
        orderApi
          .cancel(this.orderId)
          .then(() => {
            wx.showToast({ title: '已取消', icon: 'success' })
            this.load()
          })
          .catch((err) => wx.showToast({ title: err.message || '取消失败', icon: 'none' }))
      },
    })
  },

  sign() {
    wx.showModal({
      title: '确认收货',
      content: '确认已收到商品？',
      success: (res) => {
        if (!res.confirm) return
        orderApi
          .sign(this.orderId)
          .then(() => {
            wx.showToast({ title: '已确认收货', icon: 'success' })
            this.load()
          })
          .catch((err) => wx.showToast({ title: err.message || '操作失败', icon: 'none' }))
      },
    })
  },
})
