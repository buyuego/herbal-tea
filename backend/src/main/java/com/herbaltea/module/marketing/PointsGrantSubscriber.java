package com.herbaltea.module.marketing;

import com.herbaltea.infrastructure.outbox.EventSubscriber;
import com.herbaltea.infrastructure.outbox.OutboxEvent;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import com.herbaltea.module.marketing.MarketingService;
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
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointsGrantSubscriber implements EventSubscriber {

    private final MarketingService marketingService;
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
            // 平台活动商品 sourceType=2（平台补贴）；门店常规商品 sourceType=1（门店成本）
            int sourceType = node.has("platformActivity") && node.get("platformActivity").asBoolean() ? 2 : 1;
            marketingService.grantPoints(userId, storeId, orderId, 10, sourceType);
        } catch (Exception e) {
            throw new IllegalStateException("积分发放失败: " + event.getBizKey(), e);
        }
    }
}
