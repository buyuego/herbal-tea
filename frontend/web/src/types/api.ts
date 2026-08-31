/**
 * 后端统一返回结构（Result<T>）：code=0 成功；非 0 为业务错误码
 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

/** 双令牌（AuthServiceImpl.TokenPair） */
export interface TokenPair {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

/** 当前管理员信息（GET /api/auth/admin/me） */
export interface AdminProfile {
  adminId: number
  username: string
  realName: string | null
  roleId: number
  roleName: string | null
  permissionCodes: string[]
}

/** 可切换门店（GET /api/store/my-stores） */
export interface StoreBinding {
  storeId: number
  storeNo: string
  storeName: string
  /** 1=店主（主店，登录默认进入） */
  isOwner: number
  /** 是否当前上下文门店（JWT sid） */
  current: boolean
}
