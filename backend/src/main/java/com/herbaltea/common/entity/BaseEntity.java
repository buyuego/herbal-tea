package com.herbaltea.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：公共审计字段 + 乐观锁 + 逻辑删除
 *
 * <p>对齐 V1__schema.sql：全部业务表含 create_time / update_time / deleted；
 * version 乐观锁（16.2）作用于 products/product_skus/store_products/orders/settlements/coupons，
 * 这些实体通过 {@code @Version} 显式标注（此处默认提供，非全部表生效）。
 */
@Data
public abstract class BaseEntity implements Serializable {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @Version
    private Integer version;
}
