package com.herbaltea.module.user.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * B 端会员行（v26）：users × 积分账户 × 订单聚合
 *
 * <p>订单统计口径：仅统计「已支付及之后」的有效订单
 * （20已支付 / 30待发货 / 40已发货 / 50已签收 / 90已完结），
 * 排除 10待支付 / 60退款中 / 70已关闭 / 80已退款。
 */
@Data
public class MemberVO {

    private Long id;

    private String openid;

    private String nickname;

    private String avatarUrl;

    /** 手机号（脱敏：138****1234） */
    private String phone;

    /** 0禁用 / 1正常 */
    private Integer status;

    /** 当前可用积分（无积分账户记 0） */
    private Long pointsBalance;

    /** 累计获得积分 */
    private Long totalEarned;

    /** 累计使用积分 */
    private Long totalUsed;

    /** 有效订单数 */
    private Long orderCount;

    /** 累计消费金额（有效订单实付合计） */
    private BigDecimal payTotalAmount;

    /** 最近下单时间 */
    private LocalDateTime lastOrderAt;

    private LocalDateTime createdAt;
}
