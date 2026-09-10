package com.herbaltea.module.notification.subscriber;

import com.herbaltea.infrastructure.outbox.EventSubscriber;
import com.herbaltea.infrastructure.outbox.OutboxEvent;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** order_shipped 事件订阅者 → 通知店长（仓管已发货） */
@Component
@RequiredArgsConstructor
public class NotificationOrderShippedSubscriber implements EventSubscriber {

    private final NotificationSubscriberSupport support;

    @Override
    public OutboxEventType type() {
        return OutboxEventType.order_shipped;
    }

    @Override
    public void consume(OutboxEvent e) {
        support.handleOrderShipped(e);
    }
}