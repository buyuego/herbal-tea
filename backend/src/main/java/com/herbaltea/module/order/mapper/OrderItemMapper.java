package com.herbaltea.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细 Mapper（order_items）
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
