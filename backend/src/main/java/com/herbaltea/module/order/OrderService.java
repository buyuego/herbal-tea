package com.herbaltea.module.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.order.dto.CreateOrderRequest;
import com.herbaltea.module.order.dto.OrderCreateVO;
import com.herbaltea.module.order.dto.OrderDetailVO;
import com.herbaltea.module.order.dto.OrderPageQuery;
import com.herbaltea.module.order.dto.ShipRequest;
import com.herbaltea.module.order.entity.Order;
import com.herbaltea.module.order.entity.OrderShippingLog;

import java.util.List;

/**
 * 订单模块（orders / order_items / order_shipping_logs / payment_records）
 *
 * <p>核心链路（设计 v10 §16.4 / 16.11）：
 * <ol>
 *   <li>下单：幂等键（Idempotency-Key 24h 窗口）→ SKU/本店定价校验 → 库存原子扣减（16.4 ③）→
 *       订单/明细/支付单同库本地事务 → 返回收银台参数（30 分钟关单）</li>
 *   <li>支付回调：10→20→30 状态机（乐观锁 CAS 双条件）→ 支付单置成功 → 发布 order.paid</li>
 *   <li>发货：30→40 + 物流单 + 轨迹日志 + order.shipped</li>
 *   <li>签收：40→50（用户确认 / 15 天自动）→ order_auto_signed</li>
 *   <li>关单：30 分钟未支付超时（OrderCloseTask）或用户取消 → 10→70 + 库存回滚 + order_auto_closed</li>
 * </ol>
 */
public interface OrderService {

    // ==================== 下单 ====================

    /** C 端下单（userId 来自登录上下文） */
    OrderCreateVO createOrder(Long userId, CreateOrderRequest req, String idempotencyKey);

    /** B 端代客下单（order:create:behalf 权限，body 携带 userId） */
    OrderCreateVO createOrderForUser(CreateOrderRequest req, String idempotencyKey);

    // ==================== 支付推进 ====================

    /** 支付成功回调（微信验签后调用）：10→20→30 + 支付单置成功 + 发布 order_paid */
    void handlePaid(String outTradeNo, String transactionId);

    // ==================== 状态推进 ====================

    /** 总部发货：30→40 + 物流单 + 轨迹 + order_shipped */
    void ship(Long orderId, Long operatorAdminId, ShipRequest req);

    /** 用户确认签收：40→50（归属校验） */
    void sign(Long orderId, Long userId);

    /** 用户取消（仅待支付 10→70，回滚库存） */
    void cancel(Long orderId, Long userId);

    /** 超时关单（OrderCloseTask 逐单调用，幂等：非待支付直接跳过） */
    void closeExpired(Long orderId);

    // ==================== 查询 ====================

    /** 订单详情（订单头 + 明细 + 支付单） */
    OrderDetailVO getOrderDetail(Long orderId);

    /** B 端分页查询（订单号/用户/门店/状态） */
    IPage<Order> pageOrders(OrderPageQuery query);

    /** 物流轨迹（按时间升序） */
    List<OrderShippingLog> shippingLogs(Long orderId);
}
