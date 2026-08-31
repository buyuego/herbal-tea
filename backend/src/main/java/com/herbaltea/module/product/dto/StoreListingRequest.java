package com.herbaltea.module.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 店铺上架请求（门店选品 + 本店定价）
 *
 * @param price 本店售价（须在平台建议价 80%-120% 区间）
 */
public record StoreListingRequest(
        @NotNull(message = "商品不能为空") Long productId,
        @NotNull(message = "SKU 不能为空") Long skuId,
        @NotNull(message = "本店售价不能为空") @DecimalMin(value = "0.01", message = "本店售价须大于 0") BigDecimal price,
        @Min(value = 1, message = "每日配额须为正整数") Integer dailyQuota) {
}
