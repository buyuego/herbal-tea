package com.herbaltea.module.marketing.dto;

import lombok.Data;

/**
 * 促销活动分页查询（v30）
 */
@Data
public class PromotionQuery {

    /** 活动名模糊 */
    private String keyword;

    /** 活动类型：1满减 / 2折扣 / 3限时购 */
    private Integer type;

    /** 归属：1平台活动 / 2本店活动 */
    private Integer scope;

    /** 归属门店（本店活动） */
    private Long storeId;

    /** 状态：0草稿 / 1进行中 / 2已结束 */
    private Integer status;

    private long page = 1;

    private long size = 10;
}
