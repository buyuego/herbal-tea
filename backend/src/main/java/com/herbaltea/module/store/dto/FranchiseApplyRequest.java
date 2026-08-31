package com.herbaltea.module.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 加盟申请请求（C 端登录用户提交）
 *
 * @param applicantName  申请人姓名
 * @param phone          联系电话
 * @param intendedRegion 意向区域（选填）
 * @param experience     从业经历（选填）
 */
public record FranchiseApplyRequest(
        @NotBlank(message = "申请人姓名不能为空")
        @Size(max = 64, message = "申请人姓名过长") String applicantName,

        @NotBlank(message = "联系电话不能为空")
        @Pattern(regexp = "^1\\d{10}$", message = "联系电话格式不正确") String phone,

        @Size(max = 128, message = "意向区域过长") String intendedRegion,

        @Size(max = 512, message = "从业经历过长") String experience) {
}
