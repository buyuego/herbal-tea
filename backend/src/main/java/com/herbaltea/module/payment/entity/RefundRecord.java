package com.herbaltea.module.payment.entity;

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
 * refund_records 表实体（退款单，对齐 V1__schema.sql 权威结构）
 *
 * <p>状态机（{@link com.herbaltea.module.payment.RefundStatus}）：
 * 10待审批 → 20审批通过 → 30退款中 → 40已退款 / 50已驳回 / 95回退失败-待人工。
 * 状态流转一律 {@code CAS(status, version)} 双条件更新（16.2 / 16.11）。
 *
 * <p>退款分支（refund_branch，申请时同事务判定）：
 * 1 未发货直退（订单 20/30）/ 2 在途拦截（40）/ 3 已签收退货（50，建退货单）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("refund_records")
public class RefundRecord extends BaseEntity {

    // ===== 状态常量（与 RefundStatus 对齐） =====
    public static final int STATUS_PENDING_APPROVAL = 10;
    public static final int STATUS_APPROVED = 20;
    public static final int STATUS_REFUNDING = 30;
    public static final int STATUS_REFUNDED = 40;
    public static final int STATUS_REJECTED = 50;
    public static final int STATUS_FALLBACK_FAILED = 95;

    /** 退款分支：未发货直退 */
    public static final int BRANCH_NOT_SHIPPED = 1;
    /** 退款分支：在途拦截 */
    public static final int BRANCH_IN_TRANSIT = 2;
    /** 退款分支：已签收退货 */
    public static final int BRANCH_RETURNED = 3;

    /** 升级标记：正常 */
    public static final int ESCALATION_NORMAL = 0;
    /** 升级标记：24h 超时升级总部 */
    public static final int ESCALATION_TIMEOUT = 1;
    /** 升级标记：风控升级总部 */
    public static final int ESCALATION_RISK = 2;

    /** 实际审批方：门店 */
    public static final int APPROVED_BY_STORE = 1;
    /** 实际审批方：总部兜底 */
    public static final int APPROVED_BY_HQ = 2;
    /** 实际审批方：超管风控 */
    public static final int APPROVED_BY_SUPER = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退款单号（uk 唯一） */
    private String refundNo;

    /** 关联订单（orders.id） */
    private Long orderId;

    /** 买家（users.id） */
    private Long userId;

    /** 退款金额（≤ 订单实付，破损部分退款时可小于原额） */
    private BigDecimal amount;

    /** 退款原因（申请时填写） */
    private String reason;

    /** 退款分支：1未发货直退/2在途拦截/3已签收退货 */
    private Integer refundBranch;

    /** 状态机（10-95，见 RefundStatus） */
    private Integer status;

    /** 升级标记：0正常/1超时升级总部/2风控升级总部 */
    private Integer escalationStatus;

    /** 24h 未审批自动升级时间 */
    private LocalDateTime autoEscalatedAt;

    /** 实际审批方：1店铺/2总部兜底/3超管风控 */
    private Integer approvedByLevel;

    /** 审批人（admin_users.id） */
    private Long approvedBy;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    /** 退款完成时间 */
    private LocalDateTime handledAt;

    /** 驳回原因（50 已驳回时填写） */
    private String rejectReason;

    /** 驳回人（admin_users.id） */
    private Long rejectedBy;

    /** 驳回时间 */
    private LocalDateTime rejectedAt;

    /** 幂等键（refund_approve / 退款原路退款） */
    private String idempotentKey;

    /** 乐观锁（CAS 状态流转） */
    @Version
    private Integer version;
}
