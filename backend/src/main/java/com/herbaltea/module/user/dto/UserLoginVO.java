package com.herbaltea.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录成功返回的令牌对（对齐 B 端 AuthServiceImpl.TokenPair 结构）
 *
 * @param accessToken  访问令牌（2h）
 * @param refreshToken 刷新令牌（30d 轮换）
 * @param tokenType    Bearer
 * @param expiresIn    访问令牌有效秒数
 * @param firstLogin   是否首次注册（前端可用于引导完善资料）
 */
public record UserLoginVO(
        @Schema(description = "访问令牌") String accessToken,
        @Schema(description = "刷新令牌") String refreshToken,
        @Schema(description = "令牌类型") String tokenType,
        @Schema(description = "有效期（秒）") int expiresIn,
        @Schema(description = "是否首次注册") boolean firstLogin) {
}
