package com.herbaltea.module.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 订单明细视图
 *
 * @param skuId  SKU id
 * @param name   商品名快照
 * @param specs  规格 JSON 对象（已解析为 Map）
 * @param image  主图快照
 * @param price  成交单价
 * @param qty    数量
 * @param subtotal 小计
 */
@Schema(description = "订单明细视图")
public record OrderItemVO(Long skuId, String name, Object specs, String image,
                          BigDecimal price, Integer qty, BigDecimal subtotal) {
}
