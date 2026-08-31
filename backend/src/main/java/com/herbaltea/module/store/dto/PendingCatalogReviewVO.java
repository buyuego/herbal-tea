package com.herbaltea.module.store.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * D14 目录变更复核 VO（store_products catalog_dirty=1 联查 products / product_skus）
 *
 * <p>店铺端角标提示"目录已更新"，展示商品/SKU 与旧定价，供复核确认。
 * 用 {@code @Data} 而非 record：MyBatis 结果映射需无参构造 + setter。
 */
@Data
public class PendingCatalogReviewVO {

    /** store_products.id */
    private Long storeProductId;
    private Long storeId;
    private Long productId;
    private Long skuId;

    /** 本店售价（目录变更前的定价，复核时可对比） */
    private BigDecimal storePrice;

    /** 1=目录已更新待复核 */
    private Integer catalogDirty;

    private LocalDateTime updatedAt;

    /** 联查：商品名 */
    private String productName;
    /** 联查：SKU 编码 */
    private String skuCode;
    /** 联查：规格矩阵 JSON */
    private String skuSpecs;
}
