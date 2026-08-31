package com.herbaltea.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.product.dto.StoreProductVO;
import com.herbaltea.module.product.entity.StoreProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * store_products 数据访问（模块边界：仅 product 模块可读写）
 */
@Mapper
public interface StoreProductMapper extends BaseMapper<StoreProduct> {

    /**
     * 本店上架列表（联查商品目录与 SKU 展示信息）。
     *
     * @param storeId 门店 id
     * @param status  上架状态过滤（null = 全部）
     */
    @Select("<script>" +
            "SELECT sp.id, sp.store_id, sp.product_id, sp.sku_id, sp.price, sp.status, " +
            "       sp.catalog_dirty, sp.daily_quota, sp.version, sp.created_at, sp.updated_at, " +
            "       p.name AS product_name, p.main_image AS main_image, p.suggested_price AS suggested_price, " +
            "       s.sku_code AS sku_code, s.specs AS specs, s.stock AS stock " +
            "FROM store_products sp " +
            "JOIN products p ON p.id = sp.product_id " +
            "JOIN product_skus s ON s.id = sp.sku_id " +
            "WHERE sp.store_id = #{storeId} " +
            "<if test='status != null'> AND sp.status = #{status} </if>" +
            "ORDER BY sp.created_at DESC" +
            "</script>")
    List<StoreProductVO> listStoreProducts(@Param("storeId") Long storeId, @Param("status") Integer status);
}
