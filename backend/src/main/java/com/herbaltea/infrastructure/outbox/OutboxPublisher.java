package com.herbaltea.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbaltea.infrastructure.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Outbox 发布器：业务事务内同步写入 event_outbox（保证"业务成功 ⇔ 事件必达"）
 *
 * <p>用法：{@code outboxPublisher.publish(OutboxEventType.order_paid, orderId, map)}，
 * 必须在业务事务内调用（同库本地事务）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    /**
     * 事务内发布事件。
     *
     * @param bizKey 业务幂等键，如 "order_paid:202608260001"
     */
    @Transactional
    public void publish(OutboxEventType type, String bizKey, Map<String, Object> payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setEventType(type.name());
        event.setBizKey(bizKey);
        event.setStatus(OutboxEvent.STATUS_PENDING);
        event.setRetryCount(0);
        event.setNextRetryAt(LocalDateTime.now());
        try {
            event.setPayload(objectMapper.writeValueAsString(payload == null ? new HashMap<>() : payload));
        } catch (Exception e) {
            throw new IllegalStateException("Outbox payload 序列化失败", e);
        }
        outboxMapper.insert(event);
        log.info("Outbox 发布事件 {} bizKey={} eventId={}", type, bizKey, event.getEventId());
    }
}
