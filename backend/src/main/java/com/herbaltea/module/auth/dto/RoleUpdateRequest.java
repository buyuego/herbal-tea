package com.herbaltea.module.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新角色请求（v10）
 *
 * <p>约束：code 创建后不可改（不在此入参）；预设角色（is_preset=1）的
 * data_scope / level 不可变（防破坏角色基线），仅可改 name / description；
 * 自定义角色全字段可改。
 */
public record RoleUpdateRequest(

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
