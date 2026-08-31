package com.herbaltea.infrastructure.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 幂等键服务（设计文档 16.3 / ADR-A2）
 *
 * <p>两层语义：
 * <ul>
 *   <li><b>请求级防重（24h 窗口）</b>：同一 Idempotency-Key 的写请求只执行一次，
 *       重复请求返回首次结果（Redis SETNX，key 前缀 idem:）</li>
 *   <li><b>业务唯一性</b>：由数据库 5 组 UNIQUE 索引兜底（orders / refund_records /
 *       return_orders / settlements / event_outbox），DB 层不可绕过</li>
 * </ul>
 * 幂等键窗口 24h 后自然过期，业务唯一性仍由索引保证。
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redis;

    @Value("${app.idempotency.ttl:24h}")
    private Duration ttl;

    private static final String KEY_PREFIX = "idem:";

    /**
     * 尝试占位幂等键。
     *
     * @return true = 首次请求（继续执行业务）；false = 重复请求（返回上次结果或 409）
     */
    public boolean tryAcquire(String idempotencyKey) {
        Boolean ok = redis.opsForValue().setIfAbsent(KEY_PREFIX + idempotencyKey, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 释放幂等键（业务失败回滚后调用，允许客户端用同一键重试）。
     *
     * <p>事务语义：业务异常时本地事务已回滚（库存/订单均未落库），
     * 释放键是安全的；仅在事务提交后才应保留键（防重放）。
     */
    public void release(String idempotencyKey) {
        redis.delete(KEY_PREFIX + idempotencyKey);
    }

    /**
     * 事件消费幂等（Outbox Worker 调用）：与写接口共用同一套幂等键体系，
     * 重复投递不重复执行（16.3 / 2.3）。
     */
    public boolean tryConsume(String bizKey) {
        return tryAcquire(bizKey);
    }
}
