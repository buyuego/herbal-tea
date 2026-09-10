const orderApi = require('../../api/order')
const fmt = require('../../utils/format')

/** 订单状态文案（与后端 OrderStatus 对齐） */
const STATUS_TEXT = {
  10: '待付款',
  20: '已支付',
  30: '待发货',
  40: '待收货',
  50: '已签收',
  60: '退款中',
  70: '已关闭',
  80: '已退款',
  90: '已完成',
}

const TABS = [
  { label: '全部', status: '' },
  { label: '待付款', status: 10 },
  { label: '待发货', status: 30 },
  { label: '待收货', status: 40 },
  { label: '已完成', status: 90 },
]

Page({
  data: {
    tabs: TABS,
    tabIndex: 0,
    list: [],
    total: 0,
    page: 1,
    size: 10,
    loading: false,
    finished: false,
  },

  onLoad(query) {
    const status = query.status === undefined ? '' : String(query.status)
    const idx = TABS.findIndex((t) => String(t.status) === status)
    this.setData({ tabIndex: idx < 0 ? 0 : idx })
  },

  onShow() {
    this.reload()
  },

  onReachBottom() {
    this.load()
  },

  onTab(e) {
    const idx = Number(e.currentTarget.dataset.index)
    if (idx === this.data.tabIndex) return
    this.setData({ tabIndex: idx })
    this.reload()
  },

  reload() {
    this.setData({ list: [], page: 1, finished: false, total: 0 })
    this.load()
  },

  async load() {
    const { tabs, tabIndex, page, size, loading, finished } = this.data
    if (loading || finished) return
    this.setData({ loading: true })
    try {
      const params = { page, size }
      if (tabs[tabIndex].status !== '') params.status = tabs[tabIndex].status
      const res = await orderApi.mine(params)
      const rows = (res.records || []).map((o) => ({
        id: o.id,
        orderNo: o.orderNo,
        statusText: STATUS_TEXT[o.status] || ('#' + o.status),
        status: o.status,
        payAmountText: fmt.price(o.payAmount),
        totalAmountText: fmt.price(o.totalAmount),
        couponText: Number(o.couponAmount) > 0 ? fmt.price(o.couponAmount) : '',
        pointsText: Number(o.pointsDeduct) > 0 ? String(o.pointsDeduct) : '',
        createdText: fmt.time(o.createdAt),
        expireText: o.status === 10 ? fmt.time(o.expireAt) : '',
      }))
      this.setData({
        list: this.data.list.concat(rows),
        total: res.total || 0,
        page: page + 1,
        finished: rows.length < size,
      })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
      this.setData({ finished: true })
    } finally {
      this.setData({ loading: false })
    }
  },

  toDetail(e) {
    wx.navigateTo({ url: '/pages/order/detail?id=' + e.currentTarget.dataset.id })
  },

  pay(e) {
    const id = e.currentTarget.dataset.id
    wx.showLoading({ title: '支付中' })
    orderApi
      .pay(id)
      .then(() => {
        wx.hideLoading()
        wx.showToast({ title: '支付成功', icon: 'success' })
        this.reload()
      })
      .catch((err) => {
        wx.hideLoading()
        wx.showToast({ title: err.message || '支付失败', icon: 'none' })
      })
  },

  cancel(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '取消订单',
      content: '确定取消该订单？库存将被释放。',
      success: (res) => {
        if (!res.confirm) return
        orderApi
          .cancel(id)
          .then(() => {
            wx.showToast({ title: '已取消', icon: 'success' })
            this.reload()
          })
          .catch((err) => wx.showToast({ title: err.message || '取消失败', icon: 'none' }))
      },
    })
  },

  sign(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认收货',
      content: '确认已收到商品？',
      success: (res) => {
        if (!res.confirm) return
        orderApi
          .sign(id)
          .then(() => {
            wx.showToast({ title: '已确认收货', icon: 'success' })
            this.reload()
          })
          .catch((err) => wx.showToast({ title: err.message || '操作失败', icon: 'none' }))
      },
    })
  },
})
