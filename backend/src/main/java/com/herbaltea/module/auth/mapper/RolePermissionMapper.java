package com.herbaltea.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.auth.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * role_permissions 表数据访问（模块边界：仅 auth 模块可读写）
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
