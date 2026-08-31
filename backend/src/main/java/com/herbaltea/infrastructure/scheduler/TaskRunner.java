package com.herbaltea.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 定时任务执行器（16.12 单实例模型）
 *
 * <p>单进程部署：@Scheduled 触发天然无多实例重复（D4 消除）；本类提供两层防御：
 * <ul>
 *   <li>Redis SETNX 选主（预留）：多实例扩容时启用，lock:cron:{task} TTL 60s，业务零改动</li>
 *   <li>错过补偿：进程重启后检测到任务超过窗口未跑（last_run 时间戳），立即补跑一次</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRunner {

    private final StringRedisTemplate redis;

    private static final String LOCK_PREFIX = "lock:cron:";
    private static final String LAST_RUN_PREFIX = "lastrun:cron:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);

    /**
     * 单实例下恒返回 true；多实例扩容时通过 SETNX 抢锁。
     */
    public boolean acquireLock(String task) {
        if (!isMultiInstance()) {
            return true;
        }
        Boolean ok = redis.opsForValue().setIfAbsent(LOCK_PREFIX + task, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(ok)) {
            log.debug("任务 {} 已被其他实例执行，跳过", task);
            return false;
        }
        return true;
    }

    /** 执行结束后记录最近成功时间（供错过补偿判断） */
    public void recordSuccess(String task) {
        redis.opsForValue().set(LAST_RUN_PREFIX + task, String.valueOf(System.currentTimeMillis()),
                Duration.ofDays(7));
    }

    /** 是否错过窗口（重启停机期间错过执行）——返回 true 表示需要补跑 */
    public boolean isMissed(String task, long maxGapMillis) {
        String last = redis.opsForValue().get(LAST_RUN_PREFIX + task);
        if (last == null) {
            // 无记录：首启不补跑（避免启动即全量扫描），由各任务自身 cron 兜底
            return false;
        }
        return System.currentTimeMillis() - Long.parseLong(last) > maxGapMillis;
    }

    private boolean isMultiInstance() {
        // 单实例部署恒 false；通过配置 app.scheduler.multi-instance=true 开启（演进触发时）
        return Boolean.parseBoolean(System.getenv("SCHEDULER_MULTI_INSTANCE"));
    }
}
