package com.herbaltea.infrastructure.outbox;

import com.herbaltea.infrastructure.idempotency.IdempotencyService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outbox Worker（设计文档 2.3 / ADR-A7）
 *
 * <ul>
 *   <li>每 5 秒扫描待投递事件（OUTBOX_SCAN_INTERVAL），批量 100（OUTBOX_BATCH）</li>
 *   <li>按事件类型分发到注册的订阅者（同进程方法调用）</li>
 *   <li>失败指数退避：next_retry_at = now + 2^retry * 5s，上限 5 次，超限置 FAILED + 告警日志</li>
 *   <li>积压监控：PENDING 待投递超过阈值（OUTBOX_BACKLOG_ALERT）输出告警（16.2 监控项）</li>
 *   <li>单实例调度天然无重复触发（16.12 / D4）；幂等消费由订阅者经 IdempotencyService 兜底</li>
 * </ul>
 *
 * <p>状态流转（对齐 DDL TINYINT）：0 待投递（含退避重试中）→ 成功置 1 + delivered_at；
 * 失败重试保持 0 并后移 next_retry_at；超过上限置 2（终态，转人工，不再轮询）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private final OutboxMapper outboxMapper;
    private final IdempotencyService idempotencyService;
    private final List<EventSubscriber> subscribers;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Value("${app.outbox.max-retry:5}")
    private int maxRetry;

    @Value("${app.outbox.backoff-base-ms:5000}")
    private long backoffBaseMs;

    @Value("${app.outbox.backlog-alert-threshold:100}")
    private long backlogAlertThreshold;

    private final Map<OutboxEventType, EventSubscriber> registry = new ConcurrentHashMap<>();

    @PostConstruct
    void buildRegistry() {
        for (EventSubscriber s : subscribers) {
            registry.put(s.type(), s);
            log.info("Outbox 订阅注册: {} -> {}", s.type(), s.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.scan-interval:5000}")
    public void scan() {
        // 积压告警
        long backlog = outboxMapper.countBacklog();
        if (backlog > backlogAlertThreshold) {
            log.warn("[ALERT] Outbox 积压 {} 条，超过阈值 {}，请检查消费链路", backlog, backlogAlertThreshold);
        }

        List<OutboxEvent> events = outboxMapper.pollPending(LocalDateTime.now(), batchSize);
        if (events.isEmpty()) {
            return;
        }
        for (OutboxEvent event : events) {
            dispatch(event);
        }
    }

    private void dispatch(OutboxEvent event) {
        // 先 CAS 领取：仅 status=0 可抢占，防重复投递（claim 成功才继续）
        if (outboxMapper.claim(event.getEventId()) != 1) {
            return;
        }
        EventSubscriber subscriber = registry.get(OutboxEventType.valueOf(event.getEventType()));
        if (subscriber == null) {
            // 无订阅者：视为已处理（事件已持久化且已置 DISPATCHED，幂等可恢复）
            log.warn("Outbox 事件 {} 无订阅者，视为已投递", event.getEventType());
            return;
        }
        // 幂等消费：同一 bizKey 不重复执行（at-least-once 语义）
        try {
            boolean first = idempotencyService.tryConsume("outbox:" + event.getEventType() + ":" + event.getBizKey());
            if (!first) {
                return;
            }
            subscriber.consume(event);
        } catch (Exception e) {
            handleFailure(event, e);
        }
    }

    private void handleFailure(OutboxEvent event, Exception e) {
        int retry = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
        OutboxEvent upd = new OutboxEvent();
        upd.setId(event.getId());
        upd.setRetryCount(retry);
        if (retry > maxRetry) {
            upd.setStatus(OutboxEvent.STATUS_FAILED);
            log.error("[ALERT] Outbox 事件 {} bizKey={} 超过重试上限，转人工处理（终态规则见设计文档 11.3）",
                    event.getEventType(), event.getBizKey(), e);
        } else {
            // 指数退避：2^retry * base，封顶 10 分钟；保持 status=0 等待下次轮询
            long backoff = Math.min(backoffBaseMs * (1L << Math.min(retry, 7)), 600_000L);
            upd.setStatus(OutboxEvent.STATUS_PENDING);
            upd.setNextRetryAt(LocalDateTime.now().plusNanos(backoff * 1_000_000));
            log.warn("Outbox 事件 {} bizKey={} 第 {} 次重试失败，{}ms 后退避",
                    event.getEventType(), event.getBizKey(), retry, backoff);
        }
        outboxMapper.updateById(upd);
    }
}
