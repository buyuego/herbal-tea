package com.herbaltea.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.auth.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
}
