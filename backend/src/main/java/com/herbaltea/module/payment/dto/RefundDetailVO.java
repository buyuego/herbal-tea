package com.herbaltea.module.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单详情（B 端退款售后详情抽屉：退款单 + 订单头 + 退货单）
 */
@Data
@Schema(description = "退款单详情")
public class RefundDetailVO {

    // ===== 退款单 =====
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
    @Schema(description = "驳回原因")
    private String rejectReason;
    @Schema(description = "驳回时间")
    private LocalDateTime rejectedAt;
    @Schema(description = "退款完成时间")
    private LocalDateTime handledAt;
    @Schema(description = "申请时间")
    private LocalDateTime createdAt;

    // ===== 订单头 =====
    @Schema(description = "实付金额")
    private BigDecimal payAmount;
    @Schema(description = "收货人")
    private String receiverName;
    @Schema(description = "收货电话")
    private String receiverPhone;
    @Schema(description = "收货地址（快照）")
    private String receiverAddress;
    @Schema(description = "支付时间")
    private LocalDateTime paidAt;
    @Schema(description = "订单发货状态（warehouse_status）")
    private Integer orderWarehouseStatus;
    @Schema(description = "订单发货状态描述")
    private String orderWarehouseStatusDesc;

    // ===== 退货单（refund_branch=3 时才有） =====
    @Schema(description = "退货单 id")
    private Long returnId;
    @Schema(description = "退货单状态：0申请中/1待寄回/2在途/3待验货/4已完结/5已取消")
    private Integer returnStatus;
    @Schema(description = "退货单状态描述")
    private String returnStatusDesc;
    @Schema(description = "总部退货寄回地址")
    private String returnAddress;
    @Schema(description = "退货物流单号")
    private String returnTrackingNo;
    @Schema(description = "退货快递公司")
    private String returnCarrier;
    @Schema(description = "总部收货状态：1待收货/2已收货/3验货通过/4验货不通过")
    private Integer warehouseStatus;
    @Schema(description = "总部收货状态描述")
    private String warehouseStatusDesc;
    @Schema(description = "验货结论")
    private String inspectionResult;
    @Schema(description = "验货仓管")
    private Long inspectedBy;
    @Schema(description = "总部收货人")
    private Long receivedBy;
    @Schema(description = "总部收货时间")
    private LocalDateTime receivedAt;
}
