package com.herbaltea.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * product_skus 表实体（SKU，总部总仓，对齐 V1__schema.sql 权威结构）
 *
 * <p>库存扣减（16.4 ③）为单条原子 SQL（{@code stock >= qty} 条件 + 乐观锁 version +1），
 * 零超卖且无需 Redis 锁。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_skus")
public class ProductSku extends BaseEntity {

    /** 停用 */
    public static final int STATUS_DISABLED = 0;
    /** 启用 */
    public static final int STATUS_ENABLED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属商品（products.id） */
    private Long productId;

    /** SKU 编码（全局唯一） */
    private String skuCode;

    /** 规格矩阵 JSON 对象字符串，如 {"规格":"500ml","包装":"礼盒装"} */
    private String specs;

    /** SKU 建议价 */
    private BigDecimal price;

    /** SKU 成本价 */
    private BigDecimal costPrice;

    /** 总仓库存（总部维护，原子扣减） */
    private Integer stock;

    /** 0停用 / 1启用 */
    private Integer status;

    /** 乐观锁（16.2） */
    @Version
    private Integer version;
}
