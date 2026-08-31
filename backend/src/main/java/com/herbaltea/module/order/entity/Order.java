package com.herbaltea.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * orders 表实体（订单，对齐 V1__schema.sql 权威结构）
 *
 * <p>状态机（{@link com.herbaltea.module.order.OrderStatus}）：
 * 10待支付 → 20已支付 → 30待发货 → 40已发货 → 50已签收 / 60退款中 / 70已关闭 /
 * 80已退款 / 90已完结 / 95回退失败-待人工。
 * 状态流转一律 {@code CAS(status, version)} 双条件更新（16.2 / 16.11）。
 *
 * <p>warehouse_status（总部发货状态）：1待接单 / 2待发货 / 3已发货 / 4已签收。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("orders")
public class Order extends BaseEntity {

    // ===== 状态机常量（与 OrderStatus 对齐） =====
    public static final int STATUS_PENDING_PAYMENT = 10;
    public static final int STATUS_PAID = 20;
    public static final int STATUS_PENDING_SHIPMENT = 30;
    public static final int STATUS_SHIPPED = 40;
    public static final int STATUS_SIGNED = 50;
    public static final int STATUS_REFUNDING = 60;
    public static final int STATUS_CLOSED = 70;
    public static final int STATUS_REFUNDED = 80;
    public static final int STATUS_COMPLETED = 90;

    /** 总部发货状态：待接单 */
    public static final int WH_READY = 1;
    /** 总部发货状态：待发货 */
    public static final int WH_TO_SHIP = 2;
    /** 总部发货状态：已发货 */
    public static final int WH_SHIPPED = 3;
    /** 总部发货状态：已签收 */
    public static final int WH_SIGNED = 4;

    /** 自动关单标记：未触发 */
    public static final int AUTO_CLOSE_NONE = 0;
    /** 自动关单标记：已自动关单 */
    public static final int AUTO_CLOSE_TIMEOUT = 1;
    /** 自动关单标记：用户已取消 */
    public static final int AUTO_CLOSE_USER = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号（uk 唯一） */
    private String orderNo;

    /** 买家（users.id） */
    private Long userId;

    /** 业绩归属门店（stores.id，下单时选择） */
    private Long storeId;

    /** 状态机（10-95，见 OrderStatus） */
    private Integer status;

    /** 总部发货状态：1待接单/2待发货/3已发货/4已签收 */
    private Integer warehouseStatus;

    /** 商品总额 */
    private BigDecimal totalAmount;

    /** 优惠券抵扣 */
    private BigDecimal couponAmount;

    /** 券归属：0无券 / 1平台券（平台承担）/ 2本店券（店铺承担）（v28，V14 补列） */
    private Integer couponScope;

    /** 积分抵扣数量 */
    private Long pointsDeduct;

    /** 积分抵扣金额 */
    private BigDecimal pointsDeductAmount;

    /** 本单发放积分 */
    private Long pointsEarned;

    /** 积分来源：1门店营销 / 2平台活动 */
    private Integer pointsSource;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 平台佣金比例（下单时快照，如 0.0500 = 5%） */
    private BigDecimal commissionRate;

    /** 收货人（快照） */
    private String receiverName;

    /** 收货电话（快照） */
    private String receiverPhone;

    /** 收货地址（快照） */
    private String receiverAddress;

    /** 买家备注 */
    private String remark;

    /** 物流单号 */
    private String trackingNo;

    /** 快递公司 */
    private String carrier;

    /** 发货仓管（admin_users.id） */
    private Long shippedBy;

    /** 发货时间 */
    private LocalDateTime shippedAt;

    /** 支付时间 */
    private LocalDateTime paidAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    /** 退款审批人 */
    private Long refundApprovedBy;

    /** 退款审批时间 */
    private LocalDateTime refundApprovedAt;

    /** 未支付自动关单时间（下单 + 30min，定时任务扫描） */
    private LocalDateTime expireAt;

    /** 0未触发 / 1已自动关单 / 2用户已取消 */
    private Integer autoCloseStatus;

    /** 催发货次数 */
    private Integer urgeCount;

    /** 最近催发货时间 */
    private LocalDateTime urgedAt;

    /** 总部 48h 超时预警已触发（防重复告警） */
    private Integer shipTimeoutWarned;

    /** 发货 15 天无签收自动完成时间 */
    private LocalDateTime autoSignedAt;

    /** 乐观锁（状态流转 CAS，16.11） */
    @Version
    private Integer version;
}
