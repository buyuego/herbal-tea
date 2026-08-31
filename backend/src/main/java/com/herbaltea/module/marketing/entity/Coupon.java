package com.herbaltea.module.marketing.entity;

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
 * coupons 表实体（优惠券模板，对齐 V1__schema.sql 权威结构）
 *
 * <p>券类型：1 满减券（discount_amount 直接减）/ 2 折扣券（rules.discountRate 折扣率，可选 rules.maxDiscount 封顶）。
 * <p>归属：scope=1 平台券（平台承担成本，store_id 为 NULL，全部门店可用）/
 * scope=2 本店券（店铺承担成本，store_id 指定门店，仅该店可用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coupons")
public class Coupon extends BaseEntity {

    /** 满减券 */
    public static final int TYPE_CASH = 1;
    /** 折扣券 */
    public static final int TYPE_DISCOUNT = 2;

    /** 平台券（平台承担） */
    public static final int SCOPE_PLATFORM = 1;
    /** 本店券（店铺承担） */
    public static final int SCOPE_STORE = 2;

    /** 未发布 */
    public static final int STATUS_DRAFT = 0;
    /** 发放中 */
    public static final int STATUS_PUBLISHED = 1;
    /** 已停止 */
    public static final int STATUS_STOPPED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 1满减券 / 2折扣券 */
    private Integer type;

    /** 1平台券 / 2本店券 */
    private Integer scope;

    /** 本店券归属门店（平台券为 NULL） */
    private Long storeId;

    /** 使用门槛（满 X 元） */
    private BigDecimal thresholdAmount;

    /** 优惠金额（满减券） */
    private BigDecimal discountAmount;

    /** 扩展规则 JSON：折扣券 {discountRate, maxDiscount} */
    private String rules;

    /** 发行总量 */
    private Integer totalCount;

    /** 已领取（原子递增） */
    private Integer receivedCount;

    /** 每人限领 */
    private Integer perUserLimit;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 0未发布 / 1发放中 / 2已停止 */
    private Integer status;

    @Version
    private Integer version;
}
