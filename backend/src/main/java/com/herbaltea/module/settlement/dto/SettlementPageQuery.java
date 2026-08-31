package com.herbaltea.module.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 结算单分页查询条件
 */
@Data
@Schema(description = "结算单分页查询条件")
public class SettlementPageQuery {

    @Schema(description = "结算单号模糊")
    private String settleNo;

    @Schema(description = "门店 id（总部筛选用）")
    private Long storeId;

    @Schema(description = "状态 10待确认/20平台审核/30已结算/40已打款/90已冲正")
    private Integer status;

    @Schema(description = "结算周期（2026-08-30 或 2026-W35）")
    private String period;

    @Schema(description = "页码")
    private Integer page = 1;

    @Schema(description = "每页条数")
    private Integer size = 10;
}
