package com.herbaltea.module.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 角色授权请求（v10，全量覆盖）
 *
 * <p>语义：以 permissionIds 为最终态全量覆盖该角色授权（删旧插新）。
 * 空列表 = 清空授权（收回全部权限，属合法操作）。
 * 约束：权限 id 必须存在；is_sensitive=1 敏感权限仅 SUPER_ADMIN 角色（id=1）可授予。
 */
public record RoleAuthRequest(

        @NotNull(message = "权限列表不能为空")
        @Size(max = 200, message = "单次授权权限数上限 200")
        List<Long> permissionIds
) {
}
