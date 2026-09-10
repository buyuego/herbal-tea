package com.herbaltea.module.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单期销售额聚合（用于概览环比对比）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverviewAggregateRow {
    private BigDecimal salesAmount;
    private Long orderCount;
}