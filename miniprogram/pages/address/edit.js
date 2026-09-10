const addressApi = require('../../api/address')

Page({
  data: {
    id: 0,
    form: {
      receiverName: '',
      phone: '',
      province: '',
      city: '',
      district: '',
      detail: '',
      isDefault: 0,
    },
    region: [],
    saving: false,
  },

  onLoad(query) {
    if (query.id) {
      this.setData({ id: Number(query.id) })
      this.loadAddress(Number(query.id))
    }
  },

  async loadAddress(id) {
    try {
      const list = (await addressApi.list()) || []
      const a = list.find((x) => x.id === id)
      if (!a) return
      this.setData({
        form: {
          receiverName: a.receiverName || '',
          phone: a.phone || '',
          province: a.province || '',
          city: a.city || '',
          district: a.district || '',
          detail: a.detail || '',
          isDefault: a.isDefault || 0,
        },
        region: [a.province || '', a.city || '', a.district || ''],
      })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  onInput(e) {
    const key = e.currentTarget.dataset.key
    this.setData({ ['form.' + key]: e.detail.value })
  },

  onRegion(e) {
    const [province, city, district] = e.detail.value
    this.setData({
      region: e.detail.value,
      'form.province': province,
      'form.city': city,
      'form.district': district,
    })
  },

  onDefault(e) {
    this.setData({ 'form.isDefault': e.detail.value ? 1 : 0 })
  },

  save() {
    const f = this.data.form
    if (!f.receiverName) return this.tip('请填写收货人')
    if (!/^1\d{10}$/.test(f.phone)) return this.tip('请填写正确的手机号')
    if (!f.province) return this.tip('请选择所在地区')
    if (!f.detail) return this.tip('请填写详细地址')
    if (this.data.saving) return

    this.setData({ saving: true })
    const req = this.data.id ? addressApi.update(this.data.id, f) : addressApi.create(f)
    req
      .then(() => {
        wx.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => wx.navigateBack(), 600)
      })
      .catch((err) => {
        this.setData({ saving: false })
        wx.showToast({ title: err.message || '保存失败', icon: 'none' })
      })
  },

  tip(msg) {
    wx.showToast({ title: msg, icon: 'none' })
  },
})
