package com.herbaltea.module.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单分页行（JOIN orders / stores / users 联查，B 端退款售后列表）
 */
@Data
@Schema(description = "退款单分页行")
public class RefundPageVO {

    @Schema(description = "退款单 id")
    private Long id;

    @Schema(description = "退款单号")
    private String refundNo;

    @Schema(description = "关联订单 id")
    private Long orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "归属门店 id")
    private Long storeId;

    @Schema(description = "归属门店名")
    private String storeName;

    @Schema(description = "买家昵称")
    private String userName;

    @Schema(description = "买家手机号")
    private String userPhone;

    @Schema(description = "退款金额")
    private BigDecimal amount;

    @Schema(description = "退款原因")
    private String reason;

    @Schema(description = "退款分支：1未发货直退/2在途拦截/3已签收退货")
    private Integer refundBranch;

    @Schema(description = "退款分支描述")
    private String refundBranchDesc;

    @Schema(description = "状态：10待审批/20审批通过/30退款中/40已退款/50已驳回/95回退失败")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "升级标记：0正常/1超时升级总部/2风控升级总部")
    private Integer escalationStatus;

    @Schema(description = "实际审批方：1店铺/2总部兜底/3超管风控")
    private Integer approvedByLevel;

    @Schema(description = "审批人")
    private Long approvedBy;

    @Schema(description = "审批时间")
    private LocalDateTime approvedAt;

    @Schema(description = "退款完成时间")
    private LocalDateTime handledAt;

    @Schema(description = "创建时间（申请时间）")
    private LocalDateTime createdAt;

    // ===== 退货单摘要（refund_branch=3 时才有） =====

    @Schema(description = "退货单状态：0申请中/1待寄回/2在途/3待验货/4已完结/5已取消")
    private Integer returnStatus;

    @Schema(description = "退货单状态描述")
    private String returnStatusDesc;

    @Schema(description = "总部收货状态：1待收货/2已收货/3验货通过/4验货不通过")
    private Integer warehouseStatus;

    @Schema(description = "总部收货状态描述")
    private String warehouseStatusDesc;

    @Schema(description = "退货物流单号")
    private String returnTrackingNo;
}
