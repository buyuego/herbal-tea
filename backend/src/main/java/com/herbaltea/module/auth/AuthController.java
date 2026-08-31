package com.herbaltea.module.auth;

import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.idempotency.Idempotent;
import com.herbaltea.infrastructure.web.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权接口（登录接口在鉴权白名单 /api/auth/** 内，拦截器链自动放行）
 *
 * <p>OpenAPI 契约：63 API 中 auth 组 3 个端点（admin/login、refresh、logout）。
 */
@Tag(name = "认证", description = "B 端管理员登录 / 刷新令牌轮换 / 登出")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录请求（输入边界校验：用户名/密码必填） */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    /** 刷新请求（刷新令牌走请求体，不经 header，避免与访问令牌混淆，D12） */
    public record RefreshRequest(
            @NotBlank(message = "刷新令牌不能为空") String refreshToken) {
    }

    @Operation(summary = "B 端管理员登录", description = "账号+密码登录；失败 5 次锁定 15 分钟；成功返回双令牌（访问 2h + 刷新 30d 轮换）")
    @PostMapping("/admin/login")
    public Result<AuthServiceImpl.TokenPair> adminLogin(@Valid @RequestBody LoginRequest body) {
        return Result.ok(authService.adminLogin(body.username(), body.password()));
    }

    @Operation(summary = "刷新令牌轮换", description = "旧刷新令牌作废并签发新双令牌（D12）；已轮换/登出/吊销返回 40100")
    @PostMapping("/refresh")
    public Result<AuthServiceImpl.TokenPair> refresh(@Valid @RequestBody RefreshRequest body) {
        return Result.ok(authService.refresh(body.refreshToken()));
    }

    @Operation(summary = "登出 / 设备吊销", description = "token_version +1 使全部已签发 JWT 秒级失效，并吊销本管理员全部设备信任（R9/D13）")
    @PostMapping("/logout")
    @AuditLog(action = "管理员登出")
    public Result<Void> logout() {
        authService.revoke(UserContext.get().getAdminId());
        return Result.ok();
    }

    /**
     * 幂等写接口示例：重复提交返回 40901（@Idempotent 切面读取 Idempotency-Key 头）
     * 实际使用：@PostMapping("/xx") @Idempotent @AuditLog(action = "xx") public Result<Void> xx()
     */
    @Operation(summary = "幂等写接口示例（开发参考）", hidden = true)
    @PostMapping("/_idempotent-demo")
    @Idempotent
    public Result<Void> idempotentDemo() {
        return Result.ok();
    }
}
