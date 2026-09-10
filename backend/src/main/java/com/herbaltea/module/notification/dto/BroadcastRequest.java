package com.herbaltea.module.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员广播请求（notification:manage 权限）
 */
@Data
@Schema(description = "通知广播请求")
public class BroadcastRequest {

    @Schema(description = "目标角色（1超管/2财务/3仓管/4店长/5店员/6加盟店主），0=全员")
    private Integer targetRole = 0;

    @Schema(description = "目标门店id（NULL=不限门店；仅 targetRole=4/5 时生效）")
    private Long targetStoreId;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "标题")
    private String title;

    @NotBlank
    @Size(max = 512)
    @Schema(description = "正文")
    private String content;

    @Schema(description = "跳转链接（可选）")
    private String link;

    @Schema(description = "业务类型：system/announcement")
    private String bizType = "announcement";
}