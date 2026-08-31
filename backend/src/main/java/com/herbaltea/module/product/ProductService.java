package com.herbaltea.module.product;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.product.dto.CategoryCreateRequest;
import com.herbaltea.module.product.dto.ProductCreateRequest;
import com.herbaltea.module.product.dto.ProductDetailVO;
import com.herbaltea.module.product.dto.ProductPageQuery;
import com.herbaltea.module.product.dto.ProductUpdateRequest;
import com.herbaltea.module.product.dto.SkuAddRequest;
import com.herbaltea.module.product.dto.StockAdjustRequest;
import com.herbaltea.module.product.dto.StoreListingRequest;
import com.herbaltea.module.product.dto.StoreProductVO;
import com.herbaltea.module.product.entity.InventoryRecord;
import com.herbaltea.module.product.entity.Product;
import com.herbaltea.module.product.entity.ProductCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品模块（product_categories / products / product_skus / store_products / inventory_records）
 *
 * <p>职责：
 * <ul>
 *   <li>平台商品目录（总部）：分类、商品、SKU 维护；目录变更发布
 *       {@code product_catalog_changed} 事件并标记本店 catalog_dirty（D14，不覆盖本店定价）</li>
 *   <li>库存（总部仓管）：入库/盘点调整落流水；原子扣减（16.4 ③）供订单模块调用，零超卖</li>
 *   <li>店铺上架（门店）：本店定价（建议价 80%-120% 区间校验）+ 上下架开关 + 每日配额（12.3）</li>
 * </ul>
 */
public interface ProductService {

    // ==================== 分类（总部） ====================

    /** 分类列表（启用优先，按 sort 升序） */
    List<ProductCategory> listCategories();

    /** 创建分类 */
    Long createCategory(CategoryCreateRequest req);

    /** 启停用分类 */
    void updateCategoryStatus(Long id, Integer status);

    // ==================== 平台商品目录（总部） ====================

    /** 创建平台商品（商品头 + 初始 SKU 列表，事务） */
    Long createProduct(ProductCreateRequest req);

    /** 商品详情（含 SKU 列表，images 已解析） */
    ProductDetailVO getProductDetail(Long productId);

    /** 分页查询商品目录（keyword 模糊 / 分类 / 状态） */
    IPage<Product> pageProducts(ProductPageQuery query);

    /** 更新目录（D14）：更新 products + 标记本店 catalog_dirty=1 + 发布 catalog_changed 事件 */
    void updateCatalog(Long productId, ProductUpdateRequest req, Long operatorAdminId);

    /** 上下架（目录层，0下架 / 1在售） */
    void updateProductStatus(Long productId, Integer status, Long operatorAdminId);

    /** 为商品追加 SKU */
    Long addSku(Long productId, SkuAddRequest req);

    /** SKU 启停用 */
    void updateSkuStatus(Long skuId, Integer status);

    // ==================== 库存（总部仓管） ====================

    /**
     * 库存原子扣减（16.4 ③，供订单模块下单调用）：
     * UPDATE product_skus SET stock = stock - qty, version = version + 1
     * WHERE id = ? AND stock >= qty AND status = 1
     * 影响行数 0 → 库存不足（业务唯一约束 + 乐观锁双保险，无 Redis 锁）
     */
    boolean deductStock(Long skuId, Integer qty);

    /** 回滚库存（关单/退款，同库本地事务，D10 消除） */
    void restoreStock(Long skuId, Integer qty);

    /** 库存调整（入库/盘点）：行锁 + 乐观锁 + 落流水 */
    void adjustStock(StockAdjustRequest req, Long operatorAdminId);

    /** 库存流水分页（按 SKU / 单号过滤） */
    IPage<InventoryRecord> pageInventoryRecords(Long skuId, String bizNo, long page, long size);

    // ==================== 店铺上架（门店） ====================

    /** 本店上架（选品 + 定价，价格须在建议价 80%-120%） */
    Long createStoreProduct(Long storeId, StoreListingRequest req);

    /** 本店改价（同样校验 80%-120%） */
    void updateStorePrice(Long id, BigDecimal price, Long storeId);

    /** 本店上下架开关（不被目录变更覆盖，D14） */
    void updateStoreProductStatus(Long id, Integer status, Long storeId);

    /** 本店上架列表（联查商品/SKU 展示信息，status 为 null 查全部） */
    List<StoreProductVO> listStoreProducts(Long storeId, Integer status);
}
