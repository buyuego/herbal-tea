package com.herbaltea.module.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * D14 目录变更复核——驳回请求（确认无需 body）。
 *
 * @param note 驳回原因（必填，最长 255 字；总部据此了解店铺不采纳新目录的理由）
 */
public record CatalogReviewRequest(
        @NotBlank(message = "驳回原因不能为空")
        @Size(max = 255, message = "驳回原因最长 255 字")
        String note
) {
}
