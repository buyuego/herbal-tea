package com.herbaltea.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.product.dto.StoreProductVO;
import com.herbaltea.module.product.entity.StoreProduct;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.RowBounds;

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

    // ==================== C 端货架（v29） ====================

    /**
     * 分页查询本店在售商品 id（DISTINCT，保证分页粒度为「商品」而非「SKU」）。
     *
     * <p>在售 = 本店已上架（store_products.status=1）且商品目录在售（products.status=1）
     * 且存在启用中的 SKU（product_skus.status=1）。
     */
    @Select("""
            <script>
            SELECT DISTINCT p.id
            FROM store_products sp
                     JOIN products p ON p.id = sp.product_id
                     JOIN product_skus s ON s.id = sp.sku_id
            WHERE sp.store_id = #{storeId}
              AND sp.status = 1 AND p.status = 1 AND s.status = 1
              <if test='categoryId != null'> AND p.category_id = #{categoryId} </if>
              <if test='keyword != null and keyword != ""'>
                  AND (p.name LIKE CONCAT('%', #{keyword}, '%')
                       OR s.sku_code LIKE CONCAT('%', #{keyword}, '%'))
              </if>
            ORDER BY p.id
            </script>
            """)
    List<Long> pageShelfProductIds(org.apache.ibatis.session.RowBounds rowBounds,
                                   @Param("storeId") Long storeId,
                                   @Param("categoryId") Long categoryId,
                                   @Param("keyword") String keyword);

    /**
     * 本店在售商品总数（与 pageShelfProductIds 同条件，独立 COUNT 避免拉全量 id）。
     */
    @Select("""
            <script>
            SELECT COUNT(DISTINCT p.id)
            FROM store_products sp
                     JOIN products p ON p.id = sp.product_id
                     JOIN product_skus s ON s.id = sp.sku_id
            WHERE sp.store_id = #{storeId}
              AND sp.status = 1 AND p.status = 1 AND s.status = 1
              <if test='categoryId != null'> AND p.category_id = #{categoryId} </if>
              <if test='keyword != null and keyword != ""'>
                  AND (p.name LIKE CONCAT('%', #{keyword}, '%')
                       OR s.sku_code LIKE CONCAT('%', #{keyword}, '%'))
              </if>
            </script>
            """)
    long countShelfProducts(@Param("storeId") Long storeId,
                            @Param("categoryId") Long categoryId,
                            @Param("keyword") String keyword);

    /**
     * 按商品 id 批量取本店在售 SKU 行（供 Service 聚合）。
     */
    @Select("""
            <script>
            SELECT p.id AS productId, p.name AS name, p.subtitle AS subtitle,
                   p.main_image AS mainImage, p.suggested_price AS suggestedPrice,
                   p.category_id AS categoryId,
                   s.id AS skuId, s.sku_code AS skuCode, s.specs AS specs,
                   sp.price AS price, s.stock AS stock
            FROM store_products sp
                     JOIN products p ON p.id = sp.product_id
                     JOIN product_skus s ON s.id = sp.sku_id
            WHERE sp.store_id = #{storeId}
              AND sp.status = 1 AND p.status = 1 AND s.status = 1
              AND p.id IN
              <foreach collection='productIds' item='pid' open='(' separator=',' close=')'>
                  #{pid}
              </foreach>
            ORDER BY p.id, s.id
            </script>
            """)
    List<ShelfRow> listShelfRows(@Param("storeId") Long storeId,
                                 @Param("productIds") List<Long> productIds);

    /**
     * 单个商品的货架详情行（v29：C 端商品详情页，含配方/图集/富文本详情）。
     */
    @Select("""
            SELECT p.id AS productId, p.name AS name, p.subtitle AS subtitle,
                   p.main_image AS mainImage, p.suggested_price AS suggestedPrice,
                   p.category_id AS categoryId, p.formula AS formula,
                   p.images AS images, p.detail AS detail,
                   s.id AS skuId, s.sku_code AS skuCode, s.specs AS specs,
                   sp.price AS price, s.stock AS stock
            FROM store_products sp
                     JOIN products p ON p.id = sp.product_id
                     JOIN product_skus s ON s.id = sp.sku_id
            WHERE sp.store_id = #{storeId}
              AND sp.status = 1 AND p.status = 1 AND s.status = 1
              AND p.id = #{productId}
            ORDER BY s.id
            """)
    List<ShelfRow> listShelfRowsForDetail(@Param("storeId") Long storeId,
                                          @Param("productId") Long productId);

    /**
     * 货架聚合行（Mapper 内部使用：商品 × SKU 一行一条，Service 层再按商品分组）。
     */
    @Data
    @lombok.NoArgsConstructor
    class ShelfRow {
        private Long productId;
        private String name;
        private String subtitle;
        private String mainImage;
        private java.math.BigDecimal suggestedPrice;
        private Long categoryId;
        private String formula;
        private String images;
        private String detail;
        private Long skuId;
        private String skuCode;
        private String specs;
        private java.math.BigDecimal price;
        private Integer stock;
    }
}
