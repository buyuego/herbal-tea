package com.herbaltea.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.order.entity.OrderShippingLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物流日志 Mapper（order_shipping_logs）
 */
@Mapper
public interface OrderShippingLogMapper extends BaseMapper<OrderShippingLog> {
}
