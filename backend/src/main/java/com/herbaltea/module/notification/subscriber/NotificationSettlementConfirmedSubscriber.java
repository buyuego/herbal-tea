package com.herbaltea.module.notification.subscriber;

import com.herbaltea.infrastructure.outbox.EventSubscriber;
import com.herbaltea.infrastructure.outbox.OutboxEvent;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** settlement_confirmed 事件订阅者 → 通知店长（结算单已确认待审核） */
@Component
@RequiredArgsConstructor
public class NotificationSettlementConfirmedSubscriber implements EventSubscriber {

    private final NotificationSubscriberSupport support;

    @Override
    public OutboxEventType type() {
        return OutboxEventType.settlement_confirmed;
    }

    @Override
    public void consume(OutboxEvent e) {
        support.handleSettlementConfirmed(e);
    }
}