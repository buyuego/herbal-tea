package com.herbaltea.module.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 品类销售占比项（v31）：ECharts 饼图直接消费 [name, value]。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryShareItem {
    /** 品类名 */
    private String name;
    /** 销售额（元） */
    private BigDecimal value;
    /** 占比 %（保留 1 位小数） */
    private BigDecimal percent;
}