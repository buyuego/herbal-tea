package com.herbaltea.module.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单状态漏斗项（v31）：ECharts 漏斗图按 status 流转展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FunnelItem {
    /** 订单状态码（10/20/30/40/50/60/70/80/90） */
    private Integer status;
    /** 状态文案 */
    private String label;
    /** 订单数 */
    private Long count;
}