package com.herbaltea.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 收货地址新增/修改请求
 */
public record AddressRequest(
        @Schema(description = "收货人", example = "张三")
        @NotBlank(message = "收货人不能为空")
        @Size(max = 64)
        String receiverName,
        @Schema(description = "联系电话", example = "13800138000")
        @NotBlank(message = "联系电话不能为空")
        @Size(max = 32)
        String phone,
        @Schema(description = "省", example = "广东省")
        @NotBlank(message = "省不能为空")
        @Size(max = 32)
        String province,
        @Schema(description = "市", example = "深圳市")
        @NotBlank(message = "市不能为空")
        @Size(max = 32)
        String city,
        @Schema(description = "区", example = "南山区")
        @NotBlank(message = "区不能为空")
        @Size(max = 32)
        String district,
        @Schema(description = "详细地址", example = "科技园路 1 号")
        @NotBlank(message = "详细地址不能为空")
        @Size(max = 255)
        String detail,
        @Schema(description = "是否默认地址（1 是 / 0 否）", example = "1")
        Integer isDefault) {
}
