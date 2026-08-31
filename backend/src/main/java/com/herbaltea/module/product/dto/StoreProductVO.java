package com.herbaltea.module.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 本店上架列表 VO（store_products 联查 products / product_skus）
 *
 * <p>用 {@code @Data} 而非 record：MyBatis 结果映射需无参构造 + setter。
 */
@Data
public class StoreProductVO {

    private Long id;
    private Long storeId;
    private Long productId;
    private Long skuId;
    private BigDecimal price;
    private Integer status;
    private Integer catalogDirty;
    private Integer dailyQuota;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 联查：商品名 */
    private String productName;
    /** 联查：主图 */
    private String mainImage;
    /** 联查：平台建议价（前端展示"建议区间"用） */
    private BigDecimal suggestedPrice;
    /** 联查：SKU 编码 */
    private String skuCode;
    /** 联查：规格矩阵 JSON */
    private String specs;
    /** 联查：总仓库存 */
    private Integer stock;
}
