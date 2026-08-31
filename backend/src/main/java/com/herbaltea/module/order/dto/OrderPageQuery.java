package com.herbaltea.module.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单分页查询（B 端订单管理）
 */
@Data
@Schema(description = "订单分页查询")
public class OrderPageQuery {

    @Schema(description = "订单号（精确）")
    private String orderNo;

    @Schema(description = "买家用户 id")
    private Long userId;

    @Schema(description = "门店 id")
    private Long storeId;

    @Schema(description = "状态（10-95，空=全部）")
    private Integer status;

    @Schema(description = "页码，默认 1")
    private long page = 1;

    @Schema(description = "每页条数，默认 20，上限 100")
    private long size = 20;
}
