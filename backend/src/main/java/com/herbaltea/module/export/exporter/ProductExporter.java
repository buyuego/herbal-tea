package com.herbaltea.module.export.exporter;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.herbaltea.module.export.dto.ExportRequest;
import com.herbaltea.module.product.entity.Product;
import com.herbaltea.module.product.entity.ProductSku;
import com.herbaltea.module.product.mapper.ProductMapper;
import com.herbaltea.module.product.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品导出（v33）：导出 SKU + 关联商品名/分类/价格 + 库存/预警
 *
 * <p>列：SKU ID/SKU编码/规格/商品名称/分类/价格/成本价/库存/预警阈值/状态/创建时间
 */
@Slf4j
@Component("product")
@RequiredArgsConstructor
public class ProductExporter implements Exporter {

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    @Override
    public String bizType() {
        return "product";
    }

    @Override
    public Result export(ExportRequest.Filter filter, String targetPath) {
        QueryWrapper<ProductSku> qw = new QueryWrapper<>();
        if (filter != null && filter.getName() != null && !filter.getName().isEmpty()) {
            // 通过 product.name like 过滤（先查 product ids 再 in 过滤 sku）
            QueryWrapper<Product> pq = new QueryWrapper<>();
            pq.like("name", filter.getName());
            if (filter.getCategoryId() != null) pq.eq("category_id", filter.getCategoryId());
            if (filter.getOnline() != null) pq.eq("status", filter.getOnline() ? 1 : 0);
            List<Product> ps = productMapper.selectList(pq);
            if (ps.isEmpty()) {
                EasyExcel.write(targetPath, ProductRow.class).sheet("商品").doWrite(new ArrayList<>());
                return Result.builder().rowCount(0).fileSize(new java.io.File(targetPath).length()).build();
            }
            List<Long> pids = ps.stream().map(Product::getId).toList();
            qw.in("product_id", pids);
        } else {
            if (filter != null) {
                if (filter.getCategoryId() != null || filter.getOnline() != null) {
                    QueryWrapper<Product> pq = new QueryWrapper<>();
                    if (filter.getCategoryId() != null) pq.eq("category_id", filter.getCategoryId());
                    if (filter.getOnline() != null) pq.eq("status", filter.getOnline() ? 1 : 0);
                    List<Product> ps = productMapper.selectList(pq);
                    if (ps.isEmpty()) {
                        EasyExcel.write(targetPath, ProductRow.class).sheet("商品").doWrite(new ArrayList<>());
                        return Result.builder().rowCount(0).fileSize(new java.io.File(targetPath).length()).build();
                    }
                    qw.in("product_id", ps.stream().map(Product::getId).toList());
                }
            }
        }
        qw.orderByAsc("id");
        List<ProductSku> skus = productSkuMapper.selectList(qw);

        // 批量补商品信息（N+1 避免：商品通常 ≤ 50, ≤ 500 SKU 的场景下影响可忽略）
        Map<Long, Product> productMap = new HashMap<>();
        if (!skus.isEmpty()) {
            List<Product> ps = productMapper.selectBatchIds(skus.stream().map(ProductSku::getProductId).distinct().toList());
            for (Product p : ps) productMap.put(p.getId(), p);
        }

        List<ProductRow> rows = new ArrayList<>();
        for (ProductSku s : skus) {
            Product p = productMap.get(s.getProductId());
            ProductRow r = new ProductRow();
            r.skuId = s.getId();
            r.skuCode = s.getSkuCode();
            r.specs = s.getSpecs();
            r.productName = p == null ? null : p.getName();
            r.categoryId = p == null ? null : p.getCategoryId();
            r.price = s.getPrice() == null ? null : s.getPrice().toPlainString();
            r.costPrice = s.getCostPrice() == null ? null : s.getCostPrice().toPlainString();
            r.stock = s.getStock();
            r.alertStock = s.getAlertStock();
            r.online = s.getStatus() != null && s.getStatus() == 1;
            r.createdAt = s.getCreatedAt() == null ? null
                    : s.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            rows.add(r);
        }
        EasyExcel.write(targetPath, ProductRow.class).sheet("商品").doWrite(rows);
        long size = new java.io.File(targetPath).length();
        return Result.builder().rowCount(rows.size()).fileSize(size).build();
    }

    /** EasyExcel 行模型 */
    @lombok.Data
    public static class ProductRow {
        @com.alibaba.excel.annotation.ExcelProperty("SKU ID")
        private Long skuId;
        @com.alibaba.excel.annotation.ExcelProperty("SKU编码")
        private String skuCode;
        @com.alibaba.excel.annotation.ExcelProperty("规格")
        private String specs;
        @com.alibaba.excel.annotation.ExcelProperty("商品名称")
        private String productName;
        @com.alibaba.excel.annotation.ExcelProperty("分类ID")
        private Long categoryId;
        @com.alibaba.excel.annotation.ExcelProperty("价格(元)")
        private String price;
        @com.alibaba.excel.annotation.ExcelProperty("成本价(元)")
        private String costPrice;
        @com.alibaba.excel.annotation.ExcelProperty("库存")
        private Integer stock;
        @com.alibaba.excel.annotation.ExcelProperty("预警阈值")
        private Integer alertStock;
        @com.alibaba.excel.annotation.ExcelProperty("在售")
        private Boolean online;
        @com.alibaba.excel.annotation.ExcelProperty("创建时间")
        private String createdAt;
    }
}
