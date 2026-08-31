package com.herbaltea.module.store.mapper;

import com.herbaltea.module.store.dto.PendingCatalogReviewVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * store_products 只读联查（D14 目录变更复核）。
 *
 * <p>模块边界：store_products 的写操作归 product 模块（ProductService），
 * 本 Mapper 仅提供 store 模块所需的只读查询，不持有写能力。
 */
@Mapper
public interface StoreProductReadMapper {

    /**
     * 本店 catalog_dirty=1 的商品列表（D14：目录已更新，店铺端角标提示复核）。
     *
     * @param storeId 门店 id
     */
    @Select("""
            SELECT sp.id AS store_product_id, sp.store_id, sp.product_id, sp.sku_id,
                   sp.price AS store_price, sp.catalog_dirty,
                   sp.review_status, sp.review_note, sp.reviewed_at, sp.reviewed_by,
                   sp.updated_at,
                   p.name AS product_name,
                   s.sku_code AS sku_code, s.specs AS sku_specs
            FROM store_products sp
            JOIN products p ON p.id = sp.product_id
            JOIN product_skus s ON s.id = sp.sku_id
            WHERE sp.store_id = #{storeId} AND sp.catalog_dirty = 1
            ORDER BY sp.updated_at DESC
            """)
    List<PendingCatalogReviewVO> listPendingCatalogReview(@Param("storeId") Long storeId);
}
