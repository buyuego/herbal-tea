package com.herbaltea.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.product.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * product_skus 数据访问
 *
 * <p>库存扣减（16.4 ③）：单条原子 UPDATE，{@code stock >= qty} 条件保证零超卖，
 * version +1 乐观锁双保险，无 Redis 锁。
 */
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    /**
     * 原子扣减库存：{@code stock = stock - qty}，仅当 {@code stock >= qty} 且 SKU 启用。
     *
     * @return 影响行数（0 = 库存不足或 SKU 停用/不存在）
     */
    @Update("UPDATE product_skus SET stock = stock - #{qty}, version = version + 1 " +
            "WHERE id = #{skuId} AND stock >= #{qty} AND status = 1")
    int deductStock(@Param("skuId") Long skuId, @Param("qty") Integer qty);

    /**
     * 回滚库存（关单/退款，与订单同库本地事务，D10 消除分布式事务）。
     */
    @Update("UPDATE product_skus SET stock = stock + #{qty}, version = version + 1 WHERE id = #{skuId}")
    int restoreStock(@Param("skuId") Long skuId, @Param("qty") Integer qty);

    /**
     * 行锁读取（盘点/入库调整用，须在事务内）：SELECT ... FOR UPDATE。
     */
    @Select("SELECT * FROM product_skus WHERE id = #{skuId} FOR UPDATE")
    ProductSku lockById(@Param("skuId") Long skuId);
}
