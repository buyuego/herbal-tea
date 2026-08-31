package com.herbaltea.module.product.dto;

import java.math.BigDecimal;

/**
 * 下单视图：SKU + 本店在售价（Order 模块跨模块只读，走 ProductService 接口）
 *
 * @param skuId        SKU id
 * @param productId    商品 id
 * @param productName  商品名快照
 * @param mainImage    主图快照
 * @param specs        规格 JSON（原样快照）
 * @param price        本店在售价（store_products.price，未上架则抛 40400）
 * @param stock        当前库存（展示用）
 */
public record SkuForSaleVO(Long skuId, Long productId, String productName,
                           String mainImage, String specs, BigDecimal price, Integer stock) {
}
