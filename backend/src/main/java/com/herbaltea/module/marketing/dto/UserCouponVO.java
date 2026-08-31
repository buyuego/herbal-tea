package com.herbaltea.module.marketing.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户持券视图（v28：会员可用券 / 券领取记录共用）
 */
@Data
public class UserCouponVO {

    private Long id;

    private Long userId;

    private Long couponId;

    private String couponName;

    /** 1满减券 / 2折扣券 */
    private Integer type;

    private String typeDesc;

    /** 1平台券 / 2本店券 */
    private Integer scope;

    private String scopeDesc;

    private Long storeId;

    private String storeName;

    private BigDecimal thresholdAmount;

    private BigDecimal discountAmount;

    private String rules;

    /** 0未使用 / 1已使用 / 2已过期 / 3退款退回 */
    private Integer status;

    private String statusDesc;

    private Long orderId;

    private String orderNo;

    private LocalDateTime receivedAt;

    private LocalDateTime usedAt;

    private LocalDateTime expireAt;
}
