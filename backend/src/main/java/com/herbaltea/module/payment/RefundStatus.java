package com.herbaltea.module.payment;

import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * 退款状态机（设计文档 11.3 / D2 / A3）
 *
 * <p>退款记录（refund_records）：10 待审批 → 20 审批通过 → 30 微信退款中 → 40 已退款；
 * 分账回退失败时进入 95 回退失败-待人工终态（2h 观察自动解冻 + 财务工单）。
 */
@Getter
public enum RefundStatus {

    PENDING_APPROVAL(10, "待审批"),
    APPROVED(20, "审批通过"),
    REFUNDING(30, "退款中"),
    REFUNDED(40, "已退款"),
    REJECTED(50, "已驳回"),
    /** 分账回退失败终态（D2/A3）：自动解冻 + 财务工单 + 告警，人工处理后置 REFUNDED */
    FALLBACK_FAILED(95, "回退失败-待人工"),
    ;

    private final int code;
    private final String desc;

    RefundStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private static final Map<RefundStatus, Set<RefundStatus>> TRANSITIONS = Map.of(
            PENDING_APPROVAL, Set.of(APPROVED, REJECTED),     // 门店审批 / 驳回
            APPROVED,        Set.of(REFUNDING, REJECTED),      // 发起微信退款 / 退货验货不通过驳回（v20）
            REFUNDING,       Set.of(REFUNDED, FALLBACK_FAILED),// 退款成功 / 回退失败终态
            REJECTED,        Set.of(),                          // 终态
            REFUNDED,        Set.of(),                          // 终态
            FALLBACK_FAILED, Set.of()                           // 终态（人工处理后由财务手工置 REFUNDED）
    );

    public RefundStatus transitTo(RefundStatus target) {
        Set<RefundStatus> allowed = TRANSITIONS.get(this);
        if (allowed == null || !allowed.contains(target)) {
            throw new IllegalArgumentException("退款状态不允许 " + this + " -> " + target);
        }
        return target;
    }

    public static RefundStatus of(int code) {
        for (RefundStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知退款状态: " + code);
    }
}
