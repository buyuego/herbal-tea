package com.herbaltea.module.marketing.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券模板视图（v28）
 */
@Data
public class CouponVO {

    private Long id;

    private String name;

    /** 1满减券 / 2折扣券 */
    private Integer type;

    private String typeDesc;

    /** 1平台券 / 2本店券 */
    private Integer scope;

    private String scopeDesc;

    private Long storeId;

    private String storeName;

    /** 使用门槛 */
    private BigDecimal thresholdAmount;

    /** 优惠金额（满减券） */
    private BigDecimal discountAmount;

    /** 扩展规则 JSON（折扣券：{discountRate, maxDiscount}） */
    private String rules;

    private Integer totalCount;

    private Integer receivedCount;

    /** 剩余可领（totalCount - receivedCount） */
    private Integer remainCount;

    private Integer perUserLimit;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 0未发布 / 1发放中 / 2已停止 */
    private Integer status;

    private String statusDesc;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
