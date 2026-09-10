package com.herbaltea.module.report;

import com.herbaltea.module.report.dto.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表聚合服务（v31）。
 *
 * <p>统一数据隔离：超管/财务 → 查全量；店长 → 仅其绑定门店；
> 其他角色应被 menu:dashboard 拦截，本接口仍做兜底校验。
 */
public interface ReportService {

    OverviewVO overview(String period);

    List<TrendPoint> salesTrend(int days);

    List<FunnelItem> orderFunnel(int days);

    List<CategoryShareItem> categoryShare(int days);

    List<TopSkuItem> topSkus(int days, int limit);

    List<PointsTrendPoint> pointsTrend(int days);

    List<PromoStatItem> promoStats(int days);

    RefundRateVO refundRate(int days);

    /** 报表 storeIds 过滤口径：超管/财务 = null（全部）；店长 = 当前用户绑定 storeIds */
    List<Long> resolveStoreIds();

    /** 区间端点 [start, end) （闭开区间，按 LocalDateTime） */
    record DateRange(LocalDate startDate, LocalDate endDate) {}
    DateRange resolveRange(String period);
    DateRange resolveRangeByDays(int days);
}