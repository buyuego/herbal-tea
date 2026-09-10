const app = getApp()
const storeApi = require('../../api/store')
const productApi = require('../../api/product')

Page({
  data: {
    storeName: '',
    storeId: 0,
    stores: [],
    categories: [],
    categoryId: 0,
    products: [],
    total: 0,
    page: 1,
    size: 10,
    loading: false,
    finished: false,
  },

  onLoad() {
    this.bootstrap()
  },

  onShow() {
    const s = app.globalData.store
    this.setData({ storeName: s ? s.storeName : '', storeId: app.getStoreId() })
    // 门店在其他页被切换过 → 货架需要重载
    if (this.data.storeId && this.data.storeId !== this._loadedStoreId) {
      this.loadCategories()
      this.reload()
    }
  },

  onPullDownRefresh() {
    this.reload(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadProducts()
  },

  async bootstrap() {
    await this.loadStores()
    const storeId = app.getStoreId()
    if (!storeId) {
      // 首次进入必须先选门店：C 端始终从具体门店购买
      this.chooseStore()
      return
    }
    this.setData({ storeName: app.globalData.store.storeName, storeId })
    await this.loadCategories()
    this.reload()
  },

  async loadStores() {
    try {
      this.setData({ stores: (await storeApi.listStores()) || [] })
    } catch (e) {
      wx.showToast({ title: e.message || '门店加载失败', icon: 'none' })
    }
  },

  async loadCategories() {
    try {
      this.setData({ categories: (await storeApi.listCategories()) || [] })
    } catch (e) {
      // 分类失败不阻塞商品列表
    }
  },

  chooseStore() {
    const stores = this.data.stores
    if (!stores.length) {
      wx.showToast({ title: '暂无营业门店', icon: 'none' })
      return
    }
    wx.showActionSheet({
      itemList: stores.slice(0, 6).map((s) => s.storeName),
      success: (res) => {
        const s = stores[res.tapIndex]
        app.setStore({ id: s.id, storeName: s.storeName })
        this.setData({ storeName: s.storeName, storeId: s.id })
        this.reload()
      },
      fail: () => {},
    })
  },

  onCate(e) {
    const id = Number(e.currentTarget.dataset.id)
    if (id === this.data.categoryId) return
    this.setData({ categoryId: id })
    this.reload()
  },

  /** 重载第一页 */
  reload(done) {
    this.setData({ products: [], page: 1, finished: false, total: 0 })
    this._loadedStoreId = this.data.storeId
    this.loadProducts(done)
  },

  async loadProducts(done) {
    const { storeId, categoryId, page, size, loading, finished } = this.data
    if (loading || finished || !storeId) {
      if (done) done()
      return
    }
    this.setData({ loading: true })
    try {
      const res = await productApi.pageShelf({
        storeId,
        categoryId: categoryId || '',
        page,
        size,
      })
      const rows = (res.records || []).map((p) => {
        const skus = p.skus || []
        const prices = skus.map((s) => Number(s.price))
        return Object.assign({}, p, {
          minPrice: prices.length ? Math.min.apply(null, prices).toFixed(2) : '0.00',
          soldOut: !skus.some((s) => (s.stock || 0) > 0),
        })
      })
      this.setData({
        products: this.data.products.concat(rows),
        total: res.total || 0,
        page: page + 1,
        finished: rows.length < size,
      })
    } catch (e) {
      wx.showToast({ title: e.message || '商品加载失败', icon: 'none' })
      this.setData({ finished: true })
    } finally {
      this.setData({ loading: false })
      if (done) done()
    }
  },

  toProduct(e) {
    wx.navigateTo({ url: '/pages/product/product?productId=' + e.currentTarget.dataset.id })
  },
})
