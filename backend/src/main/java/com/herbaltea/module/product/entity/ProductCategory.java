package com.herbaltea.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseCreatedOnlyEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * product_categories 表实体（对齐 V1__schema.sql 权威结构）
 *
 * <p>分类为平台目录层（总部维护），门店侧只读。
 * <p>注意：本表 DDL 仅 created_at（无 updated_at），继承 {@link BaseCreatedOnlyEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_categories")
public class ProductCategory extends BaseCreatedOnlyEntity {

    /** 停用 */
    public static final int STATUS_DISABLED = 0;
    /** 启用 */
    public static final int STATUS_ENABLED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名 */
    private String name;

    /** 排序（小在前） */
    private Integer sort;

    /** 0停用 / 1启用 */
    private Integer status;
}
