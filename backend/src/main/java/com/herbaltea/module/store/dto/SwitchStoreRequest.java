package com.herbaltea.module.store.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 切换当前门店请求（MULTI_STORE：/api/store/switch-store）
 */
public record SwitchStoreRequest(
        @NotNull(message = "门店不能为空") Long storeId
) {
}
