package com.herbaltea.module.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 为平台商品追加 SKU 请求
 */
public record SkuAddRequest(
        @NotBlank(message = "SKU 编码不能为空") String skuCode,
        Map<String, Object> specs,
        @NotNull(message = "SKU 价格不能为空") @DecimalMin(value = "0.01", message = "SKU 价格须大于 0") BigDecimal price,
        @NotNull(message = "SKU 成本价不能为空") @DecimalMin(value = "0", message = "SKU 成本价不能为负") BigDecimal costPrice,
        @NotNull(message = "初始库存不能为空") @Min(value = 0, message = "初始库存不能为负") Integer stock) {
}
