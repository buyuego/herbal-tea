package com.herbaltea.module.auth;

import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.idempotency.Idempotent;
import com.herbaltea.infrastructure.web.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 鉴权接口（示例 Controller：演示统一响应 / 审计注解 / 幂等注解的标准用法）
 *
 * <p>登录接口在鉴权白名单内（/api/auth/**），拦截器链自动放行。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitInterceptor rateLimit;

    /** B 端管理员登录（登录失败 5 次锁定 15 分钟） */
    @PostMapping("/admin/login")
    public Result<Map<String, String>> adminLogin(@RequestBody Map<String, String> body) {
        String token = authService.adminLogin(body.get("username"), body.get("password"));
        return Result.ok(Map.of("accessToken", token, "tokenType", "Bearer"));
    }

    /** 刷新令牌轮换（D12） */
    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(@RequestHeader("Authorization") String bearer) {
        String token = authService.refresh(bearer.substring("Bearer ".length()));
        return Result.ok(Map.of("accessToken", token, "tokenType", "Bearer"));
    }

    /** 登出 / 设备级吊销（D13）：token_version + 1，全部会话失效 */
    @PostMapping("/logout")
    @AuditLog(action = "管理员登出")
    public Result<Void> logout() {
        authService.revoke(0L);
        return Result.ok();
    }

    /**
     * 幂等写接口示例：重复提交返回 40901（@Idempotent 切面读取 Idempotency-Key 头）
     * 实际使用：@PostMapping("/xx") @Idempotent @AuditLog(action = "xx") public Result<Void> xx()
     */
    @PostMapping("/_idempotent-demo")
    @Idempotent
    public Result<Void> idempotentDemo() {
        return Result.ok();
    }
}
