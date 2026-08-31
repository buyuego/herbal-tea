package com.herbaltea.module.settlement.dto;

import com.herbaltea.module.settlement.entity.SettlementItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算单详情（结算单 + 门店 + 明细行 D15）
 */
@Data
@Schema(description = "结算单详情")
public class SettlementDetailVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "结算单号")
    private String settleNo;

    @Schema(description = "门店 id")
    private Long storeId;

    @Schema(description = "门店名称")
    private String storeName;

    @Schema(description = "结算周期")
    private String period;

    @Schema(description = "1日结/2周结/3调整单")
    private Integer type;

    @Schema(description = "订单数")
    private Integer orderCount;

    @Schema(description = "销售总额")
    private BigDecimal totalAmount;

    @Schema(description = "平台佣金")
    private BigDecimal commissionAmount;

    @Schema(description = "门店营销积分抵扣")
    private BigDecimal pointsDeductAmount;

    @Schema(description = "门店营销积分成本")
    private BigDecimal pointsCostStore;

    @Schema(description = "平台活动积分成本（平台补贴）")
    private BigDecimal pointsCostPlatform;

    @Schema(description = "本店券成本")
    private BigDecimal couponCostStore;

    @Schema(description = "退款冲正")
    private BigDecimal refundAdjust;

    @Schema(description = "调整单金额")
    private BigDecimal adjustAmount;

    @Schema(description = "实际到账")
    private BigDecimal finalAmount;

    @Schema(description = "0待确认/1自动确认/2人工确认/3有异议")
    private Integer confirmStatus;

    @Schema(description = "10待确认/20平台审核/30已结算/40已打款/90已冲正")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "自动确认时间")
    private LocalDateTime autoConfirmAt;

    @Schema(description = "确认时间")
    private LocalDateTime confirmedAt;

    @Schema(description = "异议说明")
    private String disputeNote;

    @Schema(description = "审核人")
    private Long reviewedBy;

    @Schema(description = "打款时间")
    private LocalDateTime paidAt;

    @Schema(description = "打款流水号")
    private String payoutNo;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "乐观锁版本")
    private Integer version;

    @Schema(description = "明细行（按积分来源分行，D15）")
    private List<SettlementItem> items;
}
