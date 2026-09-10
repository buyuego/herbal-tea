package com.herbaltea.module.product.dto;

import lombok.Data;

/**
 * C 端货架商品查询（v29：小程序首页 / 分类浏览）
 */
@Data
public class ShelfQuery {

    /** 门店（必填：C 端始终从具体门店购买） */
    private Long storeId;

    /** 分类（null = 全部） */
    private Long categoryId;

    /** 关键词（商品名 / SKU 编码模糊） */
    private String keyword;

    private long page = 1;

    private long size = 10;
}
