package com.herbaltea.module.product.dto;

import com.herbaltea.module.product.entity.ProductSku;

import java.math.BigDecimal;
import java.util.List;

/**
 * 平台商品详情 VO（商品头 + SKU 列表）
 *
 * @param images 轮播图 URL 列表（JSON 列已解析）
 */
public record ProductDetailVO(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String subtitle,
        String formula,
        String mainImage,
        List<String> images,
        String detail,
        BigDecimal suggestedPrice,
        BigDecimal costPrice,
        Integer status,
        List<ProductSku> skus) {
}
