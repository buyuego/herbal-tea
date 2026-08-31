package com.herbaltea.infrastructure.scheduler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 统一定时任务注解（设计文档 16.12 / ADR-A6）
 *
 * <p>任务统一实现 {@link ScheduledTaskRunner} 并标注本注解，由 TaskScheduler 统一编排：
 * <ul>
 *   <li>bizKey 幂等（进程崩溃重启后重扫不重复执行，防御纵深）</li>
 *   <li>错过补偿（进程重启后补跑一次）</li>
 *   <li>多实例部署时：TaskScheduler 用 Redis SETNX 选主（lock:cron:{task} TTL 60s），业务零改动</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScheduledTask {

    /** 任务标识，如 "close_timeout_orders"（对应 Redis 选主 key lock:cron:{task}） */
    String task();
}
