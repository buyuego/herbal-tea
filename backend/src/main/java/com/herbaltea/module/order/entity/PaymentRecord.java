package com.herbaltea.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * payment_records 表实体（支付单，对齐 V1__schema.sql 权威结构）
 *
 * <p>下单时创建（status=0 待支付），微信支付回调成功后置 1（幂等：同 pay_no 重复回调直接返回）。
 * callback_raw 为验签后存档的回调原文 JSON 字符串。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_records")
public class PaymentRecord extends BaseEntity {

    /** 待支付 */
    public static final int STATUS_PENDING = 0;
    /** 成功 */
    public static final int STATUS_SUCCESS = 1;
    /** 失败 */
    public static final int STATUS_FAILED = 2;
    /** 已退款 */
    public static final int STATUS_REFUNDED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支付单号（uk 唯一） */
    private String payNo;

    /** 订单（orders.id） */
    private Long orderId;

    /** 微信支付单号（回调后回填） */
    private String transactionId;

    /** 支付金额 */
    private BigDecimal amount;

    /** 0待支付 / 1成功 / 2失败 / 3已退款 */
    private Integer status;

    /** 回调原文 JSON（验签后存档） */
    private String callbackRaw;

    /** 幂等键（pay_callback） */
    private String idempotentKey;

    /** 支付时间 */
    private LocalDateTime paidAt;
}
