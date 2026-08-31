package com.herbaltea.module.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 支付售后模块骨架实现
 *
 * <p>待实现（风险最高链路，PRD ST 系列验收指向）：
 * <ol>
 *   <li>createWxPay：WxPayService 服务商模式统一下单（V3 API）</li>
 *   <li>handlePayNotify：验签（V3 签名）→ 幂等处理（回调可重复）→ 状态机推进</li>
 *   <li>approveRefund：审批通过（@AuditLog）→ 微信退款 → 分账回退（服务商分账权限，1-3 周申请）</li>
 *   <li>handleFallbackFailed：D2/A3 回退失败 2h 观察 → 自动解冻 → 95 终态 + 财务工单</li>
 *   <li>退款 24h 未审批自动升级总部（refund.auto_escalated 事件）</li>
 * </ol>
 */
@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    @Override
    public String createWxPay(Long orderId) {
        // TODO: WxJava pay 服务（wx.pay 配置注入）
        return null;
    }

    @Override
    public void handlePayNotify(String rawBody, String signature) {
        // TODO: 验签失败抛 401/400；成功按 out_trade_no 幂等推进（16.3）
    }

    @Override
    public Long applyRefund(Long orderId, Long storeAdminId, String reason) {
        // TODO: 状态机订单 20/30/40 → 60 退款中（先校验门店归属 Data Scope）
        return null;
    }

    @Override
    public void approveRefund(Long refundId, Long approverAdminId) {
        // TODO: @AuditLog(action = "退款审批")；RefundStatus 10→20→30→40/95
        //       refund.approved 事件 → 结算冲正订阅者
    }

    @Override
    public void handleFallbackFailed(Long refundId) {
        // TODO: 回退失败 → 2h 后自动解冻（定时扫描 refund_records）
        //       → RefundStatus.FALLBACK_FAILED（95 终态）+ 财务工单 + 告警（11.3/D2）
    }

    @Override
    public Long applyReturn(Long orderId, Long userId, String reason) {
        // TODO: return_orders（refund_id 唯一索引兜底，D1）
        return null;
    }
}
