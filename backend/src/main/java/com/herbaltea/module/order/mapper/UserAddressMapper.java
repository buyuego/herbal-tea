package com.herbaltea.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.order.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货地址 Mapper（user_addresses，只读：下单地址快照）
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {
}
