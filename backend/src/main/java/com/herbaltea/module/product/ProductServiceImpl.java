package com.herbaltea.module.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.infrastructure.outbox.OutboxEventType;
import com.herbaltea.infrastructure.outbox.OutboxPublisher;
import com.herbaltea.module.product.dto.CategoryCreateRequest;
import com.herbaltea.module.product.dto.ProductCreateRequest;
import com.herbaltea.module.product.dto.ProductDetailVO;
import com.herbaltea.module.product.dto.ProductPageQuery;
import com.herbaltea.module.product.dto.ProductUpdateRequest;
import com.herbaltea.module.product.dto.SkuAddRequest;
import com.herbaltea.module.product.dto.SkuForSaleVO;
import com.herbaltea.module.product.dto.StockAdjustRequest;
import com.herbaltea.module.product.dto.StoreListingRequest;
import com.herbaltea.module.product.dto.StoreProductVO;
import com.herbaltea.module.product.entity.InventoryRecord;
import com.herbaltea.module.product.entity.Product;
import com.herbaltea.module.product.entity.ProductCategory;
import com.herbaltea.module.product.entity.ProductSku;
import com.herbaltea.module.product.entity.StoreProduct;
import com.herbaltea.module.product.mapper.InventoryRecordMapper;
import com.herbaltea.module.product.mapper.ProductCategoryMapper;
import com.herbaltea.module.product.mapper.ProductMapper;
import com.herbaltea.module.product.mapper.ProductSkuMapper;
import com.herbaltea.module.product.mapper.StoreProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品模块实现（对齐设计 v10 §16.3 / D14 / 16.4 ③）
 *
 * <p>关键决策：
 * <ul>
 *   <li>库存扣减：单条原子 UPDATE（stock >= qty 条件 + version 乐观锁），零超卖无锁</li>
 *   <li>目录变更：不自动覆盖本店定价，仅标 catalog_dirty=1 + Outbox 事件（D14）</li>
 *   <li>本店定价：平台建议价 80%-120% 区间，越界拒绝（DDL 注释约定）</li>
 *   <li>JSON 列（products.images / product_skus.specs）：实体 String 承接，业务层与
 *       List/Map 互转（避免 MyBatis-Plus JacksonTypeHandler 的泛型/空值坑）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    /** 本店定价下限（平台建议价 × 80%） */
    private static final BigDecimal PRICE_MIN_RATIO = new BigDecimal("0.80");
    /** 本店定价上限（平台建议价 × 120%） */
    private static final BigDecimal PRICE_MAX_RATIO = new BigDecimal("1.20");

    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final StoreProductMapper storeProductMapper;
    private final InventoryRecordMapper inventoryRecordMapper;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;

    // ==================== 分类 ====================

    @Override
    public List<ProductCategory> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                .orderByDesc(ProductCategory::getStatus)
                .orderByAsc(ProductCategory::getSort));
    }

    @Override
    public Long createCategory(CategoryCreateRequest req) {
        ProductCategory category = new ProductCategory();
        category.setName(req.name());
        category.setSort(req.sort());
        category.setStatus(ProductCategory.STATUS_ENABLED);
        categoryMapper.insert(category);
        return category.getId();
    }

    @Override
    public void updateCategoryStatus(Long id, Integer status) {
        requireCategory(id);
        ProductCategory update = new ProductCategory();
        update.setId(id);
        update.setStatus(status);
        categoryMapper.updateById(update);
    }

    // ==================== 平台商品目录 ====================

    @Override
    @Transactional
    public Long createProduct(ProductCreateRequest req) {
        requireCategory(req.categoryId());

        Product product = new Product();
        product.setCategoryId(req.categoryId());
        product.setName(req.name());
        product.setSubtitle(req.subtitle());
        product.setFormula(req.formula());
        product.setMainImage(req.mainImage());
        product.setImages(toJson(req.images()));
        product.setDetail(req.detail());
        product.setSuggestedPrice(req.suggestedPrice());
        product.setCostPrice(req.costPrice());
        product.setStatus(Product.STATUS_ON);
        productMapper.insert(product);
        log.info("创建平台商品 id={} name={} operator=system", product.getId(), req.name());

        for (ProductCreateRequest.SkuDraft draft : req.skus()) {
            insertSku(product.getId(), draft.skuCode(), draft.specs(),
                    draft.price(), draft.costPrice(), draft.stock());
        }
        return product.getId();
    }

    @Override
    public ProductDetailVO getProductDetail(Long productId) {
        Product product = requireProduct(productId);
        ProductCategory category = categoryMapper.selectById(product.getCategoryId());
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, productId)
                .orderByAsc(ProductSku::getId));
        return new ProductDetailVO(
                product.getId(),
                product.getCategoryId(),
                category == null ? null : category.getName(),
                product.getName(),
                product.getSubtitle(),
                product.getFormula(),
                product.getMainImage(),
                fromJsonList(product.getImages()),
                product.getDetail(),
                product.getSuggestedPrice(),
                product.getCostPrice(),
                product.getStatus(),
                skus);
    }

    @Override
    public IPage<Product> pageProducts(ProductPageQuery query) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                // 关键词匹配（名称 OR 副标题）必须用 and() 包裹，否则 OR 优先级会吞掉后续 eq 条件
                .and(StringUtils.hasText(query.getKeyword()),
                        w -> w.like(Product::getName, query.getKeyword())
                                .or()
                                .like(Product::getSubtitle, query.getKeyword()))
                .eq(query.getCategoryId() != null, Product::getCategoryId, query.getCategoryId())
                .eq(query.getStatus() != null, Product::getStatus, query.getStatus())
                .orderByDesc(Product::getId);
        return productMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
    }

    @Override
    @Transactional
    public void updateCatalog(Long productId, ProductUpdateRequest req, Long operatorAdminId) {
        Product product = requireProduct(productId);
        product.setCategoryId(req.categoryId());
        product.setName(req.name());
        product.setSubtitle(req.subtitle());
        product.setFormula(req.formula());
        product.setMainImage(req.mainImage());
        product.setImages(toJson(req.images()));
        product.setDetail(req.detail());
        product.setSuggestedPrice(req.suggestedPrice());
        product.setCostPrice(req.costPrice());
        if (productMapper.updateById(product) == 0) {
            throw BizException.conflict("商品已被他人修改，请刷新后重试");
        }
        log.info("更新平台商品目录 id={} name={} operator={}", productId, req.name(), operatorAdminId);

        // D14：标记本店目录已更新（不覆盖本店定价，仅提示复核）+ 发布事件
        storeProductMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StoreProduct>()
                .eq(StoreProduct::getProductId, productId)
                .set(StoreProduct::getCatalogDirty, 1));

        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("name", req.name());
        outboxPublisher.publish(OutboxEventType.product_catalog_changed,
                "catalog:" + productId, payload);
    }

    @Override
    public void updateProductStatus(Long productId, Integer status, Long operatorAdminId) {
        requireProduct(productId);
        Product update = new Product();
        update.setId(productId);
        update.setStatus(status);
        productMapper.updateById(update);
        log.info("平台商品 id={} 状态置为 {} operator={}", productId, status, operatorAdminId);
    }

    @Override
    @Transactional
    public Long addSku(Long productId, SkuAddRequest req) {
        requireProduct(productId);
        return insertSku(productId, req.skuCode(), req.specs(),
                req.price(), req.costPrice(), req.stock());
    }

    @Override
    public void updateSkuStatus(Long skuId, Integer status) {
        requireSku(skuId);
        ProductSku update = new ProductSku();
        update.setId(skuId);
        update.setStatus(status);
        skuMapper.updateById(update);
    }

    // ==================== 库存 ====================

    @Override
    public boolean deductStock(Long skuId, Integer qty) {
        if (qty == null || qty <= 0) {
            throw new BizException("扣减数量必须为正整数");
        }
        return skuMapper.deductStock(skuId, qty) > 0;
    }

    @Override
    public void restoreStock(Long skuId, Integer qty) {
        if (qty == null || qty <= 0) {
            throw new BizException("回滚数量必须为正整数");
        }
        skuMapper.restoreStock(skuId, qty);
    }

    @Override
    public SkuForSaleVO getSkuForSale(Long skuId, Long storeId) {
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ResultCode.NOT_FOUND, "SKU 不存在");
        }
        if (sku.getStatus() == null || sku.getStatus() != 1) {
            throw new BizException("SKU 已下架");
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new BizException("商品已下架");
        }
        // 本店在售校验 + 本店定价（C 端始终从门店购买）
        StoreProduct sp = storeProductMapper.selectOne(new LambdaQueryWrapper<StoreProduct>()
                .eq(StoreProduct::getStoreId, storeId)
                .eq(StoreProduct::getSkuId, skuId)
                .last("LIMIT 1"));
        if (sp == null || sp.getStatus() == null || sp.getStatus() != 1) {
            throw new BizException("该门店暂未上架此商品");
        }
        return new SkuForSaleVO(sku.getId(), product.getId(), product.getName(),
                product.getMainImage(), sku.getSpecs(), sp.getPrice(), sku.getStock());
    }

    @Override
    @Transactional
    public void adjustStock(StockAdjustRequest req, Long operatorAdminId) {
        Integer changeType = req.changeType();
        if (changeType != InventoryRecord.TYPE_INBOUND && changeType != InventoryRecord.TYPE_ADJUST) {
            throw new BizException("库存调整仅支持入库(1)或盘点(3)");
        }
        int changeQty = req.changeQty();
        if (changeQty == 0) {
            throw new BizException("变动数量不能为 0");
        }

        // 行锁读取（事务内），保证 before/after 流水准确
        ProductSku sku = skuMapper.lockById(req.skuId());
        if (sku == null) {
            throw new BizException("SKU 不存在或已删除");
        }
        int before = sku.getStock();
        int after = before + changeQty;
        if (after < 0) {
            throw new BizException("库存不足：当前 " + before + "，调整 " + changeQty + " 后为负");
        }

        // 乐观锁双保险（version 校验 + 自增）
        ProductSku update = new ProductSku();
        update.setId(sku.getId());
        update.setStock(after);
        update.setVersion(sku.getVersion());
        if (skuMapper.updateById(update) == 0) {
            throw BizException.conflict("库存已被他人修改，请刷新后重试");
        }

        InventoryRecord record = new InventoryRecord();
        record.setSkuId(sku.getId());
        record.setChangeType(changeType);
        record.setChangeQty(changeQty);
        record.setBeforeStock(before);
        record.setAfterStock(after);
        record.setBizNo(req.bizNo());
        record.setOperatorId(operatorAdminId);
        record.setNote(req.note());
        inventoryRecordMapper.insert(record);
        log.info("库存调整 skuId={} type={} qty={} before={} after={} operator={}",
                sku.getId(), changeType, changeQty, before, after, operatorAdminId);
    }

    @Override
    public IPage<InventoryRecord> pageInventoryRecords(Long skuId, String bizNo, long page, long size) {
        LambdaQueryWrapper<InventoryRecord> wrapper = new LambdaQueryWrapper<InventoryRecord>()
                .eq(skuId != null, InventoryRecord::getSkuId, skuId)
                .eq(StringUtils.hasText(bizNo), InventoryRecord::getBizNo, bizNo)
                .orderByDesc(InventoryRecord::getId);
        return inventoryRecordMapper.selectPage(new Page<>(page, size), wrapper);
    }

    // ==================== 店铺上架 ====================

    @Override
    @Transactional
    public Long createStoreProduct(Long storeId, StoreListingRequest req) {
        if (storeId == null || storeId <= 0) {
            throw new BizException("总部账号不能直接上架，请使用门店账号");
        }
        ProductSku sku = requireSku(req.skuId());
        Product product = requireProduct(req.productId());
        if (!product.getId().equals(sku.getProductId())) {
            throw new BizException("SKU 不属于该商品");
        }
        checkStorePrice(req.price(), product.getSuggestedPrice());

        StoreProduct sp = new StoreProduct();
        sp.setStoreId(storeId);
        sp.setProductId(product.getId());
        sp.setSkuId(sku.getId());
        sp.setPrice(req.price());
        sp.setStatus(StoreProduct.STATUS_ON);
        sp.setCatalogDirty(0);
        sp.setDailyQuota(req.dailyQuota());
        try {
            storeProductMapper.insert(sp);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("该 SKU 本店已上架，请直接修改价格或状态");
        }
        log.info("门店 {} 上架 SKU {} 售价 {} store_product_id={}", storeId, sku.getSkuCode(), req.price(), sp.getId());
        return sp.getId();
    }

    @Override
    public void updateStorePrice(Long id, BigDecimal price, Long storeId) {
        StoreProduct sp = requireStoreProduct(id);
        if (!sp.getStoreId().equals(storeId)) {
            throw BizException.unauthorized("只能修改本店上架记录");
        }
        Product product = requireProduct(sp.getProductId());
        checkStorePrice(price, product.getSuggestedPrice());

        sp.setPrice(price);
        if (storeProductMapper.updateById(sp) == 0) {
            throw BizException.conflict("上架记录已被他人修改，请刷新后重试");
        }
        log.info("门店 {} 上架记录 {} 改价 -> {} store_id={}", storeId, id, price, storeId);
    }

    @Override
    public void updateStoreProductStatus(Long id, Integer status, Long storeId) {
        StoreProduct sp = requireStoreProduct(id);
        if (!sp.getStoreId().equals(storeId)) {
            throw BizException.unauthorized("只能操作本店上架记录");
        }
        sp.setStatus(status);
        storeProductMapper.updateById(sp);
        log.info("门店 {} 上架记录 {} 状态 -> {} ", storeId, id, status);
    }

    @Override
    public List<StoreProductVO> listStoreProducts(Long storeId, Integer status) {
        return storeProductMapper.listStoreProducts(storeId, status);
    }

    // ==================== 私有工具 ====================

    private ProductCategory requireCategory(Long id) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException("分类不存在");
        }
        return category;
    }

    private Product requireProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException("商品不存在");
        }
        return product;
    }

    private ProductSku requireSku(Long id) {
        ProductSku sku = skuMapper.selectById(id);
        if (sku == null) {
            throw new BizException("SKU 不存在");
        }
        return sku;
    }

    private StoreProduct requireStoreProduct(Long id) {
        StoreProduct sp = storeProductMapper.selectById(id);
        if (sp == null) {
            throw new BizException("上架记录不存在");
        }
        return sp;
    }

    /**
     * 插入 SKU（sku_code 全局唯一，冲突抛 40900）。
     */
    private Long insertSku(Long productId, String skuCode, Map<String, Object> specs,
                           BigDecimal price, BigDecimal costPrice, Integer stock) {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode(skuCode);
        sku.setSpecs(toJson(specs));
        sku.setPrice(price);
        sku.setCostPrice(costPrice);
        sku.setStock(stock);
        sku.setStatus(ProductSku.STATUS_ENABLED);
        try {
            skuMapper.insert(sku);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("SKU 编码 " + skuCode + " 已存在");
        }
        return sku.getId();
    }

    /**
     * 本店定价区间校验：平台建议价 80%-120%（含边界）。
     */
    private void checkStorePrice(BigDecimal price, BigDecimal suggestedPrice) {
        BigDecimal min = suggestedPrice.multiply(PRICE_MIN_RATIO);
        BigDecimal max = suggestedPrice.multiply(PRICE_MAX_RATIO);
        if (price.compareTo(min) < 0 || price.compareTo(max) > 0) {
            throw new BizException("本店售价须在平台建议价 80%-120% 区间内（" + min + " ~ " + max + "）");
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException("数据序列化失败");
        }
    }

    private List<String> fromJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("images JSON 解析失败: {}", json);
            return List.of();
        }
    }
}
