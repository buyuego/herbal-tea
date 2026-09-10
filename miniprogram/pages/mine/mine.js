const app = getApp()
const userApi = require('../../api/user')
const marketingApi = require('../../api/marketing')
const fmt = require('../../utils/format')

Page({
  data: {
    profile: null,
    phoneText: '',
    points: { balance: 0, totalEarned: 0, totalUsed: 0, totalExpired: 0 },
    couponCount: 0,
    storeName: '',
    loading: true,
  },

  onShow() {
    const s = app.globalData.store
    this.setData({ storeName: s ? s.storeName : '未选择' })
    this.loadAll()
  },

  async loadAll() {
    this.setData({ loading: true })
    const [profile, points, coupons] = await Promise.all([
      userApi.profile().catch(() => null),
      marketingApi.myPoints().catch(() => null),
      marketingApi.myCoupons({ status: 0, page: 1, size: 1 }).catch(() => null),
    ])
    this.setData({
      loading: false,
      profile,
      phoneText: profile && profile.phone ? fmt.phone(profile.phone) : '未绑定',
      points: points || this.data.points,
      couponCount: coupons ? coupons.total || 0 : 0,
    })
  },

  toOrders(e) {
    const status = e.currentTarget.dataset.status || ''
    wx.navigateTo({ url: '/pages/order/list' + (status ? '?status=' + status : '') })
  },

  toCoupons() {
    wx.navigateTo({ url: '/pages/coupon/mine' })
  },

  toAddress() {
    wx.navigateTo({ url: '/pages/address/list' })
  },
})
