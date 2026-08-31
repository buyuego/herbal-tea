package com.herbaltea.infrastructure.web;

import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 限流拦截器（拦截器链第 3 道，原网关限流下沉）
 *
 * <p>Redis 滑动窗口计数，key 规范 limit:{scope}:{id}:{path}（部署清单 3.3）。
 * 登录/短信等接口由各 Controller 自行调 {@link #check(String, int, Duration)} 精确限流
 * （如登录失败 5 次锁定、短信每日限额防轰炸 8.3）。
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redis;

    @Value("${app.rate-limit.default-per-minute:120}")
    private int defaultPerMinute;

    private static final String KEY_PREFIX = "limit:";

    /** 精确限流（供登录/短信等接口主动调用） */
    public void check(String scope, String id, int max, Duration window) {
        String key = KEY_PREFIX + scope + ":" + id;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, window);
        }
        if (count != null && count > max) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试");
        }
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        UserContext ctx = UserContext.get();
        if (ctx == null) {
            return true;
        }
        String scope = ctx.getType() == UserContext.PrincipalType.ADMIN ? "admin" : "user";
        check(scope, String.valueOf(ctx.getUserId()) + ":" + request.getRequestURI(),
                defaultPerMinute, Duration.ofMinutes(1));
        return true;
    }
}
