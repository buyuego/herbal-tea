package com.herbaltea.module.settlement;

import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * 结算单状态机（第 11 章：3 天自动确认 → 平台审核 → 打款；退款触发结算冲正）
 */
@Getter
public enum SettlementStatus {

    PENDING_CONFIRM(10, "待确认"),
    PLATFORM_REVIEW(20, "平台审核"),
    SETTLED(30, "已结算"),
    PAID(40, "已打款"),
    /** refund_approved 事件触发结算冲正（refund_approved 订阅者调用） */
    REVERSED(90, "已冲正"),
    ;

    private final int code;
    private final String desc;

    SettlementStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private static final Map<SettlementStatus, Set<SettlementStatus>> TRANSITIONS = Map.of(
            PENDING_CONFIRM, Set.of(PLATFORM_REVIEW),   // 3 天自动确认（settlement.confirmed 事件）
            PLATFORM_REVIEW, Set.of(SETTLED, REVERSED), // 平台审核通过 / 退款触发冲正
            SETTLED,         Set.of(PAID, REVERSED),    // 打款 / 冲正
            PAID,            Set.of(REVERSED),          // 打款后退款（回退扣款）
            REVERSED,        Set.of()                   // 终态
    );

    public SettlementStatus transitTo(SettlementStatus target) {
        Set<SettlementStatus> allowed = TRANSITIONS.get(this);
        if (allowed == null || !allowed.contains(target)) {
            throw new IllegalArgumentException("结算状态不允许 " + this + " -> " + target);
        }
        return target;
    }

    /** 按 code 反查（未知 code 抛 IllegalArgumentException） */
    public static SettlementStatus of(int code) {
        for (SettlementStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知结算状态: " + code);
    }
}
