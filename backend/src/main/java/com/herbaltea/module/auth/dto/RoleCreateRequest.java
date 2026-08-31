package com.herbaltea.module.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建角色请求（v10）
 *
 * <p>约束：code 全局唯一（创建后不可改）、自定义角色上限 10、
 * data_scope 枚举 GLOBAL/MULTI_STORE/SINGLE_STORE、level 1 平台级 / 2 店铺级。
 */
public record RoleCreateRequest(

        @NotBlank(message = "角色编码不能为空")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$", message = "角色编码须为大写下划线字母数字（2-32位）")
        String code,

        @NotBlank(message = "角色名不能为空")
        @Size(max = 64, message = "角色名最长 64 字符")
        String name,

        @NotBlank(message = "数据范围不能为空")
        @Pattern(regexp = "GLOBAL|MULTI_STORE|SINGLE_STORE", message = "数据范围仅支持 GLOBAL/MULTI_STORE/SINGLE_STORE")
        String dataScope,

        @NotNull(message = "角色级别不能为空")
        @Min(value = 1, message = "级别仅支持 1 平台级 / 2 店铺级")
        @Max(value = 2, message = "级别仅支持 1 平台级 / 2 店铺级")
        Integer level,

        @Size(max = 255, message = "描述最长 255 字符")
        String description
) {
}
