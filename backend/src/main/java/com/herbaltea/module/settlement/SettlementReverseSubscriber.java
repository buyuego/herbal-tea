package com.herbaltea.module.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbaltea.infrastructure.outbox.EventSubscriber;
import com.herbaltea.infrastructure.outbox.OutboxEvent;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import com.herbaltea.module.settlement.entity.Settlement;
import com.herbaltea.module.settlement.mapper.SettlementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 结算冲正订阅者（refund_approved 事件 → 结算单冲正，第 11 章）
 *
 * <p>实现要点：
 * <ul>
 *   <li>幂等由 OutboxWorker 统一处理（tryConsume outbox:refund_approved:{bizKey}），
 *       reverse 内部再兜底（status=90 直接返回）</li>
 *   <li>按退款订单反查所属结算单（经 settlement_items）：
 *       <ul>
 *         <li>订单未参与结算 → 无需冲正（订单已置 80 已退款，后续生成不会纳入）</li>
 *         <li>结算单 10/20/30 → 冲正 → 90 + refund_adjust 累加 + type=7 明细行</li>
 *         <li>结算单 40 已打款 → 无法自动冲正，告警日志转人工（TODO 微信分账原路退回）</li>
 *       </ul>
 *   <li>失败抛异常 → Worker 指数退避重试，超 5 次置 FAILED + 告警</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementReverseSubscriber implements EventSubscriber {

    private final SettlementService settlementService;
    private final SettlementMapper settlementMapper;
    private final ObjectMapper objectMapper;

    @Override
    public OutboxEventType type() {
        return OutboxEventType.refund_approved;
    }

    @Override
    public void consume(OutboxEvent event) {
        try {
            JsonNode node = objectMapper.readTree(event.getPayload());
            Long orderId = node.get("orderId").asLong();
            String refundNo = node.get("refundNo").asText();
            BigDecimal amount = node.get("amount").decimalValue();

            Settlement s = settlementMapper.selectByOrderId(orderId);
            if (s == null) {
                log.info("退款订单 {} 未参与结算单，无需冲正（refundNo={}）", orderId, refundNo);
                return;
            }
            if (s.getStatus() == SettlementStatus.REVERSED.getCode()) {
                log.info("结算单 {} 已冲正，忽略重复事件（refundNo={}）", s.getSettleNo(), refundNo);
                return;
            }
            if (s.getStatus() == SettlementStatus.PAID.getCode()) {
                // 已打款：不能自动冲正，转人工（11.3 终态规则：分账原路退回 TODO 接微信）
                log.error("[ALERT] 结算单 {} 已打款（payoutNo={}），退款 {} 无法自动冲正，转人工处理",
                        s.getSettleNo(), s.getPayoutNo(), refundNo);
                return;
            }
            settlementService.reverse(s.getId(), orderId, refundNo, amount);
        } catch (Exception e) {
            throw new IllegalStateException("结算冲正失败: " + event.getBizKey(), e);
        }
    }
}
