package com.herbaltea.infrastructure.idempotency;

import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 幂等切面（16.3 ② 幂等键）
 *
 * <p>读取请求头 Idempotency-Key；缺省时按 请求方法 + 路径 + 请求体摘要 生成
 * （对应 V1__schema.sql 中 idempotency_keys.request_hash）。重复请求返回 40901。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final IdempotencyService idempotencyService;

    @Around("@annotation(com.herbaltea.infrastructure.idempotency.Idempotent)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs == null ? null : attrs.getRequest();

        String key = request == null ? null : request.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) {
            key = defaultKey(request, pjp);
        }

        if (!idempotencyService.tryAcquire(key)) {
            throw new BizException(ResultCode.IDEMPOTENT_REPLAY, "重复请求，请勿重复提交");
        }
        return pjp.proceed();
    }

    private String defaultKey(HttpServletRequest request, ProceedingJoinPoint pjp) {
        // TODO: 与 idempotency_keys.request_hash 对齐——取请求体 SHA-256 + 路径
        return "req:" + (request == null ? "" : request.getRequestURI())
                + ":" + pjp.getArgs().length;
    }
}
