package com.herbaltea.module.store.mapper;

import com.herbaltea.module.store.dto.StoreProductReviewRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * store_products 复核写操作（D14 目录变更复核，v13）。
 *
 * <p>模块边界说明：store_products 的业务写（选品/定价/上下架）归 product 模块；
 * 但「目录变更复核」是门店端对本店商品的状态流转（确认采纳/驳回），
 * 由 store 模块自治（与 StoreStaffServiceImpl 持有 AdminUserMapper 同构），
 * 全部 UPDATE 均带 {@code store_id = #{storeId}} 条件，杜绝跨店误写。
 */
@Mapper
public interface StoreProductWriteMapper {

    /** 复核行查询（存在性 + 门店归属 + 复核状态判定） */
    @Select("""
            SELECT id, store_id, catalog_dirty, review_status
            FROM store_products
            WHERE id = #{id} AND store_id = #{storeId}
            """)
    StoreProductReviewRow selectReviewRow(@Param("id") Long id, @Param("storeId") Long storeId);

    /**
     * 确认采纳新目录：catalog_dirty 1→0 + review_status 0/2→1，清除驳回原因。
     * 仅更新属于本店的行（store_id 条件，配合应用层前置校验双保险）。
     */
    @Update("""
            UPDATE store_products
            SET catalog_dirty = 0, review_status = 1, review_note = NULL,
                reviewed_at = NOW(), reviewed_by = #{operatorId}
            WHERE id = #{id} AND store_id = #{storeId}
            """)
    int confirmReview(@Param("id") Long id, @Param("storeId") Long storeId,
                      @Param("operatorId") Long operatorId);

    /**
     * 驳回新目录：review_status 0→2 + 记录驳回原因；catalog_dirty 保持 1（仍待复核，
     * 店铺端角标不清除，总部可见未达成一致）。
     */
    @Update("""
            UPDATE store_products
            SET review_status = 2, review_note = #{note},
                reviewed_at = NOW(), reviewed_by = #{operatorId}
            WHERE id = #{id} AND store_id = #{storeId}
            """)
    int rejectReview(@Param("id") Long id, @Param("storeId") Long storeId,
                     @Param("operatorId") Long operatorId, @Param("note") String note);
}
