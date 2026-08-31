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
 * store_products 表实体（店铺上架层，对齐 V1__schema.sql 权威结构）
 *
 * <p>语义：
 * <ul>
 *   <li>店铺定价到 SKU 粒度，价格须在平台建议价 80%-120% 区间（越界拒绝）</li>
 *   <li>status 为本店上下架开关，不被目录变更覆盖（D14）</li>
 *   <li>catalog_dirty：1=目录已更新，店铺端角标提示复核（D14 事件标记）</li>
 *   <li>daily_quota：本店可售配额/日（可选，NULL=不限，12.3）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store_products")
public class StoreProduct extends BaseEntity {

    /** 下架 */
    public static final int STATUS_OFF = 0;
    /** 上架 */
    public static final int STATUS_ON = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 门店（stores.id） */
    private Long storeId;

    /** 平台商品（products.id） */
    private Long productId;

    /** SKU（product_skus.id，店铺定价到 SKU 粒度） */
    private Long skuId;

    /** 本店售价（校验：平台建议价 80%-120%） */
    private BigDecimal price;

    /** 0下架 / 1上架 */
    private Integer status;

    /** 1=目录已更新（D14 标记，店铺端角标提示复核） */
    private Integer catalogDirty;

    /** 本店可售配额/日（NULL=不限，12.3） */
    private Integer dailyQuota;

    /** 乐观锁（16.2） */
    @Version
    private Integer version;
}
