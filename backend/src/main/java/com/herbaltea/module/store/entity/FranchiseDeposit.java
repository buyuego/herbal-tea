package com.herbaltea.module.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseCreatedOnlyEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * franchise_deposits 表实体（加盟保证金流水，对齐 V1__schema.sql 权威结构）
 *
 * <p>type=1 缴纳 / 2 退还；status=0 待处理 / 1 完成（线下打款/退款后由财务确认）。
 * 审批通过时写入一笔「缴纳-待处理」流水，biz_no 关联申请单号 FR-{applicationId}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("franchise_deposits")
public class FranchiseDeposit extends BaseCreatedOnlyEntity {

    /** 类型：缴纳 */
    public static final int TYPE_PAY = 1;
    /** 类型：退还 */
    public static final int TYPE_REFUND = 2;

    /** 状态：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态：完成 */
    public static final int STATUS_DONE = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 门店（stores.id） */
    private Long storeId;

    /** 1缴纳 / 2退还 */
    private Integer type;

    /** 金额 */
    private BigDecimal amount;

    /** 0待处理 / 1完成 */
    private Integer status;

    /** 关联单号（如 FR-{申请单号}） */
    private String bizNo;

    /** 缴纳时间 */
    private LocalDateTime paidAt;

    /** 退还时间 */
    private LocalDateTime refundedAt;
}
