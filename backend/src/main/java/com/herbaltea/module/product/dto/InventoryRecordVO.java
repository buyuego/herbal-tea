package com.herbaltea.module.product.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水行（v25）：inventory_records 联商品/SKU/操作人，便于 B 端直接展示
 */
@Data
public class InventoryRecordVO {

    private Long id;

    /** SKU id */
    private Long skuId;

    /** SKU 编码 */
    private String skuCode;

    /** 商品名 */
    private String productName;

    /** 规格 JSON 字符串 */
    private String specs;

    /** 1入库 / 2出库 / 3盘点调整 / 4退款回库 */
    private Integer changeType;

    /** 变动数量（正负） */
    private Integer changeQty;

    /** 变动前库存 */
    private Integer beforeStock;

    /** 变动后库存 */
    private Integer afterStock;

    /** 关联单号 */
    private String bizNo;

    /** 操作人 admin_id */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 备注 */
    private String note;

    private LocalDateTime createdAt;
}
