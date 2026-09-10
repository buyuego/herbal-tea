const marketingApi = require('../../api/marketing')
const fmt = require('../../utils/format')

const TABS = [
  { label: '可用', status: 0 },
  { label: '已使用', status: 1 },
  { label: '已过期', status: 2 },
  { label: '退款退回', status: 3 },
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

  onLoad() {
    this.load()
  },

  onReachBottom() {
    this.load()
  },

  onTab(e) {
    const idx = Number(e.currentTarget.dataset.index)
    if (idx === this.data.tabIndex) return
    this.setData({ tabIndex: idx, list: [], page: 1, finished: false, total: 0 })
    this.load()
  },

  async load() {
    const { tabs, tabIndex, page, size, loading, finished } = this.data
    if (loading || finished) return
    this.setData({ loading: true })
    try {
      const res = await marketingApi.myCoupons({
        status: tabs[tabIndex].status,
        page,
        size,
      })
      const rows = (res.records || []).map((c) =>
        Object.assign({}, c, {
          discountText: c.type === 1 ? '¥' + fmt.price(c.discountAmount) : '折扣券',
          thresholdText: Number(c.thresholdAmount) > 0 ? '满 ¥' + fmt.price(c.thresholdAmount) + ' 可用' : '无门槛',
          expireText: fmt.time(c.expireAt).slice(0, 10),
          usedText: c.usedAt ? fmt.time(c.usedAt) : '',
        })
      )
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
})
