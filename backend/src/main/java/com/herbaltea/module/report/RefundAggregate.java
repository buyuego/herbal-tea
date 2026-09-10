package com.herbaltea.module.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退款率聚合（已退/已付）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundAggregate {
    private Long refundCount;
    private Long paidCount;
}