package com.herbaltea.module.settlement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.PageResult;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.settlement.dto.SettlementDetailVO;
import com.herbaltea.module.settlement.dto.SettlementPageQuery;
import com.herbaltea.module.settlement.entity.Settlement;
import com.herbaltea.module.settlement.entity.SettlementItem;
import com.herbaltea.module.settlement.mapper.SettlementItemMapper;
import com.herbaltea.module.settlement.mapper.SettlementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 结算模块实现（设计文档第 11 章）
 *
 * <p>全流程：generate（按店按周期聚合已完结订单）→ confirm（10→20，dev 手动/生产自动）
 * → review（20→30，平台财务）→ pay（30→40，dev 模拟分账）→ reverse（→90，退款冲正）。
 *
 * <p>金额口径（11.2 D15）：佣金 = 订单 total × commission_rate（下单快照）；
 * 门店营销积分抵扣/成本、平台补贴、本店券按订单字段归集，明细行分行展示。
 * dev 简化：微信服务商分账未接入，打款生成 payout_no 模拟（TODO 接微信分账）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    /** 订单「已完结」= 可结算（OrderStatus.COMPLETED=90） */
    private static final int ORDER_COMPLETED = 90;
    /** 积分来源：门店营销 */
    private static final int POINTS_SOURCE_STORE = 1;
    /** 积分来源：平台活动 */
    private static final int POINTS_SOURCE_PLATFORM = 2;
    /** 积分成本单价：1 积分 = 0.01 元（营销成本估算，TODO 接营销模块精确成本） */
    private static final BigDecimal POINTS_COST_UNIT = new BigDecimal("0.01");

    private final SettlementMapper settlementMapper;
    private final SettlementItemMapper settlementItemMapper;

    // ---------- 查询 ----------

    @Override
    public PageResult<SettlementDetailVO> page(SettlementPageQuery query) {
        List<Long> storeIds = resolveVisibleStoreIds();
        if (storeIds != null && storeIds.isEmpty()) {
            return PageResult.of(0, query.getPage(), query.getSize(), List.of());
        }
        long total = settlementMapper.countVO(query, storeIds);
        List<com.herbaltea.module.settlement.dto.SettlementPageVO> rows = settlementMapper.pageVO(query, storeIds);
        List<SettlementDetailVO> list = new ArrayList<>();
        for (com.herbaltea.module.settlement.dto.SettlementPageVO r : rows) {
            list.add(toDetailVO(r));
        }
        return PageResult.of(total, query.getPage(), query.getSize(), list);
    }

    @Override
    public SettlementDetailVO detail(Long settlementId) {
        Settlement s = requireSettlement(settlementId);
        checkStoreAccess(s.getStoreId());
        SettlementDetailVO vo = new SettlementDetailVO();
        copyToVO(s, vo);
        vo.setStatusDesc(SettlementStatus.of(s.getStatus()).getDesc());
        vo.setItems(settlementItemMapper.listBySettlementId(settlementId));
        return vo;
    }

    // ---------- 生成（dev 造数 / 生产定时任务入口） ----------

    @Override
    @Transactional
    public void generate(Long storeId, String period) {
        if (period == null || period.isBlank()) {
            throw new BizException("结算周期不能为空（日结=yyyy-MM-dd）");
        }
        LocalDate periodDate;
        try {
            periodDate = LocalDate.parse(period.trim());
        } catch (Exception e) {
            throw new BizException("结算周期格式错误，应为 yyyy-MM-dd");
        }
        LocalDateTime start = periodDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        List<SettlementMapper.OrderRow> orders;
        if (storeId != null) {
            checkStoreAccess(storeId);
            orders = settlementMapper.listUnsettledOrders(storeId, start, end);
            if (orders.isEmpty()) {
                throw new BizException("该门店该周期无「已完结」且未结算的订单");
            }
            createSettlement(storeId, period, orders);
        } else {
            // 全部门店：按店分组生成（唯一索引 uk_set_store_period 兜底，D1）
            for (Long sid : settlementMapper.listStoreIdsWithUnsettled(start, end)) {
                List<SettlementMapper.OrderRow> rows = settlementMapper.listUnsettledOrders(sid, start, end);
                if (!rows.isEmpty()) {
                    try {
                        createSettlement(sid, period, rows);
                    } catch (Exception e) {
                        log.warn("generate settlement skipped for store {} period {}: {}", sid, period, e.getMessage());
                    }
                }
            }
        }
    }

    // ---------- 状态流转 ----------

    @Override
    @Transactional
    public void confirm(Long settlementId, Long operatorAdminId) {
        Settlement s = requireSettlement(settlementId);
        checkStoreAccess(s.getStoreId());
        transit(s, SettlementStatus.PLATFORM_REVIEW);
        s.setConfirmStatus(2); // 人工确认（1=自动确认，见 settlements.confirm_status 注释）
        s.setConfirmedAt(LocalDateTime.now());
        settlementMapper.updateById(s);
    }

    @Override
    public List<Long> listAutoConfirmable(int limit) {
        return settlementMapper.selectAutoConfirmableIds(limit);
    }

    @Override
    @Transactional
    public void autoConfirm(Long settlementId) {
        Settlement s = requireSettlement(settlementId);
        if (s.getStatus() != SettlementStatus.PENDING_CONFIRM.getCode()) {
            return; // 幂等：已被手动确认/并发处理
        }
        transit(s, SettlementStatus.PLATFORM_REVIEW);
        s.setConfirmStatus(1); // 自动确认（72h 无异议）
        s.setConfirmedAt(LocalDateTime.now());
        settlementMapper.updateById(s);
        log.info("settlement auto-confirmed: {} (72h 无异议)", s.getSettleNo());
    }

    @Override
    @Transactional
    public void review(Long settlementId, Long operatorAdminId) {
        Settlement s = requireSettlement(settlementId);
        transit(s, SettlementStatus.SETTLED);
        settlementMapper.markReviewed(settlementId, operatorAdminId);
    }

    @Override
    @Transactional
    public void pay(Long settlementId, Long operatorAdminId) {
        Settlement s = requireSettlement(settlementId);
        transit(s, SettlementStatus.PAID);
        String payoutNo = "PO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        settlementMapper.markPaid(settlementId, LocalDateTime.now(), payoutNo);
    }

    @Override
    @Transactional
    public void reverse(Long settlementId, Long orderId, String refundNo, BigDecimal amount) {
        Settlement s = requireSettlement(settlementId);
        if (s.getStatus() == SettlementStatus.REVERSED.getCode()) {
            return; // 幂等：已冲正
        }
        transit(s, SettlementStatus.REVERSED);

        // 冲正金额：refund_adjust 累加，final_amount 扣减（不低于 0）
        BigDecimal adj = nz(amount);
        s.setRefundAdjust(nz(s.getRefundAdjust()).add(adj));
        s.setFinalAmount(nz(s.getFinalAmount()).subtract(adj).max(BigDecimal.ZERO));
        settlementMapper.updateById(s);

        // 冲正明细行（type=7 冲正，direction=2 店铺减项）
        SettlementItem it = new SettlementItem();
        it.setSettlementId(settlementId);
        it.setOrderId(orderId);
        it.setOrderNo(settlementMapper.selectOrderNo(orderId));
        it.setItemType(SettlementItem.ITEM_REFUND_ADJUST);
        it.setDirection(SettlementItem.DIR_DEDUCT);
        it.setAmount(adj);
        it.setRemark("退款冲正 " + refundNo);
        settlementItemMapper.insert(it);
        log.info("settlement reversed: {} order={} refundNo={} amount={}",
                s.getSettleNo(), orderId, refundNo, adj);
    }

    // ---------- 申诉 / 复核（第 11 章异议闭环） ----------

    @Override
    @Transactional
    public void dispute(Long settlementId, String note) {
        Settlement s = requireSettlement(settlementId);
        checkStoreAccess(s.getStoreId());
        if (note == null || note.isBlank()) {
            throw new BizException("异议说明不能为空");
        }
        if (s.getStatus() != SettlementStatus.PENDING_CONFIRM.getCode()
                && s.getStatus() != SettlementStatus.PLATFORM_REVIEW.getCode()) {
            throw new BizException("结算单当前状态（" + SettlementStatus.of(s.getStatus()).getDesc()
                    + "）不可申诉，仅待确认/审核期可提出异议");
        }
        if (s.getConfirmStatus() == 3) {
            throw new BizException("该结算单已在异议处理中，请等待平台复核");
        }
        s.setConfirmStatus(3); // 有异议
        s.setDisputeNote(note.trim());
        settlementMapper.updateById(s);
        log.info("settlement disputed: {} note={}", s.getSettleNo(), note.trim());
    }

    @Override
    @Transactional
    public Long reconcile(Long settlementId, BigDecimal adjustAmount, String remark) {
        Settlement s = requireSettlement(settlementId);
        if (s.getConfirmStatus() != 3) {
            throw new BizException("仅「有异议」的结算单可复核生成调整单");
        }
        if (adjustAmount == null || adjustAmount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BizException("调整金额不能为空或为零");
        }

        // 原单：adjust_amount 累加 + final_amount 调整（负数扣减时钳零）+ 异议标记复位
        s.setAdjustAmount(nz(s.getAdjustAmount()).add(adjustAmount));
        s.setFinalAmount(nz(s.getFinalAmount()).add(adjustAmount).max(BigDecimal.ZERO));
        s.setConfirmStatus(2); // 复核完毕恢复人工确认
        settlementMapper.updateById(s);

        // 原单明细：type=8 调整行（正数加项 direction=1 / 负数减项 direction=2）
        SettlementItem it = new SettlementItem();
        it.setSettlementId(settlementId);
        it.setItemType(SettlementItem.ITEM_ADJUST);
        it.setDirection(adjustAmount.signum() > 0 ? SettlementItem.DIR_INCOME : SettlementItem.DIR_DEDUCT);
        it.setAmount(adjustAmount.abs());
        it.setRemark("复核调整：" + (remark == null || remark.isBlank() ? "申诉复核" : remark.trim()));
        settlementItemMapper.insert(it);

        // 生成调整单（type=3，关联原单，复用状态机 10→20→30→40）
        Settlement adj = new Settlement();
        adj.setSettleNo(generateNo("AD"));
        adj.setStoreId(s.getStoreId());
        adj.setPeriod(s.getPeriod());
        adj.setType(Settlement.TYPE_ADJUST);
        adj.setOrderCount(0);
        adj.setAdjustAmount(adjustAmount);
        adj.setFinalAmount(adjustAmount.max(BigDecimal.ZERO));
        adj.setConfirmStatus(0);
        adj.setStatus(SettlementStatus.PENDING_CONFIRM.getCode());
        adj.setParentSettlementId(settlementId);
        adj.setAutoConfirmAt(LocalDateTime.now().plusHours(72));
        settlementMapper.insert(adj);

        SettlementItem adjItem = new SettlementItem();
        adjItem.setSettlementId(adj.getId());
        adjItem.setItemType(SettlementItem.ITEM_ADJUST);
        adjItem.setDirection(adjustAmount.signum() > 0 ? SettlementItem.DIR_INCOME : SettlementItem.DIR_DEDUCT);
        adjItem.setAmount(adjustAmount.abs());
        adjItem.setRemark("调整单（关联 " + s.getSettleNo() + "）：" + (remark == null || remark.isBlank() ? "申诉复核" : remark.trim()));
        settlementItemMapper.insert(adjItem);

        log.info("settlement reconciled: {} -> adjust settlement {} amount={} remark={}",
                s.getSettleNo(), adj.getSettleNo(), adjustAmount, remark);
        return adj.getId();
    }

    // ---------- 内部方法 ----------

    /** 生成单张结算单（聚合 + 明细分行 D15） */
    private void createSettlement(Long storeId, String period, List<SettlementMapper.OrderRow> orders) {
        Settlement s = new Settlement();
        s.setSettleNo(generateNo("SL"));
        s.setStoreId(storeId);
        s.setPeriod(period);
        s.setType(Settlement.TYPE_DAILY);
        s.setOrderCount(orders.size());
        s.setConfirmStatus(0);
        s.setStatus(SettlementStatus.PENDING_CONFIRM.getCode());

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        BigDecimal pointsDeduct = BigDecimal.ZERO;
        BigDecimal pointsCostStore = BigDecimal.ZERO;
        BigDecimal pointsCostPlatform = BigDecimal.ZERO;
        BigDecimal couponCost = BigDecimal.ZERO;

        List<SettlementItem> items = new ArrayList<>();
        for (SettlementMapper.OrderRow o : orders) {
            BigDecimal amt = nz(o.getTotalAmount());
            BigDecimal rate = o.getCommissionRate() != null ? o.getCommissionRate() : new BigDecimal("0.0500");
            BigDecimal comm = amt.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amt);
            commission = commission.add(comm);

            // 明细行：订单销售额（店铺加项）
            items.add(item(s.getId(), o, SettlementItem.ITEM_SALES, SettlementItem.DIR_INCOME, amt, "订单销售额"));

            // 明细行：平台佣金（店铺减项）
            items.add(item(s.getId(), o, SettlementItem.ITEM_COMMISSION, SettlementItem.DIR_DEDUCT, comm, "平台佣金 " + rate.multiply(BigDecimal.valueOf(100)).setScale(1) + "%"));

            // 积分：门店营销（店铺承担）/ 平台活动（平台补贴）
            BigDecimal pd = nz(o.getPointsDeductAmount());
            BigDecimal cost = o.getPointsEarned() != null
                    ? BigDecimal.valueOf(o.getPointsEarned()).multiply(POINTS_COST_UNIT).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            if (o.getPointsSource() != null && o.getPointsSource() == POINTS_SOURCE_PLATFORM) {
                // 平台活动积分：抵扣与成本均由平台承担
                pointsCostPlatform = pointsCostPlatform.add(pd).add(cost);
                if (pd.signum() > 0) {
                    items.add(item(s.getId(), o, SettlementItem.ITEM_POINTS_DEDUCT, SettlementItem.DIR_PLATFORM, pd, "平台活动积分抵扣（平台补贴）"));
                }
                if (cost.signum() > 0) {
                    items.add(item(s.getId(), o, SettlementItem.ITEM_POINTS_COST_PLATFORM, SettlementItem.DIR_PLATFORM, cost, "平台活动积分成本（平台补贴）"));
                }
            } else {
                // 门店营销积分：抵扣与成本均计入该店
                pointsDeduct = pointsDeduct.add(pd);
                pointsCostStore = pointsCostStore.add(cost);
                if (pd.signum() > 0) {
                    items.add(item(s.getId(), o, SettlementItem.ITEM_POINTS_DEDUCT, SettlementItem.DIR_DEDUCT, pd, "门店营销积分抵扣"));
                }
                if (cost.signum() > 0) {
                    items.add(item(s.getId(), o, SettlementItem.ITEM_POINTS_COST_STORE, SettlementItem.DIR_DEDUCT, cost, "门店营销积分成本"));
                }
            }

            // 本店券成本（简化：订单券全额按本店券计，TODO 区分平台券）
            BigDecimal coupon = nz(o.getCouponAmount());
            if (coupon.signum() > 0) {
                couponCost = couponCost.add(coupon);
                items.add(item(s.getId(), o, SettlementItem.ITEM_COUPON_STORE, SettlementItem.DIR_DEDUCT, coupon, "本店券成本"));
            }
        }

        s.setTotalAmount(total);
        s.setCommissionAmount(commission);
        s.setPointsDeductAmount(pointsDeduct);
        s.setPointsCostStore(pointsCostStore);
        s.setPointsCostPlatform(pointsCostPlatform);
        s.setCouponCostStore(couponCost);
        s.setRefundAdjust(BigDecimal.ZERO);
        s.setAdjustAmount(BigDecimal.ZERO);
        s.setFinalAmount(total.subtract(commission).subtract(pointsDeduct)
                .subtract(pointsCostStore).subtract(couponCost).setScale(2, RoundingMode.HALF_UP));
        s.setAutoConfirmAt(LocalDateTime.now().plusHours(72));

        settlementMapper.insert(s);
        // 回填 settlementId 后写明细
        for (SettlementItem it : items) {
            it.setSettlementId(s.getId());
            settlementItemMapper.insert(it);
        }
        log.info("settlement generated: {} store={} period={} orders={} final={}",
                s.getSettleNo(), storeId, period, orders.size(), s.getFinalAmount());
    }

    private SettlementItem item(Long settlementId, SettlementMapper.OrderRow o, int type, int dir, BigDecimal amount, String remark) {
        SettlementItem it = new SettlementItem();
        it.setSettlementId(settlementId);
        it.setOrderId(o.getId());
        it.setOrderNo(o.getOrderNo());
        it.setItemType(type);
        it.setDirection(dir);
        it.setAmount(amount);
        it.setRemark(remark);
        return it;
    }

    /** 状态机校验并 CAS 更新（乐观锁防并发，D1） */
    private void transit(Settlement s, SettlementStatus target) {
        try {
            SettlementStatus.of(s.getStatus()).transitTo(target);
        } catch (IllegalArgumentException e) {
            throw new BizException("结算单当前状态（" + SettlementStatus.of(s.getStatus()).getDesc() + "）不允许该操作");
        }
        int updated = settlementMapper.casStatus(s.getId(), s.getStatus(), target.getCode(), s.getVersion());
        if (updated == 0) {
            throw new BizException(ResultCode.CONFLICT, "结算单状态已变化，请刷新后重试");
        }
        s.setStatus(target.getCode());
        // CAS 已将 DB version+1，同步内存对象，避免后续 updateById（@Version）因版本过期静默丢更新
        s.setVersion(s.getVersion() + 1);
    }

    private Settlement requireSettlement(Long id) {
        Settlement s = settlementMapper.selectById(id);
        if (s == null) {
            throw new BizException(ResultCode.NOT_FOUND, "结算单不存在");
        }
        return s;
    }

    /** 门店数据范围：STORE 管理员仅本店可见/操作 */
    private List<Long> resolveVisibleStoreIds() {
        UserContext ctx = UserContext.get();
        if (ctx != null && "STORE".equals(ctx.getDataScope())) {
            if (ctx.getStoreIds() != null && !ctx.getStoreIds().isEmpty()) {
                return ctx.getStoreIds();
            }
            return ctx.getStoreId() != null ? List.of(ctx.getStoreId()) : List.of();
        }
        return null; // 总部全量
    }

    private void checkStoreAccess(Long settlementStoreId) {
        List<Long> sids = resolveVisibleStoreIds();
        if (sids != null && !sids.contains(settlementStoreId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他门店的结算单");
        }
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private BigDecimal nz(java.math.BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private SettlementDetailVO toDetailVO(com.herbaltea.module.settlement.dto.SettlementPageVO r) {
        SettlementDetailVO vo = new SettlementDetailVO();
        vo.setId(r.getId());
        vo.setSettleNo(r.getSettleNo());
        vo.setStoreId(r.getStoreId());
        vo.setStoreName(r.getStoreName());
        vo.setPeriod(r.getPeriod());
        vo.setType(r.getType());
        vo.setOrderCount(r.getOrderCount());
        vo.setTotalAmount(r.getTotalAmount());
        vo.setCommissionAmount(r.getCommissionAmount());
        vo.setPointsDeductAmount(r.getPointsDeductAmount());
        vo.setPointsCostStore(r.getPointsCostStore());
        vo.setPointsCostPlatform(r.getPointsCostPlatform());
        vo.setCouponCostStore(r.getCouponCostStore());
        vo.setRefundAdjust(r.getRefundAdjust());
        vo.setAdjustAmount(r.getAdjustAmount());
        vo.setFinalAmount(r.getFinalAmount());
        vo.setConfirmStatus(r.getConfirmStatus());
        vo.setStatus(r.getStatus());
        vo.setStatusDesc(SettlementStatus.of(r.getStatus()).getDesc());
        vo.setAutoConfirmAt(r.getAutoConfirmAt());
        vo.setConfirmedAt(r.getConfirmedAt());
        vo.setDisputeNote(r.getDisputeNote());
        vo.setReviewedBy(r.getReviewedBy());
        vo.setPaidAt(r.getPaidAt());
        vo.setPayoutNo(r.getPayoutNo());
        vo.setCreatedAt(r.getCreatedAt());
        vo.setVersion(r.getVersion());
        vo.setParentSettlementId(r.getParentSettlementId());
        return vo;
    }

    private void copyToVO(Settlement s, SettlementDetailVO vo) {
        vo.setId(s.getId());
        vo.setSettleNo(s.getSettleNo());
        vo.setStoreId(s.getStoreId());
        vo.setPeriod(s.getPeriod());
        vo.setType(s.getType());
        vo.setOrderCount(s.getOrderCount());
        vo.setTotalAmount(s.getTotalAmount());
        vo.setCommissionAmount(s.getCommissionAmount());
        vo.setPointsDeductAmount(s.getPointsDeductAmount());
        vo.setPointsCostStore(s.getPointsCostStore());
        vo.setPointsCostPlatform(s.getPointsCostPlatform());
        vo.setCouponCostStore(s.getCouponCostStore());
        vo.setRefundAdjust(s.getRefundAdjust());
        vo.setAdjustAmount(s.getAdjustAmount());
        vo.setFinalAmount(s.getFinalAmount());
        vo.setConfirmStatus(s.getConfirmStatus());
        vo.setStatus(s.getStatus());
        vo.setAutoConfirmAt(s.getAutoConfirmAt());
        vo.setConfirmedAt(s.getConfirmedAt());
        vo.setDisputeNote(s.getDisputeNote());
        vo.setReviewedBy(s.getReviewedBy());
        vo.setPaidAt(s.getPaidAt());
        vo.setPayoutNo(s.getPayoutNo());
        vo.setCreatedAt(s.getCreatedAt());
        vo.setVersion(s.getVersion());
        vo.setParentSettlementId(s.getParentSettlementId());
    }
}
