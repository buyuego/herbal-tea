package com.herbaltea.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类（仅 created_at）：流水/字典/日志类表（V1 共 13 张，如 product_categories、
 * inventory_records、order_items、point_records、event_outbox 等）
 *
 * <p>这些表 DDL 无 updated_at 列，继承 {@link BaseEntity} 会生成引用不存在列的 SQL
 * （Unknown column 'updated_at'）。写入时由 MetaObjectHandler 显式填充 createdAt。
 */
@Data
public abstract class BaseCreatedOnlyEntity implements Serializable {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
