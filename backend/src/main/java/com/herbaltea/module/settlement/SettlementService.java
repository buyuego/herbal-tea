package com.herbaltea.module.settlement;

/**
 * 结算模块（store_settlement_configs / settlements / settlement_items）
 *
 * <p>职责：结算单生成（settlement.confirmed：3 天自动确认）、平台审核、
 * 服务商分账发起、退款冲正（refund.approved 订阅者）、
 * 结算单按积分来源分行展示（D15：门店积分行 / 平台补贴行）。
 */
public interface SettlementService {

    /** 生成结算单（每日扫描已确认订单，按店按周期聚合，settlements 唯一索引兜底） */
    void generateSettlement(Long storeId, String period);

    /** 3 天自动确认（settlement.confirmed 事件，状态机 10→20） */
    void confirmSettlement(Long settlementId);

    /** 平台审核通过 → 发起服务商分账打款 */
    void reviewAndPay(Long settlementId, Long operatorAdminId);

    /** 退款冲正（refund.approved 订阅者调用，状态机 → 90 已冲正） */
    void reverse(Long settlementId, Long refundId);
}
