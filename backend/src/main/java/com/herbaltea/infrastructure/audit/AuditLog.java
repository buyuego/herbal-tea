package com.herbaltea.infrastructure.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解（设计文档 15.2 / D6）
 *
 * <p>标注在需要审计的写接口上（退款审批、打款、改价、权限变更等敏感操作），
 * AOP 切面记录：操作人（以 JWT 登录主体 + 设备指纹服务端落库为准，选择框名单仅作展示）、
 * 动作、对象、前后快照、IP，落 operation_logs 表。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 业务动作描述，如 "退款审批" */
    String action();
}
