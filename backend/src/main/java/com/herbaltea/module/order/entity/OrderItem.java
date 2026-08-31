package com.herbaltea.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseCreatedOnlyEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * order_items 表实体（订单明细快照，对齐 V1__schema.sql 权威结构）
 *
 * <p>下单时全量快照（商品名/规格/主图/成交价），后续目录变更不影响历史订单。
 * specs 为 JSON 对象字符串（与 product_skus.specs 同构）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_items")
public class OrderItem extends BaseCreatedOnlyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单（orders.id） */
    private Long orderId;

    /** 商品（products.id，快照归属） */
    private Long productId;

    /** SKU（product_skus.id） */
    private Long skuId;

    /** 商品名快照 */
    private String name;

    /** 规格 JSON 快照（{"规格":"500ml","包装":"袋装"}） */
    private String specs;

    /** 主图快照 */
    private String image;

    /** 成交单价（快照） */
    private BigDecimal price;

    /** 数量 */
    private Integer qty;

    /** 小计（price × qty） */
    private BigDecimal subtotal;
}
