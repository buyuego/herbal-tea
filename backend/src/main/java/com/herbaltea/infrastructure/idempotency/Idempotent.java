package com.herbaltea.infrastructure.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 写接口幂等注解（16.3 ② 幂等键）
 *
 * <p>标注在写接口上，AOP 切面读取请求头 {@code Idempotency-Key}：
 * <ul>
 *   <li>缺省：按业务规则自动生成（如按请求体摘要 request_hash，幂等键表 24h 窗口）</li>
 *   <li>重复请求：返回 40901（IDEMPOTENT_REPLAY）或首次结果快照</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
