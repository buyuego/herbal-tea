package com.herbaltea.module.product.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 库存调整请求（入库 / 盘点调整共用）
 *
 * @param changeType 1入库 / 3盘点调整
 * @param changeQty  变动数量：入库为正；盘点为实际差值（可正可负，不允许 0，Service 层校验）
 */
public record StockAdjustRequest(
        @NotNull(message = "SKU 不能为空") Long skuId,
        @NotNull(message = "变动类型不能为空") Integer changeType,
        @NotNull(message = "变动数量不能为空") Integer changeQty,
        String bizNo,
        String note) {
}
