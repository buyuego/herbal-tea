package com.herbaltea.module.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 退款单分页查询（B 端退款售后列表）
 */
@Data
@Schema(description = "退款单分页查询")
public class RefundPageQuery {

    @Schema(description = "退款单号（精确）")
    private String refundNo;

    @Schema(description = "订单号（精确）")
    private String orderNo;

    @Schema(description = "门店 id（总部可筛，门店管理员忽略）")
    private Long storeId;

    @Schema(description = "状态（10-95，空=全部）")
    private Integer status;

    @Schema(description = "退款分支（1未发货直退/2在途拦截/3已签收退货）")
    private Integer refundBranch;

    @Schema(description = "页码，默认 1")
    private long page = 1;

    @Schema(description = "每页条数，默认 20，上限 100")
    private long size = 20;
}
