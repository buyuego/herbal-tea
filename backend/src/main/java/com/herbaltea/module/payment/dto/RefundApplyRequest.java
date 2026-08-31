package com.herbaltea.module.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 退款申请请求（门店发起，refund:submit）
 */
@Data
@Schema(description = "退款申请请求")
public class RefundApplyRequest {

    @NotNull(message = "orderId 必填")
    @Schema(description = "订单 id（已支付且未完结）")
    private Long orderId;

    @Schema(description = "退款原因")
    private String reason;
}
