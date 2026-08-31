package com.herbaltea.module.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色权限查询（RBAC，设计文档 15.1）。
 * 权限校验统一走 Auth 模块（AuthServiceImpl 注册的 PermissionProvider），禁止跨模块直读。
 */
@Mapper
public interface PermissionMapper {

    /** 角色拥有的全部权限码（permissions.code） */
    @Select("""
            SELECT p.code
            FROM role_permissions rp
            JOIN permissions p ON p.id = rp.permission_id
            WHERE rp.role_id = #{roleId}
            ORDER BY p.id
            """)
    List<String> selectCodesByRoleId(Long roleId);
}
