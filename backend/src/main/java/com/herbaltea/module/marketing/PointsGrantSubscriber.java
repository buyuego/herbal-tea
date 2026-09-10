package com.herbaltea.module.marketing;

import com.herbaltea.infrastructure.outbox.EventSubscriber;
import com.herbaltea.infrastructure.outbox.OutboxEvent;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import com.herbaltea.module.marketing.MarketingService;
import com.herbaltea.module.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 积分发放订阅者（示例：order.paid 事件 → 发放积分，D15 双维归属）
 *
 * <p>实现要点：
 * <ul>
 *   <li>幂等由 OutboxWorker 统一处理（tryConsume outbox:{type}:{bizKey}），订阅者只做业务</li>
 *   <li>同进程方法调用（2.3：进程内 Worker 按订阅关系分发）</li>
 *   <li>失败抛异常 → Worker 指数退避重试，超 5 次置 FAILED + 告警</li>
 *   <li>v32 扩展：通知推送（新订单待发货 → 仓管+店长）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointsGrantSubscriber implements EventSubscriber {

    private final MarketingService marketingService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    public OutboxEventType type() {
        return OutboxEventType.order_paid;
    }

    @Override
    public void consume(OutboxEvent event) {
        try {
            JsonNode node = objectMapper.readTree(event.getPayload());
            Long userId = node.get("userId").asLong();
            Long storeId = node.get("storeId").asLong();
            Long orderId = node.get("orderId").asLong();
            String orderNo = node.has("orderNo") ? node.get("orderNo").asText() : String.valueOf(orderId);
            // 发放数量取订单快照 points_earned（1 元 = 1 积分）；缺省时按实付金额兜底
            int amount = node.has("pointsEarned")
                    ? node.get("pointsEarned").asInt()
                    : (int) node.get("payAmount").asDouble();
            // 平台活动商品 sourceType=2（平台补贴）；门店常规商品 sourceType=1（门店成本）
            int sourceType = node.has("platformActivity") && node.get("platformActivity").asBoolean() ? 2 : 1;

            // v32 通知推送：仓管 + 店长（限本店）—— 提前到所有分支之前，无论是否发积分都推送
            String title = "新订单待发货";
            String content = "订单 " + orderNo + " 已支付，请尽快安排发货";
            String link = "/order?status=20&keyword=" + orderNo;
            notificationService.pushByRole(3, null, "order", orderNo, title, content, link);
            notificationService.pushByRole(4, storeId, "order", orderNo, title, content, link);
            log.info("order_paid 通知推送完成 orderNo={}", orderNo);

            if (amount <= 0) {
                log.info("积分发放跳过（无可发放积分）orderNo={} pointsEarned={}", orderNo, amount);
                return;
            }
            marketingService.grantPoints(userId, storeId, orderId, orderNo, amount, sourceType);
        } catch (Exception e) {
            throw new IllegalStateException("积分发放失败: " + event.getBizKey(), e);
        }
    }
}
