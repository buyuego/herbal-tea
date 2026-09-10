package com.herbaltea.module.notification.subscriber;

import com.herbaltea.infrastructure.outbox.EventSubscriber;
import com.herbaltea.infrastructure.outbox.OutboxEvent;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** order_urged 事件订阅者 → 通知仓管（店铺催发货） */
@Component
@RequiredArgsConstructor
public class NotificationOrderUrgedSubscriber implements EventSubscriber {

    private final NotificationSubscriberSupport support;

    @Override
    public OutboxEventType type() {
        return OutboxEventType.order_urged;
    }

    @Override
    public void consume(OutboxEvent e) {
        support.handleOrderUrged(e);
    }
}