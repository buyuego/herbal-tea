package com.herbaltea.module.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重置员工密码请求（重置后旧令牌全部失效，需重新登录）
 */
public record StaffPasswordRequest(

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度须为 6-32 位")
        String newPassword
) {
}
