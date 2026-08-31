package com.herbaltea.module.payment;

/**
 * 支付售后模块（payment_records / refund_records / return_orders）
 *
 * <p>职责：微信支付下单与回调验签、退款审批（24h 未审批自动升级总部）、
 * 服务商分账（含回退失败 2h 解冻终态，11.3 / D2）。
 */
public interface PaymentService {

    /** 微信支付统一下单（服务商模式） */
    String createWxPay(Long orderId);

    /** 支付回调验签 + 幂等处理（回调可能重复，16.3） */
    void handlePayNotify(String rawBody, String signature);

    /** 退款申请（门店发起）→ 待审批 */
    Long applyRefund(Long orderId, Long storeAdminId, String reason);

    /** 门店审批通过 → 发起微信退款（refund.approved 事件驱动结算冲正） */
    void approveRefund(Long refundId, Long approverAdminId);

    /**
     * 分账回退失败终态（D2/A3）：
     * 回退失败 → 2h 观察窗口 → 自动解冻 + 置 95 回退失败-待人工 + 财务工单 + 告警
     */
    void handleFallbackFailed(Long refundId);

    /** 售后退货（return_orders，签收后 7 天内） */
    Long applyReturn(Long orderId, Long userId, String reason);
}
