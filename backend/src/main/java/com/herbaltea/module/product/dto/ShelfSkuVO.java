package com.herbaltea.module.product.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * C 端货架 SKU（v29）：本店定价 + 可售库存
 */
@Data
public class ShelfSkuVO {

    private Long skuId;

    private String skuCode;

    /** 规格 JSON 字符串，如 {"规格":"500ml","包装":"礼盒装"} */
    private String specs;

    /** 本店售价（store_products.price） */
    private BigDecimal price;

    /** 可售库存（总仓库存） */
    private Integer stock;
}
