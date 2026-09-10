package com.herbaltea.module.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 概览聚合行（v31）。单类标 public，供 MyBatis mapper 代理跨包访问。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverviewAggregate {
    private BigDecimal salesAmount;
    private Long orderCount;
    private Long memberCount;
}