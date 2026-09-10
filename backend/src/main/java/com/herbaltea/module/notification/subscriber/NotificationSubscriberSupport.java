package com.herbaltea.module.notification.subscriber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbaltea.infrastructure.outbox.OutboxEvent;
import com.herbaltea.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通知订阅者支持类：聚合处理方法，被 5 个独立 Subscriber bean 调用。
 *
 * <p>EventSubscriber.type() 是单值约束，所以每个事件类型需要独立 bean。
 * 此处把处理逻辑集中到本类，Subscriber 仅做 type() + 委托。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSubscriberSupport {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /** 处理 order_paid 事件 */
    public void handleOrderPaid(OutboxEvent event) {
        try {
            JsonNode n = objectMapper.readTree(event.getPayload());
            String orderNo = n.path("orderNo").asText();
            Long storeId = n.path("storeId").asLong();
            String title = "新订单待发货";
            String content = "订单 " + orderNo + " 已支付，请尽快安排发货";
            notificationService.pushByRole(3, null, "order", orderNo, title, content,
                    "/order?status=20&keyword=" + orderNo);
            notificationService.pushByRole(4, storeId, "order", orderNo, title, content,
                    "/order?status=20&keyword=" + orderNo);
            log.info("order_paid 通知推送完成 orderNo={}", orderNo);
        } catch (Exception e) {
            throw new IllegalStateException("order_paid 通知处理失败: " + event.getBizKey(), e);
        }
    }

    /** 处理 order_shipped 事件 */
    public void handleOrderShipped(OutboxEvent event) {
        try {
            JsonNode n = objectMapper.readTree(event.getPayload());
            String orderNo = n.path("orderNo").asText();
            Long storeId = n.path("storeId").asLong();
            String trackingNo = n.path("trackingNo").asText("");
            String title = "订单已发货";
            String content = "订单 " + orderNo + " 已发货" + (trackingNo.isEmpty() ? "" : "（" + trackingNo + "）");
            notificationService.pushByRole(4, storeId, "order", orderNo, title, content,
                    "/order?keyword=" + orderNo);
        } catch (Exception e) {
            throw new IllegalStateException("order_shipped 通知处理失败: " + event.getBizKey(), e);
        }
    }

    /** 处理 refund_approved 事件 */
    public void handleRefundApproved(OutboxEvent event) {
        try {
            JsonNode n = objectMapper.readTree(event.getPayload());
            String refundNo = n.path("refundNo").asText(n.path("refundId").asText());
            Long storeId = n.path("storeId").asLong();
            String title = "退款已审批通过";
            String content = "退款单 " + refundNo + " 已审批通过，将原路退回";
            notificationService.pushByRole(4, storeId, "refund", refundNo, title, content,
                    "/refund?keyword=" + refundNo);
            notificationService.pushByRole(3, null, "refund", refundNo, title, content,
                    "/refund?keyword=" + refundNo);
        } catch (Exception e) {
            throw new IllegalStateException("refund_approved 通知处理失败: " + event.getBizKey(), e);
        }
    }

    /** 处理 order_urged 事件 */
    public void handleOrderUrged(OutboxEvent event) {
        try {
            JsonNode n = objectMapper.readTree(event.getPayload());
            String orderNo = n.path("orderNo").asText();
            String title = "店铺催发货";
            String content = "订单 " + orderNo + " 被店铺催促发货，请优先处理";
            notificationService.pushByRole(3, null, "order", orderNo, title, content,
                    "/order?status=20&keyword=" + orderNo);
        } catch (Exception e) {
            throw new IllegalStateException("order_urged 通知处理失败: " + event.getBizKey(), e);
        }
    }

    /** 处理 settlement_confirmed 事件 */
    public void handleSettlementConfirmed(OutboxEvent event) {
        try {
            JsonNode n = objectMapper.readTree(event.getPayload());
            String settlementNo = n.path("settlementNo").asText();
            Long storeId = n.path("storeId").asLong();
            String title = "结算单已确认";
            String content = "结算单 " + settlementNo + " 已确认，等待平台审核";
            notificationService.pushByRole(4, storeId, "settlement", settlementNo, title, content,
                    "/settlement?keyword=" + settlementNo);
        } catch (Exception e) {
            throw new IllegalStateException("settlement_confirmed 通知处理失败: " + event.getBizKey(), e);
        }
    }
}