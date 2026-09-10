package com.herbaltea.module.export.exporter;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.herbaltea.module.export.dto.ExportRequest;
import com.herbaltea.module.settlement.entity.Settlement;
import com.herbaltea.module.settlement.mapper.SettlementMapper;
import com.herbaltea.module.store.entity.Store;
import com.herbaltea.module.store.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 结算单导出（v33）
 *
 * <p>每行一条结算单，列出成本/佣金/活动/券/积分各分项，便于财务对账。
 */
@Slf4j
@Component("settlement")
@RequiredArgsConstructor
public class SettlementExporter implements Exporter {

    private final SettlementMapper settlementMapper;
    private final StoreMapper storeMapper;

    @Override
    public String bizType() {
        return "settlement";
    }

    @Override
    public Result export(ExportRequest.Filter filter, String targetPath) {
        QueryWrapper<Settlement> qw = new QueryWrapper<>();
        if (filter != null) {
            if (filter.getSettleNo() != null && !filter.getSettleNo().isEmpty()) qw.like("settle_no", filter.getSettleNo());
            if (filter.getStoreId() != null) qw.eq("store_id", filter.getStoreId());
            if (filter.getStatus() != null) qw.eq("status", filter.getStatus());
            if (filter.getPeriod() != null && !filter.getPeriod().isEmpty()) qw.eq("period", filter.getPeriod());
        }
        if (filter != null && filter.getDateFrom() != null && !filter.getDateFrom().isEmpty()) {
            qw.ge("created_at", filter.getDateFrom() + " 00:00:00");
        }
        if (filter != null && filter.getDateTo() != null && !filter.getDateTo().isEmpty()) {
            qw.le("created_at", filter.getDateTo() + " 23:59:59");
        }
        qw.orderByDesc("id");
        List<Settlement> list = settlementMapper.selectList(qw);

        Map<Long, String> storeNameMap = new HashMap<>();
        if (!list.isEmpty()) {
            List<Store> ss = storeMapper.selectBatchIds(list.stream().map(Settlement::getStoreId).distinct().toList());
            for (Store s : ss) storeNameMap.put(s.getId(), s.getStoreName());
        }

        List<SettlementRow> rows = new ArrayList<>();
        java.time.format.DateTimeFormatter F = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Settlement s : list) {
            SettlementRow r = new SettlementRow();
            r.id = s.getId();
            r.settleNo = s.getSettleNo();
            r.storeName = storeNameMap.get(s.getStoreId());
            r.period = s.getPeriod();
            r.type = s.getType();
            r.orderCount = s.getOrderCount();
            r.totalAmount = s.getTotalAmount() == null ? null : s.getTotalAmount().toPlainString();
            r.commissionAmount = s.getCommissionAmount() == null ? null : s.getCommissionAmount().toPlainString();
            r.pointsDeductAmount = s.getPointsDeductAmount() == null ? null : s.getPointsDeductAmount().toPlainString();
            r.pointsCostStore = s.getPointsCostStore() == null ? null : s.getPointsCostStore().toPlainString();
            r.couponCostStore = s.getCouponCostStore() == null ? null : s.getCouponCostStore().toPlainString();
            r.couponCostPlatform = s.getCouponCostPlatform() == null ? null : s.getCouponCostPlatform().toPlainString();
            r.promotionCostStore = s.getPromotionCostStore() == null ? null : s.getPromotionCostStore().toPlainString();
            r.promotionCostPlatform = s.getPromotionCostPlatform() == null ? null : s.getPromotionCostPlatform().toPlainString();
            r.refundAdjust = s.getRefundAdjust() == null ? null : s.getRefundAdjust().toPlainString();
            r.finalAmount = s.getFinalAmount() == null ? null : s.getFinalAmount().toPlainString();
            r.status = s.getStatus();
            r.statusText = statusText(s.getStatus());
            r.confirmedAt = s.getConfirmedAt() == null ? null : s.getConfirmedAt().format(F);
            r.paidAt = s.getPaidAt() == null ? null : s.getPaidAt().format(F);
            r.createdAt = s.getCreatedAt() == null ? null : s.getCreatedAt().format(F);
            rows.add(r);
        }
        EasyExcel.write(targetPath, SettlementRow.class).sheet("结算").doWrite(rows);
        long size = new java.io.File(targetPath).length();
        return Result.builder().rowCount(rows.size()).fileSize(size).build();
    }

    private static String statusText(Integer s) {
        if (s == null) return null;
        return switch (s) {
            case 10 -> "待确认";
            case 20 -> "已审核";
            case 30 -> "已结算";
            case 40 -> "已打款";
            case 90 -> "已冲正";
            default -> "未知";
        };
    }

    @lombok.Data
    public static class SettlementRow {
        @com.alibaba.excel.annotation.ExcelProperty("结算ID") private Long id;
        @com.alibaba.excel.annotation.ExcelProperty("结算单号") private String settleNo;
        @com.alibaba.excel.annotation.ExcelProperty("门店") private String storeName;
        @com.alibaba.excel.annotation.ExcelProperty("账期(YYYYMM)") private String period;
        @com.alibaba.excel.annotation.ExcelProperty("类型") private Integer type;
        @com.alibaba.excel.annotation.ExcelProperty("订单数") private Integer orderCount;
        @com.alibaba.excel.annotation.ExcelProperty("订单总额(元)") private String totalAmount;
        @com.alibaba.excel.annotation.ExcelProperty("佣金(元)") private String commissionAmount;
        @com.alibaba.excel.annotation.ExcelProperty("积分抵扣(元)") private String pointsDeductAmount;
        @com.alibaba.excel.annotation.ExcelProperty("积分成本-门店(元)") private String pointsCostStore;
        @com.alibaba.excel.annotation.ExcelProperty("券成本-门店(元)") private String couponCostStore;
        @com.alibaba.excel.annotation.ExcelProperty("券成本-平台(元)") private String couponCostPlatform;
        @com.alibaba.excel.annotation.ExcelProperty("活动成本-门店(元)") private String promotionCostStore;
        @com.alibaba.excel.annotation.ExcelProperty("活动成本-平台(元)") private String promotionCostPlatform;
        @com.alibaba.excel.annotation.ExcelProperty("退款冲正(元)") private String refundAdjust;
        @com.alibaba.excel.annotation.ExcelProperty("最终结算(元)") private String finalAmount;
        @com.alibaba.excel.annotation.ExcelProperty("状态(10/20/30/40/90)") private Integer status;
        @com.alibaba.excel.annotation.ExcelProperty("状态") private String statusText;
        @com.alibaba.excel.annotation.ExcelProperty("确认时间") private String confirmedAt;
        @com.alibaba.excel.annotation.ExcelProperty("打款时间") private String paidAt;
        @com.alibaba.excel.annotation.ExcelProperty("创建时间") private String createdAt;
    }
}
