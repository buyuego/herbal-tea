package com.herbaltea.module.report;

import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.report.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 报表服务实现（v31）。
 *
 * <p>数据隔离策略：
 * <ul>
 *   <li>超管（role=1）/ 财务（role=2）→ storeIds=null（全量）</li>
 *   <li>店长（role=4）→ storeIds = UserContext.storeIds()</li>
 *   <li>其他 → 抛 40300（前端路由已拦截，本接口兜底）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;

    /** 全局可见角色：超管 / 财务 */
    private static final Set<Long> GLOBAL_ROLES = Set.of(1L, 2L);

    @Override
    public List<Long> resolveStoreIds() {
        Long roleId = UserContext.get() == null ? null : UserContext.get().getRoleId();
        if (roleId == null) {
            throw new BizException(ResultCode.FORBIDDEN, "报表接口仅限超管/财务/店长访问");
        }
        if (GLOBAL_ROLES.contains(roleId)) {
            return null; // null = 全部
        }
        if (roleId == 4L) {
            // 店长：仅看绑定门店（storeIds 由 dataScope 拦截器注入）
            List<Long> ids = UserContext.storeIds();
            if (ids == null || ids.isEmpty()) {
                // 无绑定门店 → 返回空列表触发 storeIdsFilter 的 1=0 短路
                return List.of();
            }
            return ids;
        }
        throw new BizException(ResultCode.FORBIDDEN, "当前角色无报表权限");
    }

    @Override
    public DateRange resolveRange(String period) {
        LocalDate today = LocalDate.now();
        return switch (period == null ? "today" : period) {
            case "today"  -> new DateRange(today, today);
            case "yesterday" -> new DateRange(today.minusDays(1), today.minusDays(1));
            case "7d"     -> new DateRange(today.minusDays(6), today);
            case "30d"    -> new DateRange(today.minusDays(29), today);
            default -> throw new BizException(ResultCode.PARAM_ERROR, "不支持的区间：" + period);
        };
    }

    @Override
    public DateRange resolveRangeByDays(int days) {
        if (days < 1 || days > 60) {
            throw new BizException(ResultCode.PARAM_ERROR, "days 必须在 [1,60]，实际 " + days);
        }
        LocalDate today = LocalDate.now();
        return new DateRange(today.minusDays(days - 1L), today);
    }

    private LocalDateTime toStartOfDay(LocalDate d) { return LocalDateTime.of(d, LocalTime.MIN); }
    private LocalDateTime toStartOfNextDay(LocalDate d) { return LocalDateTime.of(d.plusDays(1), LocalTime.MIN); }

    // ============== overview ==============
    @Override
    public OverviewVO overview(String period) {
        DateRange r = resolveRange(period);
        List<Long> ids = resolveStoreIds();
        OverviewAggregate cur = reportMapper.selectOverview(ids, toStartOfDay(r.startDate()), toStartOfNextDay(r.endDate()));

        BigDecimal sales = cur == null ? BigDecimal.ZERO : Optional.ofNullable(cur.getSalesAmount()).orElse(BigDecimal.ZERO);
        long orderCount = cur == null ? 0L : Optional.ofNullable(cur.getOrderCount()).orElse(0L);
        long memberCount = cur == null ? 0L : Optional.ofNullable(cur.getMemberCount()).orElse(0L);

        BigDecimal avg = orderCount == 0 ? BigDecimal.ZERO
                : sales.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);

        OverviewVO vo = new OverviewVO();
        vo.setSalesAmount(sales);
        vo.setOrderCount(orderCount);
        vo.setAvgOrderAmount(avg);
        vo.setMemberCount(memberCount);
        vo.setPeriodLabel(labelOf(period));
        // 环比：单日 (today/yesterday) 与对侧区间对比；多日区间不计算环比
        if ("today".equals(period) || "yesterday".equals(period)) {
            DateRange prev = "today".equals(period)
                    ? new DateRange(r.startDate().minusDays(1), r.startDate().minusDays(1))
                    : new DateRange(r.startDate().minusDays(1), r.startDate().minusDays(1));
            OverviewAggregate p = reportMapper.selectOverview(ids, toStartOfDay(prev.startDate()), toStartOfNextDay(prev.endDate()));
            BigDecimal prevSales = p == null ? BigDecimal.ZERO : Optional.ofNullable(p.getSalesAmount()).orElse(BigDecimal.ZERO);
            vo.setSalesAmountChange(computeChange(sales, prevSales));
        }
        return vo;
    }

    private BigDecimal computeChange(BigDecimal cur, BigDecimal prev) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            return cur.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : null;
        }
        return cur.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private String labelOf(String period) {
        return switch (period == null ? "today" : period) {
            case "today" -> "今日";
            case "yesterday" -> "昨日";
            case "7d" -> "近 7 天";
            case "30d" -> "近 30 天";
            default -> period;
        };
    }

    // ============== sales trend ==============
    @Override
    public List<TrendPoint> salesTrend(int days) {
        DateRange r = resolveRangeByDays(days);
        List<Long> ids = resolveStoreIds();
        return reportMapper.selectSalesTrend(ids, r.startDate(), r.endDate());
    }

    // ============== order funnel ==============
    @Override
    public List<FunnelItem> orderFunnel(int days) {
        DateRange r = resolveRangeByDays(days);
        List<Long> ids = resolveStoreIds();
        List<FunnelItem> raw = reportMapper.selectOrderFunnel(ids, r.startDate(), r.endDate());
        Map<Integer, String> labels = Map.of(
                10, "待支付", 20, "已支付", 30, "待发货", 40, "已发货",
                50, "已签收", 60, "退款中", 70, "已取消", 80, "已退款", 90, "已完结");
        // 补零：所有 9 个状态都返回，前端按序渲染漏斗
        Map<Integer, Long> map = new HashMap<>();
        for (FunnelItem fi : raw) map.put(fi.getStatus(), fi.getCount());
        List<FunnelItem> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : labels.entrySet()) {
            out.add(new FunnelItem(e.getKey(), e.getValue(), map.getOrDefault(e.getKey(), 0L)));
        }
        return out;
    }

    // ============== category share ==============
    @Override
    public List<CategoryShareItem> categoryShare(int days) {
        DateRange r = resolveRangeByDays(days);
        List<Long> ids = resolveStoreIds();
        List<CategoryShareItem> raw = reportMapper.selectCategoryShare(ids, r.startDate(), r.endDate());
        BigDecimal total = raw.stream()
                .map(CategoryShareItem::getValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (CategoryShareItem it : raw) {
            if (total.compareTo(BigDecimal.ZERO) == 0) {
                it.setPercent(BigDecimal.ZERO);
            } else {
                it.setPercent(it.getValue().divide(total, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP));
            }
        }
        return raw;
    }

    // ============== top skus ==============
    @Override
    public List<TopSkuItem> topSkus(int days, int limit) {
        int lim = Math.max(1, Math.min(50, limit));
        DateRange r = resolveRangeByDays(days);
        return reportMapper.selectTopSkus(resolveStoreIds(), r.startDate(), r.endDate(), lim);
    }

    // ============== points trend ==============
    @Override
    public List<PointsTrendPoint> pointsTrend(int days) {
        DateRange r = resolveRangeByDays(days);
        return reportMapper.selectPointsTrend(resolveStoreIds(), r.startDate(), r.endDate());
    }

    // ============== promo stats ==============
    @Override
    public List<PromoStatItem> promoStats(int days) {
        DateRange r = resolveRangeByDays(days);
        return reportMapper.selectPromoStats(resolveStoreIds(), r.startDate(), r.endDate());
    }

    // ============== refund rate ==============
    @Override
    public RefundRateVO refundRate(int days) {
        DateRange r = resolveRangeByDays(days);
        RefundAggregate agg = reportMapper.selectRefundRate(resolveStoreIds(), r.startDate(), r.endDate());
        RefundRateVO vo = new RefundRateVO();
        long refund = agg == null ? 0L : Optional.ofNullable(agg.getRefundCount()).orElse(0L);
        long paid = agg == null ? 0L : Optional.ofNullable(agg.getPaidCount()).orElse(0L);
        vo.setRefundCount(refund);
        vo.setPaidCount(paid);
        vo.setRate(paid == 0 ? "0.00"
                : BigDecimal.valueOf(refund).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(paid), 2, RoundingMode.HALF_UP).toPlainString());
        return vo;
    }
}