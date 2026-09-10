package com.herbaltea.module.marketing.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 促销活动视图（v30）
 */
@Data
public class PromotionVO {

    private Long id;

    private String title;

    /** 1满减 / 2折扣 / 3限时购 */
    private Integer type;

    private String typeDesc;

    /** 1平台活动 / 2本店活动 */
    private Integer scope;

    private String scopeDesc;

    private Long storeId;

    private String storeName;

    /** 规则 JSON 原文 */
    private String rules;

    /** 规则中文摘要（如「满 ¥100 减 ¥15」「满 ¥50 打 88 折（最高减 ¥30）」） */
    private String ruleDesc;

    /** 门槛金额（解析自 rules，便于前端展示） */
    private BigDecimal thresholdAmount;

    /** 优惠金额（满减/限时购） */
    private BigDecimal discountAmount;

    /** 折扣率（折扣活动） */
    private BigDecimal discountRate;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 0草稿 / 1进行中 / 2已结束 */
    private Integer status;

    private String statusDesc;

    /** 当前是否处于可命中窗口（status=进行中 且 时间窗口命中） */
    private Boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
