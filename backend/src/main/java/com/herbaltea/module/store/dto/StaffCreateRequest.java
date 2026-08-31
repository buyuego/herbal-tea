package com.herbaltea.module.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建员工请求（门店管理员操作，store:staff:manage）
 *
 * <p>安全设计：不接收 roleId —— 门店只能创建「员工」角色（STORE_STAFF）账号，
 * 防止店主借员工管理接口自建同级管理员或篡改角色（提权面收敛）。
 */
public record StaffCreateRequest(

        /** 登录名（全局唯一；创建后不可改；以字母开头，3-32 位字母/数字/下划线） */
        @NotBlank(message = "登录名不能为空")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,31}$",
                message = "登录名须以字母开头，3-32 位字母/数字/下划线")
        String username,

        /** 姓名 */
        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名最长 64 字")
        String realName,

        /** 手机号（可选，短信验证用） */
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
        String phone,

        /** 初始密码（门店管理员线下告知员工；BCrypt 存储，任何日志不得输出明文） */
        @NotBlank(message = "初始密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度须为 6-32 位")
        String password
) {
}
