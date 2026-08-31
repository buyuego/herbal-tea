package com.herbaltea.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.product.dto.InventoryRecordVO;
import com.herbaltea.module.product.entity.InventoryRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * inventory_records 数据访问（库存流水，只写 + 分页查，不更新不删除）
 */
@Mapper
public interface InventoryRecordMapper extends BaseMapper<InventoryRecord> {

    /**
     * 库存流水分页（v25）：联商品/SKU/操作人，B 端直接展示。
     */
    @Select("""
            SELECT r.id AS id, r.sku_id AS skuId, s.sku_code AS skuCode,
                   p.name AS productName, s.specs AS specs,
                   r.change_type AS changeType, r.change_qty AS changeQty,
                   r.before_stock AS beforeStock, r.after_stock AS afterStock,
                   r.biz_no AS bizNo, r.operator_id AS operatorId,
                   u.real_name AS operatorName, r.note AS note, r.created_at AS createdAt
            FROM inventory_records r
                     LEFT JOIN product_skus s ON s.id = r.sku_id
                     LEFT JOIN products p ON p.id = s.product_id
                     LEFT JOIN admin_users u ON u.id = r.operator_id
            WHERE (#{skuId} IS NULL OR r.sku_id = #{skuId})
              AND (#{changeType} IS NULL OR r.change_type = #{changeType})
              AND (#{bizNo} IS NULL OR #{bizNo} = '' OR r.biz_no = #{bizNo})
            ORDER BY r.id DESC
            """)
    IPage<InventoryRecordVO> pageRecords(IPage<?> page,
                                         @Param("skuId") Long skuId,
                                         @Param("bizNo") String bizNo,
                                         @Param("changeType") Integer changeType);
}
