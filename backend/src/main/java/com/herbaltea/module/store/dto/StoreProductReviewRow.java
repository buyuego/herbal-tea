package com.herbaltea.module.store.dto;

import lombok.Data;

/**
 * store_products 复核行（StoreProductWriteMapper 行查询结果，仅复核状态流转所需列）。
 *
 * <p>用于 D14 复核确认/驳回前的状态判定（存在性 + 门店归属 + catalog_dirty/review_status）。
 */
@Data
public class StoreProductReviewRow {

    private Long id;
    private Long storeId;

    /** 1=目录已更新待复核（V1 既有字段） */
    private Integer catalogDirty;

    /** 0待复核 / 1已确认 / 2已驳回（V6 新增） */
    private Integer reviewStatus;
}
