package com.herbaltea.module.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 店铺改价请求（同样校验 80%-120% 区间）
 */
public record StorePriceRequest(
        @NotNull(message = "本店售价不能为空") @DecimalMin(value = "0.01", message = "本店售价须大于 0") BigDecimal price) {
}
