package com.herbaltea.module.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * settlement_items 表实体（结算单明细，按积分来源分行，D15）
 *
 * <p>item_type：1订单销售额 / 2平台佣金 / 3门店营销积分抵扣 / 4门店营销积分成本 /
 * 5平台补贴积分（平台承担）/ 6本店券成本 / 7退款冲正 / 8调整单。
 * direction：1店铺加项 / 2店铺减项 / 3平台承担项。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("settlement_items")
public class SettlementItem extends BaseEntity {

    /**
     * settlement_items 表无 updated_at 列（V1 DDL 仅有 created_at），
     * 覆盖父类字段并标记 exist=false，避免 MyBatis-Plus 生成含该列的 INSERT 报
     * Unknown column 'updated_at'。
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.time.LocalDateTime updatedAt;

    /** 明细类型：订单销售额 */
    public static final int ITEM_SALES = 1;
    /** 明细类型：平台佣金 */
    public static final int ITEM_COMMISSION = 2;
    /** 明细类型：门店营销积分抵扣 */
    public static final int ITEM_POINTS_DEDUCT = 3;
    /** 明细类型：门店营销积分成本 */
    public static final int ITEM_POINTS_COST_STORE = 4;
    /** 明细类型：平台补贴积分（平台承担） */
    public static final int ITEM_POINTS_COST_PLATFORM = 5;
    /** 明细类型：本店券成本 */
    public static final int ITEM_COUPON_STORE = 6;
    /** 明细类型：退款冲正 */
    public static final int ITEM_REFUND_ADJUST = 7;
    /** 明细类型：调整单 */
    public static final int ITEM_ADJUST = 8;

    /** 方向：店铺加项 */
    public static final int DIR_INCOME = 1;
    /** 方向：店铺减项 */
    public static final int DIR_DEDUCT = 2;
    /** 方向：平台承担项 */
    public static final int DIR_PLATFORM = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 结算单（settlements.id） */
    private Long settlementId;

    /** 关联订单（明细类必填） */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 1订单销售额 / 2平台佣金 / ... / 8调整单 */
    private Integer itemType;

    /** 1店铺加项 / 2店铺减项 / 3平台承担项 */
    private Integer direction;

    /** 金额 */
    private BigDecimal amount;

    /** 分行说明（11.2：门店营销积分行 vs 平台补贴行，一目了然） */
    private String remark;
}
