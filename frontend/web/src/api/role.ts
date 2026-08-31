import { http } from '@/utils/request'
import type {
  PermissionNode,
  RoleAuthPayload,
  RoleCreatePayload,
  RoleItem,
  RoleUpdatePayload,
} from '@/types/role'

/** 角色列表（system:role:config 敏感权限，超管专属） */
export const listRolesApi = () => http.get<RoleItem[]>('/system/roles')

/** 角色详情 */
export const getRoleApi = (id: number) => http.get<RoleItem>(`/system/roles/${id}`)

/** 创建角色（code 唯一且不可改；自定义角色上限 10） */
export const createRoleApi = (data: RoleCreatePayload) => http.post<RoleItem>('/system/roles', data)

/** 更新角色（预设角色仅 name/description 可改，data_scope/level 锁定） */
export const updateRoleApi = (id: number, data: RoleUpdatePayload) =>
  http.put<RoleItem>(`/system/roles/${id}`, data)

/** 删除角色（预设不可删；有绑定管理员拒删） */
export const deleteRoleApi = (id: number) => http.delete<void>(`/system/roles/${id}`)

/** 角色授权（全量覆盖；授权后该角色全部管理员旧令牌即时失效） */
export const assignRolePermissionsApi = (id: number, data: RoleAuthPayload) =>
  http.put<void>(`/system/roles/${id}/permissions`, data)

/** 权限树（菜单→按钮→接口三级；isSensitive=1 节点仅超管角色可勾选） */
export const permissionTreeApi = () => http.get<PermissionNode[]>('/system/permissions')

export type { PermissionNode, RoleAuthPayload, RoleCreatePayload, RoleItem, RoleUpdatePayload }
