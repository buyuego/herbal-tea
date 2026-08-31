package com.herbaltea.module.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 下单请求（C 端小程序 / B 端代客下单共用）
 *
 * <p>C 端：userId 从登录上下文取；B 端代客下单：userId 由调用方指定（order:create:behalf 权限）。
 * 幂等：Idempotency-Key 请求头（24h 窗口，Redis SETNX + DB 唯一索引兜底，D1）。
 */
@Schema(description = "下单请求")
public record CreateOrderRequest(

        @Schema(description = "买家用户 id（B 端代客下单必填；C 端从登录态取，忽略此字段）")
        Long userId,

        @NotNull(message = "门店不能为空")
        @Schema(description = "业绩归属门店 id")
        Long storeId,

        @NotNull(message = "SKU 不能为空")
        @Schema(description = "SKU id")
        Long skuId,

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量至少为 1")
        @Schema(description = "购买数量")
        Integer qty,

        @NotNull(message = "收货地址不能为空")
        @Schema(description = "收货地址 id（user_addresses）")
        Long addressId,

        @Schema(description = "买家备注")
        String remark,

        @Min(value = 0, message = "使用积分不能为负")
        @Schema(description = "使用的积分数量（1 积分抵扣 0.01 元；不填或 0 = 不使用积分）")
        Long usePoints
) {
}
