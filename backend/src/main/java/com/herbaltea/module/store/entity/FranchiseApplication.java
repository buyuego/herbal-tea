package com.herbaltea.module.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * franchise_applications 表实体（加盟申请，对齐 V1__schema.sql 权威结构）
 *
 * <p>状态机：0 待审核 → 1 通过 / 2 拒绝；审核人/意见/时间由总部审批落库。
 * 同用户仅允许存在一笔待审核申请（业务幂等：idem:franchise:{user}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("franchise_applications")
public class FranchiseApplication extends BaseEntity {

    /** 状态：待审核 */
    public static final int STATUS_PENDING = 0;
    /** 状态：通过 */
    public static final int STATUS_APPROVED = 1;
    /** 状态：拒绝 */
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请人姓名 */
    private String applicantName;

    /** 联系电话 */
    private String phone;

    /** 意向区域 */
    private String intendedRegion;

    /** 从业经历 */
    private String experience;

    /** 0待审核 / 1通过 / 2拒绝 */
    private Integer status;

    /** 审核意见 */
    private String reviewNote;

    /** 审核人（admin_users.id） */
    private Long reviewedBy;

    /** 审核时间 */
    private java.time.LocalDateTime reviewedAt;
}
