package com.herbaltea.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.auth.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * admin_users 数据访问（模块边界：仅 auth 模块可读写权限表）
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    /**
     * R9 即时吊销：token_version + 1，使该管理员全部已签发 JWT 失效。
     *
     * @param adminId 管理员 id
     * @return 影响行数（0 表示用户不存在）
     */
    @Update("UPDATE admin_users SET token_version = token_version + 1 WHERE id = #{adminId}")
    int bumpTokenVersion(@Param("adminId") Long adminId);

    /**
     * 按角色批量吊销（v10 角色授权变更时调用）：该角色全部管理员 token_version + 1，
     * 旧 JWT 即时失效（R9），保证权限变更秒级生效（尤其敏感权限收回场景）。
     *
     * @param roleId 角色 id
     * @return 影响行数（该角色绑定管理员数）
     */
    @Update("UPDATE admin_users SET token_version = token_version + 1 WHERE role_id = #{roleId}")
    int bumpTokenVersionByRole(@Param("roleId") Long roleId);

    /** 角色绑定的管理员数（删除角色前置校验：有绑定拒删） */
    @Select("SELECT COUNT(*) FROM admin_users WHERE role_id = #{roleId}")
    long countByRole(Long roleId);
}
