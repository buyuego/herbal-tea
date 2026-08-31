package com.herbaltea.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.auth.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * roles 表数据访问（模块边界：仅 auth 模块可读写）
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
