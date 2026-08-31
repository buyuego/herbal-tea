package com.herbaltea.module.order;

import com.herbaltea.common.exception.BizException;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * 订单状态机（设计文档 16.11 形式化：枚举 + 合法转移矩阵 + version 双条件更新）
 *
 * <p>状态码与 V1__schema.sql 一致：
 * 10待支付 → 20已支付 → 30待发货 → 40已发货 → 50已签收 /
 * 60退款中 / 70已关闭 / 80已退款 / 90已完结 / 95回退失败-待人工
 */
@Getter
public enum OrderStatus {

    PENDING_PAYMENT(10, "待支付"),
    PAID(20, "已支付"),
    PENDING_SHIPMENT(30, "待发货"),
    SHIPPED(40, "已发货"),
    SIGNED(50, "已签收"),
    REFUNDING(60, "退款中"),
    CLOSED(70, "已关闭"),
    REFUNDED(80, "已退款"),
    COMPLETED(90, "已完结"),
    /** 分账回退失败终态（11.3 / D2：2h 观察后自动解冻 + 财务工单，人工处理） */
    REFUND_FALLBACK_FAILED(95, "回退失败-待人工"),
    ;

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 合法转移矩阵（源状态 → 可达目标状态集合） */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING_PAYMENT, Set.of(PAID, CLOSED),          // 支付成功 / 30 分钟超时关单
            PAID,           Set.of(PENDING_SHIPMENT, REFUNDING), // 进入待发货 / 申请退款
            PENDING_SHIPMENT, Set.of(SHIPPED, REFUNDING),   // 发货 / 退款
            SHIPPED,        Set.of(SIGNED, REFUNDING),      // 签收（含 15 天自动签收）/ 退款
            SIGNED,         Set.of(COMPLETED, REFUNDING),   // 确认完结 / 售后退款
            REFUNDING,      Set.of(REFUNDED, REFUND_FALLBACK_FAILED), // 退款成功 / 回退失败终态
            REFUNDED,       Set.of(COMPLETED),              // 退款完成后完结
            CLOSED,         Set.of(),                       // 终态
            COMPLETED,      Set.of(REFUNDING),              // 售后退款（已完结订单退款→结算冲正）
            REFUND_FALLBACK_FAILED, Set.of()                // 终态（人工处理后由财务手工置 REFUNDED）
    );

    /**
     * 校验并返回目标状态。
     *
     * @throws BizException 非法转移（配合 WHERE status = 旧值 AND version = ? 双条件更新，见 16.2）
     */
    public OrderStatus transitTo(OrderStatus target) {
        Set<OrderStatus> allowed = TRANSITIONS.get(this);
        if (allowed == null || !allowed.contains(target)) {
            throw BizException.conflict(
                    "订单状态不允许从「" + desc + "」转移到「" + target.desc + "」");
        }
        return target;
    }

    public static OrderStatus of(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }
}
