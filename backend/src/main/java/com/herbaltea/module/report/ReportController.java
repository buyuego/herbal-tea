package com.herbaltea.module.report;

import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.module.report.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报表接口（v31）。
 *
 * <p>所有端点统一权限 {@code menu:dashboard}；数据隔离由 {@link ReportServiceImpl#resolveStoreIds()} 决定。
 */
@Tag(name = "数据看板", description = "聚合指标/趋势/漏斗/占比/排行（超管+财务全量，店长仅本店）")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "概览卡（今日/昨日/7天/30天）")
    @GetMapping("/overview")
    @RequirePermission("menu:dashboard")
    public Result<OverviewVO> overview(@RequestParam(defaultValue = "today") String period) {
        return Result.ok(reportService.overview(period));
    }

    @Operation(summary = "销售曲线（按天，区间天数 1-60）")
    @GetMapping("/sales-trend")
    @RequirePermission("menu:dashboard")
    public Result<List<TrendPoint>> salesTrend(@RequestParam(defaultValue = "14") int days) {
        return Result.ok(reportService.salesTrend(days));
    }

    @Operation(summary = "订单状态漏斗（9 个状态）")
    @GetMapping("/order-funnel")
    @RequirePermission("menu:dashboard")
    public Result<List<FunnelItem>> orderFunnel(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(reportService.orderFunnel(days));
    }

    @Operation(summary = "品类销售占比（TOP10）")
    @GetMapping("/category-share")
    @RequirePermission("menu:dashboard")
    public Result<List<CategoryShareItem>> categoryShare(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(reportService.categoryShare(days));
    }

    @Operation(summary = "TOP SKU 销量榜")
    @GetMapping("/top-skus")
    @RequirePermission("menu:dashboard")
    public Result<List<TopSkuItem>> topSkus(@RequestParam(defaultValue = "30") int days,
                                            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(reportService.topSkus(days, limit));
    }

    @Operation(summary = "积分发放/使用趋势（双柱）")
    @GetMapping("/points-trend")
    @RequirePermission("menu:dashboard")
    public Result<List<PointsTrendPoint>> pointsTrend(@RequestParam(defaultValue = "14") int days) {
        return Result.ok(reportService.pointsTrend(days));
    }

    @Operation(summary = "活动命中统计（按活动）")
    @GetMapping("/promo-stats")
    @RequirePermission("menu:dashboard")
    public Result<List<PromoStatItem>> promoStats(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(reportService.promoStats(days));
    }

    @Operation(summary = "退款率（已退/已付）")
    @GetMapping("/refund-rate")
    @RequirePermission("menu:dashboard")
    public Result<RefundRateVO> refundRate(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(reportService.refundRate(days));
    }
}