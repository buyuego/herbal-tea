package com.herbaltea.module.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseCreatedOnlyEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * point_records 表实体（积分流水，对齐 V1__schema.sql 权威结构）
 *
 * <p>D15 双维归属：source_type=1 门店营销（成本归门店）/ =2 平台活动（平台补贴），
 * 结算单按来源分行展示；change_type 决定变动语义（1发放/2抵扣/3退款回收/4过期清零/5签到）。
 * <p>注意：本表 DDL 仅 created_at（无 updated_at），继承 {@link BaseCreatedOnlyEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("point_records")
public class PointRecord extends BaseCreatedOnlyEntity {

    /** 下单发放 */
    public static final int TYPE_GRANT = 1;
    /** 下单抵扣 */
    public static final int TYPE_USE = 2;
    /** 退款回收 */
    public static final int TYPE_REFUND_RECLAIM = 3;
    /** 过期清零 */
    public static final int TYPE_EXPIRE = 4;
    /** 签到 */
    public static final int TYPE_SIGN_IN = 5;

    /** 积分来源：门店营销（成本归门店） */
    public static final int SOURCE_STORE = 1;
    /** 积分来源：平台活动（平台补贴） */
    public static final int SOURCE_PLATFORM = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 积分归属门店（平台活动积分为 NULL） */
    private Long storeId;

    private Long orderId;

    /** 关联退款单（回收） */
    private String refundNo;

    private Integer changeType;

    private Integer sourceType;

    /** 变动数量（正发放 / 负抵扣回收） */
    private Long points;

    /** 发放批次（过期回收单元，12.1） */
    private String batchNo;

    private java.time.LocalDateTime expireAt;

    /** 幂等键（订单号/退款单号/过期批次号） */
    private String bizKey;
}
