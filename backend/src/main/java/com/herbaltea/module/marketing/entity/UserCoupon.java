package com.herbaltea.module.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * user_coupons 表实体（用户持券，对齐 V1__schema.sql 权威结构）
 *
 * <p>状态：0未使用 / 1已使用 / 2已过期 / 3退款退回。
 * 核销为单条原子 UPDATE（{@code status = 0} 条件），零重复核销。
 * <p>注意：本表 DDL **没有 created_at**（只有 received_at / used_at / expire_at），
 * 故不继承任何基类，不做 createdAt 映射（与 settlement_items 同款坑）。
 */
@Data
@TableName("user_coupons")
public class UserCoupon {

    /** 未使用 */
    public static final int STATUS_UNUSED = 0;
    /** 已使用 */
    public static final int STATUS_USED = 1;
    /** 已过期 */
    public static final int STATUS_EXPIRED = 2;
    /** 退款退回 */
    public static final int STATUS_REFUNDED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long couponId;

    /** 券归属门店（平台券为 NULL） */
    private Long storeId;

    /** 0未使用 / 1已使用 / 2已过期 / 3退款退回 */
    private Integer status;

    /** 核销订单 */
    private Long orderId;

    private LocalDateTime receivedAt;

    private LocalDateTime usedAt;

    private LocalDateTime expireAt;
}
