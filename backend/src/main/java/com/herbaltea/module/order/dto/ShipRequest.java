package com.herbaltea.module.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 发货请求（B 端仓管，30待发货 → 40已发货）
 */
@Schema(description = "发货请求")
public record ShipRequest(

        @NotBlank(message = "物流单号不能为空")
        @Schema(description = "物流单号")
        String logisticsNo,

        @NotBlank(message = "快递公司不能为空")
        @Schema(description = "快递公司")
        String carrier,

        @Schema(description = "备注（写入物流轨迹）")
        String note
) {
}
