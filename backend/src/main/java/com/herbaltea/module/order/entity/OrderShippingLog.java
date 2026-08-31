package com.herbaltea.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseCreatedOnlyEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * order_shipping_logs 表实体（物流轨迹日志，对齐 V1__schema.sql 权威结构）
 *
 * <p>纯流水：发货/中转/签收等轨迹描述，随订单状态推进追加。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_shipping_logs")
public class OrderShippingLog extends BaseCreatedOnlyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单（orders.id） */
    private Long orderId;

    /** 物流轨迹描述（如"已发货"） */
    private String status;

    /** 物流单号 */
    private String trackingNo;

    /** 快递公司 */
    private String carrier;

    /** 操作人（admin_users.id） */
    private Long operatorId;

    /** 备注 */
    private String note;
}
