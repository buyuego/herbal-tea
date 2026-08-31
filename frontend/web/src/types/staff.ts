/**
 * 员工管理类型（对应后端 StoreStaffController，权限 store:staff:manage）
 */

/** 员工列表 VO（store_admins 联查 admin_users + roles） */
export interface StaffItem {
  /** admin_users.id */
  adminId: number
  /** 登录名 */
  username: string
  /** 姓名 */
  realName: string
  /** 手机号 */
  phone: string | null
  /** admin_users.status：0禁用 / 1正常 */
  adminStatus: number
  /** store_admins.status：1 正常绑定 / 0 已移除（软删可复绑） */
  bindStatus: number
  /** 角色 id（员工应为 5 = STORE_STAFF） */
  roleId: number
  /** 角色名 */
  roleName: string
  /** 1店主 / 0普通店员（店主不可被员工管理接口操作） */
  isOwner: number
  /** 绑定时间 */
  boundAt: string | null
  /** 最近登录时间 */
  lastLoginAt: string | null
}

/** 员工分页查询参数（boundStatus：null 全部 / 1 正常绑定 / 0 已移除） */
export type StaffPageQuery = {
  boundStatus?: number
  page: number
  size: number
}

/** 创建员工请求（不接收 roleId —— 门店只能创建 STORE_STAFF 角色，防提权） */
export interface StaffCreatePayload {
  /** 登录名：字母开头，3-32 位字母/数字/下划线；创建后不可改 */
  username: string
  /** 姓名 */
  realName: string
  /** 手机号（可选，^1\d{10}$） */
  phone?: string
  /** 初始密码（6-32 位） */
  password: string
}

/** 更新员工请求（username 与角色不可改） */
export interface StaffUpdatePayload {
  /** 姓名 */
  realName: string
  /** 手机号（可选） */
  phone?: string
  /** 0禁用 / 1正常（禁用时旧令牌即时失效） */
  status: number
}

/** 重置密码请求 */
export interface StaffPasswordPayload {
  /** 新密码（6-32 位） */
  newPassword: string
}
