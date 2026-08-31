package com.herbaltea.module.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * settlements 表实体（结算单，对齐 V1__schema.sql 权威结构）
 *
 * <p>状态机见 {@link com.herbaltea.module.settlement.SettlementStatus}：
 * 10待确认 → 20平台审核 → 30已结算 → 40已打款 / 90已冲正；
 * 一律 {@code CAS(status, version)} 双条件更新（审核/打款防并发，D1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("settlements")
public class Settlement extends BaseEntity {

    /** 结算单类型：日结 T+1 */
    public static final int TYPE_DAILY = 1;
    /** 结算单类型：周结 */
    public static final int TYPE_WEEKLY = 2;
    /** 结算单类型：调整单（申诉复核，v8） */
    public static final int TYPE_ADJUST = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 结算单号 */
    private String settleNo;

    /** 门店（stores.id） */
    private Long storeId;

    /** 结算周期（日结=2026-08-30，周结=2026-W35） */
    private String period;

    /** 1日结 / 2周结 / 3调整单 */
    private Integer type;

    /** 订单数 */
    private Integer orderCount;

    /** 销售总额 */
    private BigDecimal totalAmount;

    /** 平台佣金 */
    private BigDecimal commissionAmount;

    /** 门店营销积分抵扣（从店铺结算扣减） */
    private BigDecimal pointsDeductAmount;

    /** 门店营销积分成本（计入该店） */
    private BigDecimal pointsCostStore;

    /** 平台活动积分成本（平台补贴，不从店铺扣减） */
    private BigDecimal pointsCostPlatform;

    /** 本店券成本 */
    private BigDecimal couponCostStore;

    /** 退款冲正 */
    private BigDecimal refundAdjust;

    /** 调整单（申诉复核） */
    private BigDecimal adjustAmount;

    /** 实际到账 = 总额-佣金-积分抵扣-积分成本-本店券-冲正+调整 */
    private BigDecimal finalAmount;

    /** 0待确认 / 1自动确认 / 2人工确认 / 3有异议 */
    private Integer confirmStatus;

    /** 10待确认 / 20平台审核 / 30已结算 / 40已打款 / 90已冲正 */
    private Integer status;

    /** 自动确认时间（生成后 72h） */
    private LocalDateTime autoConfirmAt;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 异议申诉说明（事后申诉，v8） */
    private String disputeNote;

    /** 财务审核人（admin_users.id） */
    private Long reviewedBy;

    /** 打款时间 */
    private LocalDateTime paidAt;

    /** 打款流水号（幂等键组成部分） */
    private String payoutNo;

    /** 乐观锁（审核/打款防并发） */
    @Version
    private Integer version;
}
