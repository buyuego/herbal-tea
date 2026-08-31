package com.herbaltea.module.order;

/**
 * 订单模块（orders / order_items / order_shipping_logs）
 *
 * <p>职责：下单（幂等 + 库存原子扣减 + Outbox 发单）、支付回调状态推进、
 * 状态机流转（{@link OrderStatus}）、发货/签收、超时关单与 15 天自动签收任务。
 */
public interface OrderService {

    /** 下单：库存原子扣减 + 幂等键防重 + 本地事务 + 支付单创建 */
    Long createOrder(Long userId, Long storeId, Long skuId, Integer qty, Long addressId);

    /** 支付成功回调（微信验签后）：10→20 状态机 + 发布 order.paid 事件 */
    void handlePaid(String outTradeNo, String transactionId);

    /** 总部发货：30→40，写物流单 + 发布 order.shipped */
    void ship(Long orderId, Long operatorAdminId, String logisticsNo);

    /** 签收（用户确认或 15 天自动，40→50） */
    void sign(Long orderId);
}
