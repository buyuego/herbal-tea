package com.herbaltea.module.store.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 门店管理员绑定请求（总部操作）
 *
 * @param adminId 管理员 id（admin_users）
 * @param storeId 门店 id（stores）
 */
public record StoreAdminBindRequest(
        @NotNull(message = "管理员不能为空") Long adminId,
        @NotNull(message = "门店不能为空") Long storeId) {
}
