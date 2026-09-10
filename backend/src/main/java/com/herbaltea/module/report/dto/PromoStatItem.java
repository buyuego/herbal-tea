package com.herbaltea.module.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 活动命中统计（v31）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromoStatItem {
    private Long promotionId;
    private String name;
    /** 1平台 / 2本店 */
    private Integer scope;
    private Long orderCount;
    /** 该活动带来的优惠总额（status ≥ 20 的订单） */
    private java.math.BigDecimal discountTotal;
}