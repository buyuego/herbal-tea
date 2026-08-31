package com.herbaltea.module.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退货验货请求（总部仓管，return:inspect）
 */
@Data
@Schema(description = "退货验货请求")
public class InspectRequest {

    @NotBlank(message = "验货结论必填")
    @Schema(description = "验货结论：完好退全款/破损部分退款/非质量问题拒退")
    private String result;

    @Schema(description = "破损部分退款金额（结论=破损部分退款时填写，小于原退款额）")
    private BigDecimal refundAmount;
}
