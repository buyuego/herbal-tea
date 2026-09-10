package com.herbaltea.module.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * C 端货架商品（v29）：商品头 + 本店在售 SKU 列表
 *
 * <p>只暴露 C 端需要的字段：不含成本价（敏感）、不含上下架内部标记。
 */
@Data
public class ShelfProductVO {

    private Long productId;

    private String name;

    private String subtitle;

    private String mainImage;

    /** 平台建议价（本店价的展示锚点） */
    private BigDecimal suggestedPrice;

    private Long categoryId;

    /** 配方（详情页展示，列表接口不返回） */
    private String formula;

    /** 图集 JSON 数组（详情页轮播，列表接口不返回） */
    private String images;

    /** 详情富文本 HTML（详情页用 rich-text 渲染，列表接口不返回） */
    private String detail;

    /** 该店在售的 SKU（价格为本店定价） */
    private List<ShelfSkuVO> skus;
}
