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
     * 确认结算单（10 待确认 → 20 平台审核，confirm_status=2 人工确认）；
     * dev 由超管手动触发。
     */
    void confirm(Long settlementId, Long operatorAdminId);

    /**
     * 自动确认（10 → 20，confirm_status=1 自动确认）：auto_confirm_at 到期由
     * {@code SettlementAutoConfirmTask} 定时扫描触发（第 11 章：72h 未确认视为无异议）。
     */
    void autoConfirm(Long settlementId);

    /**
     * 取「待确认且 auto_confirm_at 已到期」的结算单 id（定时任务扫描入口）
     */
    java.util.List<Long> listAutoConfirmable(int limit);

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
     * 退款冲正（refund_approved 订阅者调用，→ 90 已冲正）：
     * refund_adjust 累加退款金额、final_amount 扣减、插入 type=7 冲正明细行。
     *
     * @param refundNo 退款单号（审计）
     * @param amount 退款金额（冲正金额）
     */
    void reverse(Long settlementId, Long orderId, String refundNo, java.math.BigDecimal amount);

    /**
     * 结算异议申诉（店长对本店结算单，confirm_status → 3 有异议 + dispute_note）：
     * 仅 status ≤ 20（待确认/审核期）可申诉；有异议单不会被自动确认任务吞掉。
     */
    void dispute(Long settlementId, String note);

    /**
     * 复核生成调整单（settlement:reconcile，超管/财务；第 11 章申诉闭环）：
     * 原单 adjust_amount/final_amount 更新 + type=8 调整明细行 +
     * 生成 type=3 调整单（parent_settlement_id 关联，复用 confirm→review→pay 状态机）。
     *
     * @param adjustAmount 调整金额（正数=补款加项）
     * @param remark 复核说明
     */
    Long reconcile(Long settlementId, java.math.BigDecimal adjustAmount, String remark);
}
