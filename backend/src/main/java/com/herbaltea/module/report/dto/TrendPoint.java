package com.herbaltea.module.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 趋势点（v31）：ECharts 折线图直接消费 [date, value] 二元组。
 *
 * <p>同点存多条 record（grant/use 双柱）时用 {@link PointsTrendPoint#grant}/{@link PointsTrendPoint#use}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendPoint {
    /** 日期 yyyy-MM-dd */
    private String date;
    /** 数值（销售额 / 积分发放 / 积分使用 等） */
    private BigDecimal value;
}