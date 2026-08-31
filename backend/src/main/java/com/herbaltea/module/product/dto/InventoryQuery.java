package com.herbaltea.module.product.dto;

import lombok.Data;

/**
 * 库存总览分页查询（v25，总部总仓维度）
 *
 * <p>筛选：关键词（商品名 / SKU 编码）、分类、SKU 状态、仅看低库存预警。
 */
@Data
public class InventoryQuery {

    /** 关键词（商品名 / SKU 编码模糊匹配） */
    private String keyword;

    /** 分类（null = 全部） */
    private Long categoryId;

    /** SKU 状态（0停用 / 1启用，null = 全部） */
    private Integer status;

    /** 1 = 仅看低库存预警（stock <= alert_stock） */
    private Integer lowStockOnly;

    private long page = 1;

    private long size = 10;
}
