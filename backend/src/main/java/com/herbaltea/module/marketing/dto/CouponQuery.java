package com.herbaltea.module.marketing.dto;

import lombok.Data;

/**
 * 券模板分页查询（v28）
 */
@Data
public class CouponQuery {

    /** 券名模糊 */
    private String keyword;

    /** 券类型：1满减 / 2折扣 */
    private Integer type;

    /** 归属：1平台券 / 2本店券 */
    private Integer scope;

    /** 归属门店（本店券） */
    private Long storeId;

    /** 状态：0未发布 / 1发放中 / 2已停止 */
    private Integer status;

    private long page = 1;

    private long size = 10;
}
