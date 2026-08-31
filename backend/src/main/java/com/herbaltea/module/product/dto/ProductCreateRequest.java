package com.herbaltea.module.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 创建平台商品请求（总部）：商品头 + 初始 SKU 列表
 *
 * @param images 轮播图 URL 列表（可空）
 * @param specs  规格矩阵，如 {"规格":"500ml","包装":"礼盒装"}
 */
public record ProductCreateRequest(
        @NotNull(message = "分类不能为空") Long categoryId,
        @NotBlank(message = "商品名不能为空") String name,
        String subtitle,
        String formula,
        @NotBlank(message = "主图不能为空") String mainImage,
        List<String> images,
        String detail,
        @NotNull(message = "建议零售价不能为空") @DecimalMin(value = "0.01", message = "建议零售价须大于 0") BigDecimal suggestedPrice,
        @NotNull(message = "成本价不能为空") @DecimalMin(value = "0", message = "成本价不能为负") BigDecimal costPrice,
        @NotEmpty(message = "至少需要一个 SKU") @Valid List<SkuDraft> skus) {

    /**
     * 初始 SKU
     */
    public record SkuDraft(
            @NotBlank(message = "SKU 编码不能为空") String skuCode,
            Map<String, Object> specs,
            @NotNull(message = "SKU 价格不能为空") @DecimalMin(value = "0.01", message = "SKU 价格须大于 0") BigDecimal price,
            @NotNull(message = "SKU 成本价不能为空") @DecimalMin(value = "0", message = "SKU 成本价不能为负") BigDecimal costPrice,
            @NotNull(message = "初始库存不能为空") @Min(value = 0, message = "初始库存不能为负") Integer stock) {
    }
}
