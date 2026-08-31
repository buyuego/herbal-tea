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
 * products 表实体（平台商品目录层，总部维护，对齐 V1__schema.sql 权威结构）
 *
 * <p>说明：
 * <ul>
 *   <li>images 为 JSON 数组列，实体以 String 承接（{@code ["url1","url2"]}），
 *       业务层负责与 {@code List<String>} 互转</li>
 *   <li>version 乐观锁（16.2）：目录编辑走 updateById 自动 CAS，冲突返回 40900</li>
 *   <li>店铺定价（store_products）不被本表修改覆盖（D14：只标记 catalog_dirty）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("products")
public class Product extends BaseEntity {

    /** 下架 */
    public static final int STATUS_OFF = 0;
    /** 在售 */
    public static final int STATUS_ON = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类（product_categories.id） */
    private Long categoryId;

    /** 商品名（总部统一） */
    private String name;

    /** 副标题 */
    private String subtitle;

    /** 配方（总部维护） */
    private String formula;

    /** 主图（COS） */
    private String mainImage;

    /** 轮播图 JSON 数组字符串 */
    private String images;

    /** 富文本详情 */
    private String detail;

    /** 建议零售价（本店定价 80%-120% 基准） */
    private BigDecimal suggestedPrice;

    /** 成本价（敏感，超管/财务可见） */
    private BigDecimal costPrice;

    /** 0下架 / 1在售 */
    private Integer status;

    /** 乐观锁（16.2） */
    @Version
    private Integer version;
}
