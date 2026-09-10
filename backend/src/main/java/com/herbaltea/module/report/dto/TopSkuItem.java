package com.herbaltea.module.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * TOP SKU 销量榜（v31）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopSkuItem {
    private Long skuId;
    private String skuName;
    private Long productId;
    private String productName;
    private Long qty;
    private BigDecimal amount;
}