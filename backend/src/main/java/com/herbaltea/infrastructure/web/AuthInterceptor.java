package com.herbaltea.infrastructure.web;

import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.infrastructure.security.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器（原独立网关下沉，设计文档 2.2 拦截器链第 1 道）
 *
 * <p>校验：Authorization: Bearer &lt;token&gt;
 * <ul>
 *   <li>JWT 签名/过期校验</li>
 *   <li>token_version 与数据库比对（R9 即时吊销）——由 tokenVersionValidator 提供</li>
 *   <li>白名单路径（登录/微信回调/健康检查）直接放行</li>
 * </ul>
 * 校验通过后填充 {@link UserContext}。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    /** token_version 比对器：按主体类型注册，实现方查 users.token_version / admin_users.token_version */
    public interface TokenVersionValidator {
        /** @return 当前有效 version，null 表示主体不存在（已注销） */
        Long currentVersion(String principalType, Long userId);
    }

    /**
     * 按主体类型分发的比对器（Auth 模块注册 ADMIN，User 模块注册 USER）。
     * 独立注册避免相互覆盖：C 端微信登录落地前 USER 无校验器，一律拒绝。
     */
    private final java.util.Map<String, TokenVersionValidator> versionValidators =
            new java.util.concurrent.ConcurrentHashMap<>();

    public void registerVersionValidator(String principalType, TokenVersionValidator validator) {
        this.versionValidators.put(principalType, validator);
    }

    /**
     * 无需登录的白名单（登录 / 刷新 / 微信支付回调 / 健康检查 / OpenAPI 文档）
     *
     * <p><b>注意</b>：
     * <ul>
     *   <li>此处为前缀字面匹配（{@code path.startsWith}），通配符 {@code **} 在 URL 路径中
     *       不会以字面量出现，写 {@code /api/auth/**} 将永不匹配</li>
     *   <li>只能放行 login/refresh 两个精确路径——若放行整个 {@code /api/auth/} 前缀，
     *       logout 也会被误放行（UserContext 为空导致 NPE）</li>
     * </ul>
     */
    private static final String[] WHITELIST = {
            "/api/auth/admin/login", "/api/auth/refresh",
            "/api/user/wx-login", "/api/user/refresh",
            "/api/wxpay/notify/", "/actuator/", "/v3/api-docs/", "/swagger-ui/"
    };

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String path = request.getRequestURI();
        for (String w : WHITELIST) {
            if (path.startsWith(w)) {
                return true;
            }
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw BizException.unauthorized("未登录");
        }

        Claims claims = jwtUtil.parse(header.substring(7));
        String type = claims.get("type", String.class);
        Long userId = Long.valueOf(claims.getSubject());
        Long tokenVersion = JwtUtil.tokenVersion(claims);

        // R9 即时吊销：数据库 token_version 不匹配则拒绝（未注册类型的校验器 = 拒绝）
        TokenVersionValidator validator = versionValidators.get(type);
        Long current = validator == null ? null : validator.currentVersion(type, userId);
        if (current == null || !current.equals(tokenVersion)) {
            throw new BizException(ResultCode.TOKEN_REVOKED, "登录已失效，请重新登录");
        }

        UserContext ctx = new UserContext();
        boolean admin = "ADMIN".equals(type);
        ctx.setType(admin ? UserContext.PrincipalType.ADMIN : UserContext.PrincipalType.USER);
        ctx.setUserId(userId);
        if (admin) {
            // B 端：adminId 即登录主体；storeId 取 JWT sid claim（门店管理员登录时签发），
            // 无 sid 默认 0（总部管理员，dataScope=ALL，见 DataScopeInterceptor）
            ctx.setAdminId(userId);
            Object sid = claims.get("sid");
            ctx.setStoreId(sid == null ? 0L : Long.valueOf(String.valueOf(sid)));
            // roleId 取 JWT r claim（RBAC 权限码校验，PermissionInterceptor）
            Object rid = claims.get("r");
            ctx.setRoleId(rid == null ? null : Long.valueOf(String.valueOf(rid)));
        }
        ctx.setSessionId(JwtUtil.sessionId(claims));
        UserContext.set(ctx);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        UserContext.clear();
    }
}
