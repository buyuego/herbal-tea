package com.herbaltea.module.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 数据看板概览卡（v31）。
 *
 * <p>单值字段直接展示；趋势字段给出环比（与上 period 对比，0% 时不渲染）。
 */
@Data
@Schema(description = "概览卡：销售额/订单数/客单价/会员数")
public class OverviewVO {

    @Schema(description = "当前区间销售额（元，status ∈ {20,30,40,50,90} 已支付及之后订单）")
    private BigDecimal salesAmount;

    @Schema(description = "当前区间已支付订单数")
    private Long orderCount;

    @Schema(description = "客单价 = salesAmount / orderCount（保留 2 位小数）")
    private BigDecimal avgOrderAmount;

    @Schema(description = "会员下单数（去重 user_id）")
    private Long memberCount;

    @Schema(description = "当前区间标签：今日 / 昨日 / 近 7 天 / 近 30 天")
    private String periodLabel;

    @Schema(description = "销售额环比（正数增长，负数下降，null=无对比）")
    private BigDecimal salesAmountChange;
}