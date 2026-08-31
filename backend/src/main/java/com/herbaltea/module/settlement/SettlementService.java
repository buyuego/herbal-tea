package com.herbaltea.module.settlement;

import com.herbaltea.common.result.PageResult;
import com.herbaltea.module.settlement.dto.SettlementDetailVO;
import com.herbaltea.module.settlement.dto.SettlementPageQuery;

/**
 * 结算模块（store_settlement_configs / settlements / settlement_items）
 *
 * <p>全流程（设计文档第 11 章）：系统按周期生成结算单（10 待确认）→
 * 72h 未确认自动无异议进入平台审核（20）→ 平台财务审核（30）→
 * 打款（40，服务商分账，dev 模拟）→ 退款触发冲正（90）。
 *
 * <p>金额口径（11.2）：结算金额 = 总额 - 平台佣金 - 门店营销积分抵扣 -
 * 门店营销积分成本 - 本店券成本 - 退款冲正 + 调整单；平台活动积分由平台承担
 * （platform 行，不扣店铺）。
 */
public interface SettlementService {

    /**
     * 分页查询（JOIN stores；STORE 管理员按 storeIds 强制过滤）
     */
    PageResult<SettlementDetailVO> page(SettlementPageQuery query);

    /**
     * 详情（结算单 + 门店 + 明细行 D15）
     */
    SettlementDetailVO detail(Long settlementId);

    /**
     * 生成结算单：按店按周期聚合「已完结（订单 status=90）且未参与过结算」的订单，
     * 明细按积分来源分行（D15），settlements 唯一索引兜底（D1）。
     *
     * @param storeId 门店；null = 全部门店
     * @param period 结算周期（日结=yyyy-MM-dd 的 finished_at 归属）
     */
    void generate(Long storeId, String period);

    /**
     * 确认结算单（10 待确认 → 20 平台审核）；dev 由超管手动触发（生产 3 天自动确认任务）。
     */
    void confirm(Long settlementId, Long operatorAdminId);

    /**
     * 平台财务审核通过（20 → 30 已结算，settlement:review）；
     * 回填审核人，CAS(status, version) 防并发。
     */
    void review(Long settlementId, Long operatorAdminId);

    /**
     * 打款确认（30 → 40 已打款，settlement:payout 敏感权限，仅超管）；
     * dev 模拟服务商分账（生成 payout_no），TODO 接微信分账接口。
     */
    void pay(Long settlementId, Long operatorAdminId);

    /**
     * 退款冲正（refund.approved 订阅者调用，→ 90 已冲正）
     */
    void reverse(Long settlementId, Long refundId);
}
