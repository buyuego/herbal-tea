package com.herbaltea.module.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款率（v31）。
 *
 * <p>口径：分子 = 已退款订单数（status ∈ {60,80}，含退款中与已退款），
 * 分母 = 已支付订单数（status ∈ {20,30,40,50,90}）。
 */
@Data
@Schema(description = "退款率（区间内）")
public class RefundRateVO {

    @Schema(description = "退款率（百分比，保留 2 位小数，字符串以避免 Jackson 丢精度）")
    private String rate;

    @Schema(description = "已退款订单数（分子）")
    private Long refundCount;

    @Schema(description = "已支付订单数（分母）")
    private Long paidCount;
}