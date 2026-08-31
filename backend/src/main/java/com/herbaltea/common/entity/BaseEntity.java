package com.herbaltea.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：公共审计字段（对齐 V1__schema.sql 权威结构）
 *
 * <ul>
 *   <li>created_at / updated_at：全表统一命名（DDL DEFAULT CURRENT_TIMESTAMP，
 *       写入时由 MetaObjectHandler 显式填充，避免依赖数据库默认值导致 MyBatis-Plus
 *       insert 语句不含该列而触发 NOT NULL 约束）</li>
 * </ul>
 *
 * <p><b>注意</b>：
 * <ul>
 *   <li>V1 DDL 无 deleted 列（逻辑删除未落地），软删除语义由各表 status 字段承担，
 *       切勿在实体上使用 {@code @TableLogic}，否则生成的 SQL 会引用不存在的 deleted 列</li>
 *   <li>version 乐观锁仅 10 张写表含该列（products / product_skus / store_products / orders /
 *       settlements / coupons / promotions / refund_records / store_settlement_configs /
 *       user_points_accounts），需要乐观锁的实体自行声明 {@code @Version Integer version;}</li>
 * </ul>
 */
@Data
public abstract class BaseEntity implements Serializable {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
