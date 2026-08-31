package com.herbaltea.module.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存总览行（v25）：SKU × 商品 × 分类 联表视图
 *
 * <p>lowStock 由 SQL 计算（{@code stock <= alert_stock}），预警行优先排序。
 */
@Data
public class InventoryVO {

    /** SKU id */
    private Long skuId;

    /** 商品 id */
    private Long productId;

    /** 商品名 */
    private String productName;

    /** 分类 id */
    private Long categoryId;

    /** 分类名 */
    private String categoryName;

    /** SKU 编码 */
    private String skuCode;

    /** 规格 JSON 字符串 */
    private String specs;

    /** 当前库存 */
    private Integer stock;

    /** 低库存预警阈值 */
    private Integer alertStock;

    /** 是否低库存预警（stock <= alert_stock） */
    private Boolean lowStock;

    /** SKU 成本价（敏感，前端按 product:cost:view 控制显隐） */
    private BigDecimal costPrice;

    /** SKU 建议价 */
    private BigDecimal price;

    /** SKU 状态（0停用 / 1启用） */
    private Integer status;

    /** 最近更新（库存/阈值变动时间） */
    private LocalDateTime updatedAt;
}
