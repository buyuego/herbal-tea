package com.herbaltea.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 微信小程序登录请求
 *
 * @param code              wx.login() 返回的临时 code（dev mock：确定性映射为 openid）
 * @param deviceFingerprint 五维设备指纹哈希（UA+Canvas+WebGL+字体+屏幕，A5；可空，空则仅登记 IP）
 * @param nickname          首次自动注册时的昵称（可空）
 * @param avatarUrl         首次自动注册时的头像（可空）
 */
public record WxLoginRequest(
        @Schema(description = "wx.login 临时 code", example = "mock-code-001")
        @NotBlank(message = "code 不能为空")
        String code,
        @Schema(description = "五维设备指纹哈希（可空）")
        String deviceFingerprint,
        @Schema(description = "昵称（首次注册）")
        String nickname,
        @Schema(description = "头像 URL（首次注册）")
        String avatarUrl) {
}
