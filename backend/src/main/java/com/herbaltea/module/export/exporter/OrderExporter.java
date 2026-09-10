package com.herbaltea.module.export.exporter;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.herbaltea.module.export.dto.ExportRequest;
import com.herbaltea.module.order.entity.Order;
import com.herbaltea.module.order.mapper.OrderMapper;
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
 * 订单导出（v33）
 */
@Slf4j
@Component("order")
@RequiredArgsConstructor
public class OrderExporter implements Exporter {

    private final OrderMapper orderMapper;
    private final StoreMapper storeMapper;

    @Override
    public String bizType() {
        return "order";
    }

    @Override
    public Result export(ExportRequest.Filter filter, String targetPath) {
        QueryWrapper<Order> qw = new QueryWrapper<>();
        if (filter != null) {
            if (filter.getOrderNo() != null && !filter.getOrderNo().isEmpty()) qw.like("order_no", filter.getOrderNo());
            if (filter.getStatus() != null) qw.eq("status", filter.getStatus());
            if (filter.getStoreId() != null) qw.eq("store_id", filter.getStoreId());
        }
        if (filter != null && filter.getDateFrom() != null && !filter.getDateFrom().isEmpty()) {
            qw.ge("created_at", filter.getDateFrom() + " 00:00:00");
        }
        if (filter != null && filter.getDateTo() != null && !filter.getDateTo().isEmpty()) {
            qw.le("created_at", filter.getDateTo() + " 23:59:59");
        }
        qw.orderByDesc("id");
        List<Order> orders = orderMapper.selectList(qw);

        Map<Long, String> storeNameMap = new HashMap<>();
        if (!orders.isEmpty()) {
            List<Store> ss = storeMapper.selectBatchIds(orders.stream().map(Order::getStoreId).distinct().toList());
            for (Store s : ss) storeNameMap.put(s.getId(), s.getStoreName());
        }
        List<OrderRow> rows = new ArrayList<>();
        java.time.format.DateTimeFormatter F = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Order o : orders) {
            OrderRow r = new OrderRow();
            r.id = o.getId();
            r.orderNo = o.getOrderNo();
            r.storeName = storeNameMap.get(o.getStoreId());
            r.status = o.getStatus();
            r.totalAmount = o.getTotalAmount() == null ? null : o.getTotalAmount().toPlainString();
            r.couponAmount = o.getCouponAmount() == null ? null : o.getCouponAmount().toPlainString();
            r.promotionDiscount = o.getPromotionDiscount() == null ? null : o.getPromotionDiscount().toPlainString();
            r.pointsDeduct = o.getPointsDeduct();
            r.payAmount = o.getPayAmount() == null ? null : o.getPayAmount().toPlainString();
            r.receiverName = o.getReceiverName();
            r.receiverPhone = o.getReceiverPhone();
            r.paidAt = o.getPaidAt() == null ? null : o.getPaidAt().format(F);
            r.finishedAt = o.getFinishedAt() == null ? null : o.getFinishedAt().format(F);
            r.createdAt = o.getCreatedAt() == null ? null : o.getCreatedAt().format(F);
            rows.add(r);
        }
        EasyExcel.write(targetPath, OrderRow.class).sheet("订单").doWrite(rows);
        long size = new java.io.File(targetPath).length();
        return Result.builder().rowCount(rows.size()).fileSize(size).build();
    }

    @lombok.Data
    public static class OrderRow {
        @com.alibaba.excel.annotation.ExcelProperty("订单ID") private Long id;
        @com.alibaba.excel.annotation.ExcelProperty("订单号") private String orderNo;
        @com.alibaba.excel.annotation.ExcelProperty("门店") private String storeName;
        @com.alibaba.excel.annotation.ExcelProperty("状态码(10待支付/20已支付/30待发货/40已发货/50已签收/80已退款/90已完结)") private Integer status;
        @com.alibaba.excel.annotation.ExcelProperty("总额(元)") private String totalAmount;
        @com.alibaba.excel.annotation.ExcelProperty("优惠券(元)") private String couponAmount;
        @com.alibaba.excel.annotation.ExcelProperty("活动优惠(元)") private String promotionDiscount;
        @com.alibaba.excel.annotation.ExcelProperty("积分抵扣(分)") private Long pointsDeduct;
        @com.alibaba.excel.annotation.ExcelProperty("实付(元)") private String payAmount;
        @com.alibaba.excel.annotation.ExcelProperty("收货人") private String receiverName;
        @com.alibaba.excel.annotation.ExcelProperty("收货电话") private String receiverPhone;
        @com.alibaba.excel.annotation.ExcelProperty("支付时间") private String paidAt;
        @com.alibaba.excel.annotation.ExcelProperty("完成时间") private String finishedAt;
        @com.alibaba.excel.annotation.ExcelProperty("创建时间") private String createdAt;
    }
}
