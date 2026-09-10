/** 展示层格式化工具 */

/** 金额：分转元、保留两位 */
function price(v) {
  const n = Number(v || 0)
  return n.toFixed(2)
}

/** 规格 JSON 串 → "规格:500ml / 包装:礼盒装"（解析失败原样返回） */
function specs(text) {
  if (!text) return ''
  try {
    const obj = typeof text === 'string' ? JSON.parse(text) : text
    return Object.keys(obj)
      .map((k) => k + ':' + obj[k])
      .join(' / ')
  } catch (e) {
    return text
  }
}

/** 时间：2026-09-01T10:00:00 → 2026-09-01 10:00 */
function time(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 16)
}

/** 手机号脱敏：138****1234 */
function phone(v) {
  if (!v || v.length !== 11) return v || ''
  return v.slice(0, 3) + '****' + v.slice(7)
}

module.exports = { price, specs, time, phone }
