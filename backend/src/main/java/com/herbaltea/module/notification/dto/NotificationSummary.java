package com.herbaltea.module.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 未读数 + 最近通知（顶部铃铛一次拉取）
 */
@Data
@AllArgsConstructor
@Schema(description = "铃铛摘要：未读数 + 最近 10 条")
public class NotificationSummary {

    @Schema(description = "未读总数")
    private Long unreadCount;

    @Schema(description = "最近通知列表（最多 10 条）")
    private java.util.List<NotificationVO> recent;
}