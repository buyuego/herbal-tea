package com.herbaltea.infrastructure.audit;

import com.herbaltea.infrastructure.web.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 审计切面（15.2 / D6）：敏感操作落 operation_logs
 *
 * <p>实现要点：
 * <ul>
 *   <li>操作人以 JWT 登录主体 + 设备指纹服务端落库为准（选择框名单仅作展示、服务端校验）</li>
 *   <li>此处为骨架：记录结构化日志；正式实现需异步写 operation_logs 表
 *       （DTO → module-auth 的 AuditLogService），含前后快照</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result;
        Throwable error = null;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            UserContext ctx = UserContext.get();
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            log.info("[AUDIT] action={} method={} operator={}/{}/store={} args={} cost={}ms success={}",
                    auditLog.action(),
                    sig.getMethod().getDeclaringClass().getSimpleName() + "." + sig.getName(),
                    ctx == null ? "-" : ctx.getType(),
                    ctx == null ? "-" : ctx.getUserId(),
                    UserContext.storeId(),
                    pjp.getArgs().length,
                    System.currentTimeMillis() - start,
                    error == null);
            // TODO(D6): 异步写 operation_logs（操作人快照 + IP + 前后值），由 module-auth 提供服务
        }
        return result;
    }
}
