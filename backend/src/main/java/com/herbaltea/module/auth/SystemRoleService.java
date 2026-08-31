package com.herbaltea.module.auth;

import com.herbaltea.module.auth.dto.PermissionNodeVO;
import com.herbaltea.module.auth.dto.RoleCreateRequest;
import com.herbaltea.module.auth.dto.RoleUpdateRequest;
import com.herbaltea.module.auth.dto.RoleVO;

import java.util.List;

/**
 * 角色权限管理（v10，RBAC 管理面；system:role:config 敏感权限，超管专属）
 *
 * <p>与 {@link AuthServiceImpl} 的分工：本服务负责角色/授权的「写」与查询管理面，
 * AuthServiceImpl 的 PermissionProvider 负责登录态「校验」面（role_permissions 只读）。
 * 二者共享 Redis 权限缓存（{@code rbac:perms:{roleId}}）：授权变更后由本服务主动失效。
 */
public interface SystemRoleService {

    /** 角色列表（含权限 id 集合与绑定管理员数） */
    List<RoleVO> listRoles();

    /** 角色详情（不存在抛 40400） */
    RoleVO getRole(Long id);

    /** 创建角色：code 唯一、自定义角色上限 10、插入后返回 */
    RoleVO createRole(RoleCreateRequest req);

    /** 更新角色：预设角色仅 name/description 可改（data_scope/level 锁定） */
    RoleVO updateRole(Long id, RoleUpdateRequest req);

    /** 删除角色：预设不可删、有绑定管理员拒删（提示先改绑）；事务级联删授权关联 */
    void deleteRole(Long id);

    /**
     * 全量覆盖授权：权限 id 存在性校验、敏感权限仅 SUPER_ADMIN（role 1）可授予；
     * 事务删旧插新 + 该角色全部管理员 token_version 批量 +1（旧 JWT 即时吊销）+
     * Redis 权限缓存失效（权限变更秒级生效）。
     */
    void assignPermissions(Long id, List<Long> permissionIds);

    /** 权限树（菜单→按钮→接口三级），供授权页勾选 */
    List<PermissionNodeVO> permissionTree();
}
