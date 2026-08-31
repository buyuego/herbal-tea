package com.herbaltea.module.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * return_orders 表实体（退货单，签收后退货链路，对齐 V1__schema.sql 权威结构）
 *
 * <p>一张退款单只建一张退货单（uk_ro_refund 唯一索引兜底，D1）。
 *
 * <p>退货单状态（status）：0申请中 / 1待寄回 / 2在途 / 3待验货 / 4已完结 / 5已取消。
 * <p>总部收货状态（warehouse_status）：1待收货 / 2已收货 / 3验货通过 / 4验货不通过。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("return_orders")
public class ReturnOrder extends BaseEntity {

    // ===== 退货单状态 =====
    public static final int STATUS_APPLYING = 0;
    public static final int STATUS_TO_RETURN = 1;
    public static final int STATUS_IN_TRANSIT = 2;
    public static final int STATUS_TO_INSPECT = 3;
    public static final int STATUS_DONE = 4;
    public static final int STATUS_CANCELED = 5;

    // ===== 总部收货状态 =====
    public static final int WH_TO_RECEIVE = 1;
    public static final int WH_RECEIVED = 2;
    public static final int WH_PASSED = 3;
    public static final int WH_FAILED = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联退款单（refund_records.id，uk 唯一） */
    private Long refundId;

    /** 关联订单（orders.id） */
    private Long orderId;

    /** 归属门店（stores.id，Data Scope 隔离） */
    private Long storeId;

    /** 退款分支：1未发货/2在途拦截/3已签收退货 */
    private Integer branch;

    /** 总部退货寄回地址（系统给出） */
    private String returnAddress;

    /** 用户填写退货物流单号 */
    private String returnTrackingNo;

    /** 退货快递公司 */
    private String returnCarrier;

    /** 总部收货状态：1待收货/2已收货/3验货通过/4验货不通过 */
    private Integer warehouseStatus;

    /** 总部收货人（admin_users.id） */
    private Long receivedBy;

    /** 总部收货时间 */
    private LocalDateTime receivedAt;

    /** 验货仓管（admin_users.id） */
    private Long inspectedBy;

    /** 验货结论：完好退全款/破损部分退款/非质量问题拒退 */
    private String inspectionResult;

    /** 退货单状态：0申请中/1待寄回/2在途/3待验货/4已完结/5已取消 */
    private Integer status;
}
