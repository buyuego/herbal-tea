package com.herbaltea.module.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图对象（B 端渲染用）
 */
@Data
@Schema(description = "通知 VO")
public class NotificationVO {

    @Schema(description = "通知id")
    private Long id;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "业务id")
    private String bizId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文")
    private String content;

    @Schema(description = "跳转链接")
    private String link;

    @Schema(description = "是否已读：0未读/1已读")
    private Integer isRead;

    @Schema(description = "已读时间")
    private LocalDateTime readAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "所属门店id")
    private Long storeId;
}