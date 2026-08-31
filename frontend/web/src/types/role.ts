/**
 * 角色权限管理（v19，RBAC 管理面；system:role:config 敏感权限，超管专属）
 * 后端契约：SystemRoleController（/api/system）
 */

/** 角色视图（GET /api/system/roles，含权限 id 集合与绑定管理员数） */
export interface RoleItem {
  id: number
  /** 角色编码（创建后不可改，唯一） */
  code: string
  name: string
  /** 数据范围：GLOBAL 全部 / MULTI_STORE 多门店 / SINGLE_STORE 单门店 */
  dataScope: string
  /** 级别：1 平台级 / 2 店铺级 */
  level: number
  /** 1 预设（不可删，data_scope/level 锁定）/ 0 自定义 */
  isPreset: number
  description: string | null
  /** 已授权权限 id 集合（授权页回显勾选态） */
  permissionIds: number[]
  /** 绑定管理员数（删除前置判断） */
  adminCount: number
  createdAt: string
}

/** 创建角色（code 全局唯一，创建后不可改；自定义角色上限 10） */
export interface RoleCreatePayload {
  code: string
  name: string
  dataScope: 'GLOBAL' | 'MULTI_STORE' | 'SINGLE_STORE'
  level: 1 | 2
  description?: string
}

/** 更新角色（code 不可改；预设角色 data_scope/level 锁定） */
export interface RoleUpdatePayload {
  name: string
  dataScope: 'GLOBAL' | 'MULTI_STORE' | 'SINGLE_STORE'
  level: 1 | 2
  description?: string
}

/** 角色授权（全量覆盖：permissionIds 为最终态；空列表=清空授权） */
export interface RoleAuthPayload {
  permissionIds: number[]
}

/** 权限树节点（GET /api/system/permissions，菜单→按钮→接口三级） */
export interface PermissionNode {
  id: number
  code: string
  name: string
  module: string
  /** 1 菜单 / 2 按钮 / 3 接口 */
  type: number
  parentId: number | null
  path: string | null
  /** 1 敏感（仅超管角色可授予，非超管授权时置灰） */
  isSensitive: number
  children: PermissionNode[]
}
