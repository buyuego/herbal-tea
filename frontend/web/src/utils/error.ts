/**
 * 后端业务错误码 → 前端可读文案映射
 * 错误码体系（ResultCode）：0 成功 / 40000 参数 / 40100 未认证 / 40101 令牌吊销 / 40300 无权限
 * / 40400 不存在 / 40900 冲突 / 40901 幂等重复 / 42900 限流 / 50000 内部
 */
export const ERROR_MESSAGES: Record<number, string> = {
  40000: '请求参数有误',
  40100: '未登录或登录已过期，请重新登录',
  40101: '令牌已失效（账号信息已变更），请重新登录',
  40300: '没有操作权限',
  40400: '数据不存在',
  40900: '操作冲突，请刷新后重试',
  40901: '重复提交，请勿重复操作',
  42900: '操作过于频繁，请稍后再试',
  50000: '服务异常，请稍后再试',
}

export class BizError extends Error {
  readonly code: number

  constructor(code: number, message?: string) {
    super(message || ERROR_MESSAGES[code] || `请求失败（${code}）`)
    this.name = 'BizError'
    this.code = code
  }
}

/** 需要重新登录的错误码（401 家族：access 过期/吊销） */
export const REAUTH_CODES = [40100, 40101]
