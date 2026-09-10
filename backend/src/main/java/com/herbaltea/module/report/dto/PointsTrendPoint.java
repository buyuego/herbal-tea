package com.herbaltea.module.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "积分趋势点（v31）：同日期双 series（granted 发放 / used 使用）。")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointsTrendPoint {
    /** 日期 yyyy-MM-dd */
    private String date;
    /** 当日发放积分 */
    private Long granted;
    /** 当日使用积分 */
    private Long used;
}