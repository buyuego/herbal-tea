package com.herbaltea.module.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色视图（v10 角色列表 / 详情）
 *
 * <p>聚合：permissionIds（授权页回显勾选态）、adminCount（绑定管理员数，删除前置判断）。
 */
public record RoleVO(
        Long id,
        String code,
        String name,
        String dataScope,
        Integer level,
        Integer isPreset,
        String description,
        List<Long> permissionIds,
        Long adminCount,
        LocalDateTime createdAt
) {
}
