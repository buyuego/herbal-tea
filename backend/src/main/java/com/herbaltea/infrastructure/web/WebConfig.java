package com.herbaltea.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器链注册（原独立 API 网关下沉为进程内组件，功能一致零运维，ADR-A1）
 *
 * <p>顺序：
 * <ol>
 *   <li>RequestLogInterceptor —— 日志 / traceId</li>
 *   <li>AuthInterceptor —— JWT 鉴权 + token_version 即时吊销（R9/D12）</li>
 *   <li>DataScopeInterceptor —— 数据权限范围注入（防越权首道拦截）</li>
 *   <li>RateLimitInterceptor —— 限流</li>
 * </ol>
 * 写接口幂等由 {@code @Idempotent} 注解 + AOP 处理（见 infrastructure.idempotency），
 * 审计日志由 {@code @AuditLog} 注解 + AOP 处理（见 infrastructure.audit）。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RequestLogInterceptor requestLogInterceptor;
    private final AuthInterceptor authInterceptor;
    private final DataScopeInterceptor dataScopeInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLogInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(dataScopeInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
    }
}
