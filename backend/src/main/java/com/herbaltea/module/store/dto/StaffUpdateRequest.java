package com.herbaltea.module.store.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新员工请求（姓名/手机号/启用禁用；username 与角色不可改）
 */
public record StaffUpdateRequest(

        /** 姓名 */
        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名最长 64 字")
        String realName,

        /** 手机号（可选） */
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
        String phone,

        /** 0禁用 / 1正常（禁用时 token_version +1，旧令牌即时失效） */
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态取值 0/1")
        @Max(value = 1, message = "状态取值 0/1")
        Integer status
) {
}
