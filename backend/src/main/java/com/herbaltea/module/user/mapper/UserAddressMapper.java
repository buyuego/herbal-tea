package com.herbaltea.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.user.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货地址 Mapper（user_addresses，User 模块维护）
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {
}
