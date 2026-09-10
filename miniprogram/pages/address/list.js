const addressApi = require('../../api/address')

Page({
  data: {
    list: [],
    pick: false,
    loading: true,
  },

  onLoad(query) {
    this.setData({ pick: query.pick === '1' })
  },

  onShow() {
    this.load()
  },

  async load() {
    this.setData({ loading: true })
    try {
      this.setData({ list: (await addressApi.list()) || [], loading: false })
    } catch (e) {
      this.setData({ loading: false })
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  onTap(e) {
    const id = Number(e.currentTarget.dataset.id)
    const addr = this.data.list.find((a) => a.id === id)
    if (!addr) return
    if (this.data.pick) {
      // 选择模式：回传给「确认订单」页
      const pages = getCurrentPages()
      const prev = pages[pages.length - 2]
      if (prev) {
        prev.setData({ address: addr })
        if (typeof prev.recalc === 'function') prev.recalc()
      }
      wx.navigateBack()
      return
    }
    wx.navigateTo({ url: '/pages/address/edit?id=' + id })
  },

  add() {
    wx.navigateTo({ url: '/pages/address/edit' })
  },

  remove(e) {
    const id = Number(e.currentTarget.dataset.id)
    wx.showModal({
      title: '删除地址',
      content: '确定删除该收货地址？',
      success: (res) => {
        if (!res.confirm) return
        addressApi
          .remove(id)
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' })
            this.load()
          })
          .catch((err) => wx.showToast({ title: err.message || '删除失败', icon: 'none' }))
      },
    })
  },
})
