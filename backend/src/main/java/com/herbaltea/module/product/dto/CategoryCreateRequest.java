package com.herbaltea.module.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建商品分类请求
 */
public record CategoryCreateRequest(
        @NotBlank(message = "分类名不能为空") String name,
        @NotNull(message = "排序不能为空") Integer sort) {
}
