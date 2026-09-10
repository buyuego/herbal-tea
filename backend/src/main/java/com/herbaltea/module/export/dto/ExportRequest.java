package com.herbaltea.module.export.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建导出任务请求（v33）
 *
 * <p>bizType 与 filter 字段名差异化（避免同名字段冲突）：
 * <ul>
 *   <li>product：name/categoryId/online</li>
 *   <li>order：orderNo/status/storeId/dateFrom/dateTo</li>
 *   <li>member：phone/nickname/status</li>
 *   <li>settlement：settleNo/storeId/status/period/dateFrom/dateTo</li>
 * </ul>
 */
@Data
@Schema(description = "创建导出请求")
public class ExportRequest {

    @Schema(description = "业务类型：product/order/member/settlement", example = "order")
    private String bizType;

    @Schema(description = "可选过滤（按模块差异）")
    private Filter filter;

    @Data
    public static class Filter {
        private String name;
        private Long categoryId;
        private Boolean online;
        private String orderNo;
        private Integer status;
        private Long storeId;
        private String dateFrom;
        private String dateTo;
        private String phone;
        private String nickname;
        private String settleNo;
        private String period;
    }
}
