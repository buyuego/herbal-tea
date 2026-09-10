const app = getApp()
const productApi = require('../../api/product')
const addressApi = require('../../api/address')
const marketingApi = require('../../api/marketing')
const orderApi = require('../../api/order')
const fmt = require('../../utils/format')

/** 1 积分 = 0.01 元（与后端 POINTS_DEDUCT_UNIT 同口径） */
const POINT_UNIT = 0.01

Page({
  data: {
    from: 'buy',
    items: [],
    subtotal: 0,
    subtotalText: '0.00',
    multi: false,

    address: null,
    addresses: [],

    pointsBalance: 0,
    usePoints: false,
    pointsToUse: 0,
    pointsDeductText: '0.00',
    maxPoints: 0,

    coupons: [],
    userCouponId: null,
    couponText: '0.00',

    payAmountText: '0.00',
    remark: '',
    submitting: false,
  },

  onLoad(query) {
    this.from = query.from === 'cart' ? 'cart' : 'buy'
    this.skuId = query.skuId ? Number(query.skuId) : 0
    this.qty = query.qty ? Number(query.qty) : 1
    this.load()
  },

  onShow() {
    // 从地址页返回时同步选择结果
    if (this._pickedAddress) {
      this.setData({ address: this._pickedAddress })
      this._pickedAddress = null
      this.recalc()
    }
  },

  async load() {
    const storeId = app.getStoreId()
    if (!storeId) {
      wx.showToast({ title: '请先在首页选择门店', icon: 'none' })
      setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 800)
      return
    }
    wx.showLoading({ title: '加载中' })
    try {
      let items = []
      if (this.from === 'cart') {
        const cart = app.globalData.cart || {}
        items = Object.keys(cart).map((k) => {
          const it = cart[k]
          return {
            skuId: it.skuId,
            productId: it.productId,
            name: it.name,
            image: it.image,
            specsText: it.specsText || fmt.specs(it.specs),
            price: Number(it.price),
            qty: Number(it.qty || 1),
          }
        })
      } else {
        const sku = await productApi.getSku(this.skuId, storeId)
        items = [
          {
            skuId: sku.skuId,
            productId: sku.productId,
            name: sku.productName,
            image: sku.mainImage,
            specsText: fmt.specs(sku.specs),
            price: Number(sku.price),
            qty: this.qty,
          },
        ]
      }
      if (!items.length) {
        wx.hideLoading()
        wx.showToast({ title: '没有可结算的商品', icon: 'none' })
        return
      }
      items.forEach((it) => {
        it.priceText = fmt.price(it.price)
        it.subtotalText = fmt.price(it.price * it.qty)
      })
      const subtotal = items.reduce((s, i) => s + i.price * i.qty, 0)
      this.setData({ items, subtotal, subtotalText: fmt.price(subtotal), multi: items.length > 1 })

      await Promise.all([this.loadAddresses(), this.loadPoints(), this.loadCoupons()])
      this.recalc()
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      wx.hideLoading()
    }
  },

  async loadAddresses() {
    try {
      const list = (await addressApi.list()) || []
      const def = list.find((a) => a.isDefault === 1) || list[0] || null
      this.setData({ addresses: list, address: def })
    } catch (e) {
      // 地址加载失败不阻塞，提交时会再校验
    }
  },

  async loadPoints() {
    try {
      const p = await marketingApi.myPoints()
      this.setData({ pointsBalance: p.balance || 0 })
    } catch (e) {
      this.setData({ pointsBalance: 0 })
    }
  },

  /** 本单可用券（门槛按商品小计过滤；多件下单不支持券） */
  async loadCoupons() {
    if (this.data.multi) {
      this.setData({ coupons: [], userCouponId: null })
      return
    }
    try {
      const res = await marketingApi.myCoupons({
        status: 0,
        storeId: app.getStoreId(),
        usableAmount: this.data.subtotal,
        page: 1,
        size: 50,
      })
      const coupons = (res.records || []).map((c) => ({
        id: c.id,
        couponName: c.couponName,
        type: c.type,
        discountAmount: Number(c.discountAmount || 0),
        thresholdAmount: Number(c.thresholdAmount || 0),
        rules: c.rules,
        scopeDesc: c.scopeDesc,
        expireText: fmt.time(c.expireAt).slice(0, 10),
      }))
      this.setData({ coupons })
    } catch (e) {
      this.setData({ coupons: [] })
    }
  },

  /** 计算券优惠金额（与后端 calcByType 同规则） */
  couponDiscount(c) {
    const base = this.data.subtotal
    let d
    if (c.type === 1) {
      d = c.discountAmount
    } else {
      let rate = 1
      let maxDiscount = 0
      try {
        const rules = c.rules ? (typeof c.rules === 'string' ? JSON.parse(c.rules) : c.rules) : {}
        rate = Number(rules.discountRate || 1)
        maxDiscount = Number(rules.maxDiscount || 0)
      } catch (e) {
        rate = 1
      }
      d = base * (1 - rate)
      if (maxDiscount > 0) d = Math.min(d, maxDiscount)
    }
    return Math.min(d, base)
  },

  onPickCoupon(e) {
    const id = Number(e.currentTarget.dataset.id)
    this.couponDiscountCache = this.couponDiscountCache || {}
    this.setData({ userCouponId: this.data.userCouponId === id ? null : id })
    this.recalc()
  },

  onTogglePoints() {
    this.setData({ usePoints: !this.data.usePoints })
    this.recalc()
  },

  onPointsInput(e) {
    const v = Number(e.detail.value || 0)
    this.setData({ pointsToUse: v })
    this.recalc()
  },

  onRemark(e) {
    this.setData({ remark: e.detail.value })
  },

  pickAddress() {
    const list = this.data.addresses
    if (!list.length) {
      wx.navigateTo({ url: '/pages/address/edit' })
      return
    }
    wx.navigateTo({ url: '/pages/address/list?pick=1' })
  },

  /** 重新计算应付：券 → 积分 → 实付（与后端下单口径一致） */
  recalc() {
    const { subtotal, coupons, userCouponId, usePoints, pointsBalance, pointsToUse } = this.data
    let couponAmount = 0
    if (userCouponId) {
      const c = coupons.find((x) => x.id === userCouponId)
      if (c) couponAmount = this.couponDiscount(c)
    }
    // 积分最多抵到「券后金额」，避免抵扣超出应付
    const afterCoupon = Math.max(subtotal - couponAmount, 0)
    const maxPoints = Math.min(pointsBalance, Math.floor(afterCoupon / POINT_UNIT))
    let use = 0
    if (usePoints && maxPoints > 0) {
      const want = Number(pointsToUse) > 0 ? Number(pointsToUse) : maxPoints
      use = Math.min(want, maxPoints)
    }
    const pointsDeductAmount = use * POINT_UNIT
    const payAmount = Math.max(subtotal - couponAmount - pointsDeductAmount, 0)
    this.setData({
      couponText: fmt.price(couponAmount),
      maxPoints,
      pointsToUse: use,
      pointsDeductText: fmt.price(pointsDeductAmount),
      payAmountText: fmt.price(payAmount),
    })
  },

  /** 幂等键：时间戳 + 随机串（24h 窗口内同一 key 返回同一订单） */
  newKey() {
    return 'mp' + Date.now() + Math.random().toString(36).slice(2, 10)
  },

  async submit() {
    if (this.data.submitting) return
    if (!this.data.address) {
      wx.showToast({ title: '请先添加收货地址', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    wx.showLoading({ title: '提交中' })
    const storeId = app.getStoreId()
    try {
      // 券与积分仅作用于首件（后端下单粒度为单一 SKU，多件=多笔订单）
      for (let i = 0; i < this.data.items.length; i++) {
        const it = this.data.items[i]
        const first = i === 0
        await orderApi.create(
          {
            storeId,
            skuId: it.skuId,
            qty: it.qty,
            addressId: this.data.address.id,
            remark: this.data.remark || null,
            usePoints: first && this.data.usePoints ? this.data.pointsToUse : 0,
            userCouponId: first ? this.data.userCouponId : null,
          },
          this.newKey()
        )
      }
      if (this.from === 'cart') app.clearCart()
      wx.hideLoading()
      wx.showToast({ title: '下单成功', icon: 'success' })
      setTimeout(() => {
        wx.redirectTo({ url: '/pages/order/list?status=10' })
      }, 600)
    } catch (e) {
      wx.hideLoading()
      this.setData({ submitting: false })
      wx.showModal({ title: '下单失败', content: e.message || '请稍后重试', showCancel: false })
    }
  },

  toAddressAdd() {
    wx.navigateTo({ url: '/pages/address/edit' })
  },
})
