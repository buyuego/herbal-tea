package com.herbaltea.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseCreatedOnlyEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * inventory_records 表实体（库存流水，总部仓管，对齐 V1__schema.sql 权威结构）
 *
 * <p>每次库存变动必须落流水（入库/出库/盘点调整/退款回库），
 * 出库流水由订单模块扣减时写入（biz_no=订单号），关单/退款回库同原因类型 4。
 * <p>注意：本表 DDL 仅 created_at（无 updated_at），继承 {@link BaseCreatedOnlyEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_records")
public class InventoryRecord extends BaseCreatedOnlyEntity {

    /** 入库 */
    public static final int TYPE_INBOUND = 1;
    /** 出库（订单扣减） */
    public static final int TYPE_OUTBOUND = 2;
    /** 盘点调整（正负皆可） */
    public static final int TYPE_ADJUST = 3;
    /** 退款回库 */
    public static final int TYPE_REFUND_RESTORE = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** SKU（product_skus.id） */
    private Long skuId;

    /** 1入库 / 2出库 / 3盘点调整 / 4退款回库 */
    private Integer changeType;

    /** 变动数量（正负） */
    private Integer changeQty;

    /** 变动前库存 */
    private Integer beforeStock;

    /** 变动后库存 */
    private Integer afterStock;

    /** 关联单号（订单号/退款单号） */
    private String bizNo;

    /** 操作仓管（admin_users.id） */
    private Long operatorId;

    /** 备注 */
    private String note;
}
