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

    /** token_version 比对器：实现方查 users.token_version / admin_users.token_version */
    public interface TokenVersionValidator {
        /** @return 当前有效 version，null 表示主体不存在（已注销） */
        Long currentVersion(String principalType, Long userId);
    }

    private TokenVersionValidator versionValidator = (type, uid) -> 0L;

    public void setVersionValidator(TokenVersionValidator validator) {
        this.versionValidator = validator;
    }

    /** 无需登录的白名单（登录接口 / 微信支付回调 / 健康检查） */
    private static final String[] WHITELIST = {
            "/api/auth/**", "/api/wxpay/notify/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**"
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

        // R9 即时吊销：数据库 token_version 不匹配则拒绝
        Long current = versionValidator.currentVersion(type, userId);
        if (current == null || !current.equals(tokenVersion)) {
            throw new BizException(ResultCode.TOKEN_REVOKED, "登录已失效，请重新登录");
        }

        UserContext ctx = new UserContext();
        ctx.setType("ADMIN".equals(type) ? UserContext.PrincipalType.ADMIN : UserContext.PrincipalType.USER);
        ctx.setUserId(userId);
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
