const app = getApp()
const productApi = require('../../api/product')
const fmt = require('../../utils/format')

Page({
  data: {
    loading: true,
    product: null,
    images: [],
    skuList: [],
    skuIndex: 0,
    sku: null,
    qty: 1,
    detailNodes: '',
  },

  onLoad(query) {
    this.productId = Number(query.productId)
  },

  onShow() {
    if (!this.loaded) this.load()
  },

  async load() {
    const storeId = app.getStoreId()
    if (!storeId) {
      wx.showToast({ title: '请先在首页选择门店', icon: 'none' })
      setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 800)
      return
    }
    this.loaded = true
    this.setData({ loading: true })
    try {
      const p = await productApi.getProduct(this.productId, storeId)
      const skuList = (p.skus || []).map((s) => ({
        skuId: s.skuId,
        skuCode: s.skuCode,
        specs: s.specs,
        specsText: fmt.specs(s.specs) || s.skuCode,
        price: Number(s.price),
        priceText: fmt.price(s.price),
        stock: s.stock || 0,
      }))
      // 图集：JSON 数组；解析失败退回主图
      let images = []
      try {
        const arr = p.images ? JSON.parse(p.images) : []
        images = Array.isArray(arr) && arr.length ? arr : [p.mainImage]
      } catch (e) {
        images = [p.mainImage]
      }
      const first = skuList.findIndex((s) => s.stock > 0)
      const idx = first < 0 ? 0 : first
      this.setData({
        loading: false,
        product: p,
        images: images.filter(Boolean),
        skuList,
        skuIndex: idx,
        sku: skuList[idx] || null,
        qty: 1,
        detailNodes: p.detail || '',
      })
      wx.setNavigationBarTitle({ title: p.name })
    } catch (e) {
      this.setData({ loading: false })
      wx.showToast({ title: e.message || '商品加载失败', icon: 'none' })
    }
  },

  onSku(e) {
    const idx = Number(e.currentTarget.dataset.index)
    const sku = this.data.skuList[idx]
    this.setData({ skuIndex: idx, sku, qty: 1 })
  },

  onQty(e) {
    const delta = Number(e.currentTarget.dataset.delta)
    const sku = this.data.sku
    const max = sku ? Math.min(sku.stock, 99) : 1
    let qty = this.data.qty + delta
    if (qty < 1) qty = 1
    if (qty > max) {
      qty = max
      wx.showToast({ title: '已达库存上限', icon: 'none' })
    }
    this.setData({ qty })
  },

  cartItem() {
    const { product, sku, qty } = this.data
    return {
      skuId: sku.skuId,
      productId: product.productId,
      name: product.name,
      image: product.mainImage,
      specs: sku.specs,
      specsText: sku.specsText,
      price: sku.price,
      qty,
    }
  },

  addCart() {
    const { sku, qty } = this.data
    if (!sku || sku.stock <= 0) {
      wx.showToast({ title: '该规格暂时缺货', icon: 'none' })
      return
    }
    app.addToCart(this.cartItem(), qty)
    wx.showToast({ title: '已加入购物车', icon: 'success' })
  },

  buyNow() {
    const { sku, qty } = this.data
    if (!sku || sku.stock <= 0) {
      wx.showToast({ title: '该规格暂时缺货', icon: 'none' })
      return
    }
    wx.navigateTo({ url: `/pages/order/confirm?skuId=${sku.skuId}&qty=${qty}` })
  },
})
