package com.herbaltea.module.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新平台商品目录请求（总部，D14：不覆盖店铺定价，仅标记 catalog_dirty）
 */
public record ProductUpdateRequest(
        @NotNull(message = "分类不能为空") Long categoryId,
        @NotBlank(message = "商品名不能为空") String name,
        String subtitle,
        String formula,
        @NotBlank(message = "主图不能为空") String mainImage,
        List<String> images,
        String detail,
        @NotNull(message = "建议零售价不能为空") @DecimalMin(value = "0.01", message = "建议零售价须大于 0") BigDecimal suggestedPrice,
        @NotNull(message = "成本价不能为空") @DecimalMin(value = "0", message = "成本价不能为负") BigDecimal costPrice) {
}
