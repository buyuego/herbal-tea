package com.herbaltea.module.product;

/**
 * 商品模块（product_categories / products / product_skus / store_products / inventory_records）
 *
 * <p>职责：平台商品目录、本店商品上下架与定价（D14 不自动覆盖本店定价）、
 * 库存原子扣减（16.4 ③，零超卖）。
 */
public interface ProductService {

    /** 创建平台商品（总部） */
    Long createProduct(Long adminId, String name, String categoryCode, String specs);

    /**
     * 库存原子扣减（16.4 ③）：
     * UPDATE product_skus SET stock = stock - #{qty}, version = version + 1
     *   WHERE sku_id = ? AND stock >= #{qty}
     * 影响行数为 0 → 库存不足（业务唯一约束 + 乐观锁双保险，无 Redis 锁）
     */
    boolean deductStock(Long skuId, Integer qty);

    /** 回滚库存（关单/退款，同库本地事务，D10 消除） */
    void restoreStock(Long skuId, Integer qty);

    /** D14：总部修改平台商品 → 发布 product.catalog_changed 事件，标记本店目录待复核 */
    void updateCatalog(Long productId, String name, Long operatorAdminId);
}
