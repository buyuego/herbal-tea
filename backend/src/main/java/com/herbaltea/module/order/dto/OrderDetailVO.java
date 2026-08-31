package com.herbaltea.module.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情视图（订单头 + 明细 + 支付单 + 物流轨迹）
 */
@Data
@Schema(description = "订单详情视图")
public class OrderDetailVO {

    @Schema(description = "订单 id")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "买家用户 id")
    private Long userId;

    @Schema(description = "业绩归属门店 id")
    private Long storeId;

    @Schema(description = "状态码（10-95）")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "总部发货状态（1-4）")
    private Integer warehouseStatus;

    @Schema(description = "商品总额")
    private BigDecimal totalAmount;

    @Schema(description = "优惠券抵扣")
    private BigDecimal couponAmount;

    @Schema(description = "积分抵扣金额")
    private BigDecimal pointsDeductAmount;

    @Schema(description = "本单发放积分")
    private Long pointsEarned;

    @Schema(description = "实付金额")
    private BigDecimal payAmount;

    @Schema(description = "收货人（快照）")
    private String receiverName;

    @Schema(description = "收货电话（快照）")
    private String receiverPhone;

    @Schema(description = "收货地址（快照）")
    private String receiverAddress;

    @Schema(description = "买家备注")
    private String remark;

    @Schema(description = "物流单号")
    private String trackingNo;

    @Schema(description = "快递公司")
    private String carrier;

    @Schema(description = "支付时间")
    private LocalDateTime paidAt;

    @Schema(description = "发货时间")
    private LocalDateTime shippedAt;

    @Schema(description = "完成时间")
    private LocalDateTime finishedAt;

    @Schema(description = "关单时间")
    private LocalDateTime expireAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "支付单号")
    private String payNo;

    @Schema(description = "支付状态（0待支付/1成功/2失败/3已退款）")
    private Integer payStatus;

    @Schema(description = "明细列表")
    private List<OrderItemVO> items;
}
