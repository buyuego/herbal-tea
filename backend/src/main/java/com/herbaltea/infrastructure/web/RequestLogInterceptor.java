package com.herbaltea.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 请求日志拦截器（拦截器链第 4 道）
 *
 * <p>为每个请求生成 traceId 注入 MDC（logback pattern 已配置 %X{traceId}），
 * 出参记录耗时与状态，用于慢接口排查（P95 &lt; 300ms 回归门槛，17.2）。
 */
@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID = "traceId";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        MDC.put(TRACE_ID, UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        request.setAttribute("_start", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        long cost = System.currentTimeMillis() - (Long) request.getAttribute("_start");
        log.info("{} {} cost={}ms status={} user={}",
                request.getMethod(), request.getRequestURI(), cost, response.getStatus(),
                UserContext.get() == null ? "-" : UserContext.get().getUserId());
        MDC.remove(TRACE_ID);
    }
}
