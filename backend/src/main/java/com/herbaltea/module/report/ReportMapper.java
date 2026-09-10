package com.herbaltea.module.report;

import com.herbaltea.module.report.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表聚合查询（v31）。
 *
 * <p>所有方法均接受 {@code storeIds} 参数：null 表示查全部数据（超管/财务）；
 * 非空表示按门店 id IN (...) 过滤（店长）。
 *
 * <p>口径约定：销售额/订单统计限定 status ∈ {20,30,40,50,90}（已支付及之后，排除未支付/退款中/已取消/已退款）。
 * 见 {@code OrderStatus} 常量。
 */
@Mapper
public interface ReportMapper {

    // ============== overview ==============
    OverviewAggregate selectOverview(@Param("storeIds") List<Long> storeIds,
                                    @Param("startAt") java.time.LocalDateTime startAt,
                                    @Param("endAt") java.time.LocalDateTime endAt);

    // ============== sales trend（按天） ==============
    List<TrendPoint> selectSalesTrend(@Param("storeIds") List<Long> storeIds,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    // ============== order funnel（按 status） ==============
    List<FunnelItem> selectOrderFunnel(@Param("storeIds") List<Long> storeIds,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    // ============== category share ==============
    List<CategoryShareItem> selectCategoryShare(@Param("storeIds") List<Long> storeIds,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    // ============== top skus ==============
    List<TopSkuItem> selectTopSkus(@Param("storeIds") List<Long> storeIds,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate,
                                   @Param("limit") int limit);

    // ============== points trend ==============
    List<PointsTrendPoint> selectPointsTrend(@Param("storeIds") List<Long> storeIds,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    // ============== promo stats ==============
    List<PromoStatItem> selectPromoStats(@Param("storeIds") List<Long> storeIds,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    // ============== refund rate ==============
    RefundAggregate selectRefundRate(@Param("storeIds") List<Long> storeIds,
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate);

    /** 概览聚合行（销售额、订单数、会员数） */
    OverviewAggregateRow selectOverviewRow(@Param("storeIds") List<Long> storeIds,
                                           @Param("startAt") java.time.LocalDateTime startAt,
                                           @Param("endAt") java.time.LocalDateTime endAt);
}