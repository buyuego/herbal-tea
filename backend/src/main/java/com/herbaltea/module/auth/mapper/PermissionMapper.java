package com.herbaltea.module.auth.mapper;

import com.herbaltea.module.auth.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色权限查询（RBAC，设计文档 15.1）。
 * 权限校验统一走 Auth 模块（AuthServiceImpl 注册的 PermissionProvider），禁止跨模块直读。
 * v10 起同时承担权限树 / 敏感标记查询（角色权限管理接口数据源）。
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

    /** 角色已绑定的权限 id 集合（授权页回显 / 全量覆盖比对） */
    @Select("SELECT permission_id FROM role_permissions WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(Long roleId);

    /** 全部权限（权限树构建 / 授权可选集；按 id 稳定排序保证树顺序） */
    @Select("SELECT * FROM permissions ORDER BY id")
    List<Permission> selectAll();

    /** 按 id 批量查询权限（授权入参校验：存在性 + 敏感标记） */
    @Select("<script>"
            + "SELECT * FROM permissions WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Permission> selectByIds(@Param("ids") List<Long> ids);
}
