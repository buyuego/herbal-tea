package com.herbaltea.module.settlement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 结算模块骨架实现
 *
 * <p>待实现：
 * <ol>
 *   <li>generateSettlement：按店按周期聚合（settlements 唯一索引 store_id+period+type 兜底，D1）；
 *       明细按积分来源分行（D15：门店积分行 / 平台补贴行）</li>
 *   <li>confirmSettlement：3 天自动确认任务（settlement.confirmed 事件）</li>
 *   <li>reviewAndPay：服务商分账打款（WxPay 分账接口）</li>
 *   <li>reverse：退款冲正（refund.approved 订阅者），状态机 → 90 已冲正</li>
 * </ol>
 */
@Slf4j
@Service
public class SettlementServiceImpl implements SettlementService {

    @Override
    public void generateSettlement(Long storeId, String period) {
        // TODO
    }

    @Override
    public void confirmSettlement(Long settlementId) {
        // TODO: 10 → 20（3 天自动确认，settlement.confirmed 事件发布）
    }

    @Override
    public void reviewAndPay(Long settlementId, Long operatorAdminId) {
        // TODO: @AuditLog(action = "结算打款")；20 → 30 → 40（分账发起）
    }

    @Override
    public void reverse(Long settlementId, Long refundId) {
        // TODO: → 90 已冲正（refund.approved 订阅者调用）
    }
}
