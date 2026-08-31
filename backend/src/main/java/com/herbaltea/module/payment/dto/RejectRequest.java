package com.herbaltea.module.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 退款驳回请求（refund:approve）
 */
@Data
@Schema(description = "退款驳回请求")
public class RejectRequest {

    @Schema(description = "驳回原因")
    private String reason;
}
