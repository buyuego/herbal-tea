package com.herbaltea.module.marketing.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 券核销结果（v28）
 */
@Data
public class CouponUseResult {

    /** 持券记录 id */
    private Long userCouponId;

    /** 券模板 id */
    private Long couponId;

    /** 优惠金额（已封顶，不超过订单金额） */
    private BigDecimal discountAmount;

    /** 券归属：1平台券 / 2本店券（决定结算成本归属） */
    private Integer scope;
}
