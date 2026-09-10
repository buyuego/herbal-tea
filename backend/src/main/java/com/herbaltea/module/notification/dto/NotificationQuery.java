package com.herbaltea.module.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通知列表查询条件（v32）
 */
@Data
@Schema(description = "通知查询条件")
public class NotificationQuery {

    @Schema(description = "页码（从 1 开始）")
    private Integer page = 1;

    @Schema(description = "每页条数")
    private Integer size = 20;

    @Schema(description = "是否仅未读")
    private Boolean unreadOnly;

    @Schema(description = "业务类型过滤：order/refund/settlement/inventory/member")
    private String bizType;
}