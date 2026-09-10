package com.herbaltea.module.marketing.dto;

import java.math.BigDecimal;

/**
 * 活动命中的计价结果（v30，下单侧使用）
 *
 * @param promotionId    命中的活动 id
 * @param title          活动名（订单备注/日志用）
 * @param scope          活动归属：1平台活动 / 2本店活动（决定结算成本承担方）
 * @param discountAmount 实际优惠金额（已按订单金额封顶）
 */
public record PromotionMatch(
        Long promotionId,
        String title,
        Integer scope,
        BigDecimal discountAmount) {
}
