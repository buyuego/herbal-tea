package com.herbaltea.module.order;

import com.herbaltea.infrastructure.idempotency.IdempotencyService;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import com.herbaltea.infrastructure.outbox.OutboxPublisher;
import com.herbaltea.module.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单模块骨架实现
 *
 * <p>待实现（核心链路，优先级最高）：
 * <ol>
 *   <li>createOrder：幂等键（Idempotency-Key）→ 库存原子扣减 → 写 orders/order_items →
 *       创建支付单（同库本地事务）——事务内可 publish Outbox</li>
 *   <li>handlePaid：微信回调验签 → 状态机 10→20（CAS status+version 双条件）→ 发布 order.paid</li>
 *   <li>ship：30→40 + 物流单 + order.shipped</li>
 *   <li>sign：40→50（15 天自动签收任务触发，解锁积分/结算）</li>
 * </ol>
 * 状态流转一律走 {@link OrderStatus#transitTo(OrderStatus)} 校验 + SQL 双条件更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ProductService productService;
    private final OutboxPublisher outboxPublisher;
    private final IdempotencyService idempotencyService;

    @Override
    public Long createOrder(Long userId, Long storeId, Long skuId, Integer qty, Long addressId) {
        // TODO
        return null;
    }

    @Override
    public void handlePaid(String outTradeNo, String transactionId) {
        // TODO: 验签在 WxPayNotifyController；此处状态推进
        Map<String, Object> payload = new HashMap<>();
        payload.put("outTradeNo", outTradeNo);
        payload.put("transactionId", transactionId);
        outboxPublisher.publish(OutboxEventType.order_paid,
                "paid:" + outTradeNo, payload);
    }

    @Override
    public void ship(Long orderId, Long operatorAdminId, String logisticsNo) {
        // TODO: @AuditLog(action = "订单发货")
    }

    @Override
    public void sign(Long orderId) {
        // TODO
    }
}
