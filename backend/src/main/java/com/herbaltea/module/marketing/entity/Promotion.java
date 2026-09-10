package com.herbaltea.module.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * promotions 表实体（促销活动，对齐 V1__schema.sql 权威结构）
 *
 * <p>活动类型：
 * <ul>
 *   <li>1 满减：rules {@code {"thresholdAmount":100.00,"discountAmount":15.00}} —— 小计达门槛减固定金额</li>
 *   <li>2 折扣：rules {@code {"thresholdAmount":0,"discountRate":0.88,"maxDiscount":30.00}} —— 按比例让利，可封顶</li>
 *   <li>3 限时购：rules {@code {"thresholdAmount":0,"discountAmount":20.00}} —— 限时窗口内的直减（与满减同算法，门槛通常为 0）</li>
 * </ul>
 *
 * <p>归属（成本承担方，与券口径一致）：scope=1 平台活动（平台承担，store_id 为 NULL，全部门店可命中）/
 * scope=2 本店活动（店铺承担，store_id 指定门店，仅该店可命中）。
 *
 * <p>叠加规则：同一订单<b>最多命中一个活动</b>（多活动并发时取优惠最大者），活动与券、积分可叠加，
 * 计价顺序为 活动 → 券 → 积分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("promotions")
public class Promotion extends BaseEntity {

    /** 满减 */
    public static final int TYPE_CASH = 1;
    /** 折扣 */
    public static final int TYPE_DISCOUNT = 2;
    /** 限时购 */
    public static final int TYPE_FLASH = 3;

    /** 平台活动（平台承担） */
    public static final int SCOPE_PLATFORM = 1;
    /** 本店活动（店铺承担） */
    public static final int SCOPE_STORE = 2;

    /** 草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 进行中 */
    public static final int STATUS_RUNNING = 1;
    /** 已结束 */
    public static final int STATUS_ENDED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动名 */
    private String title;

    /** 1满减 / 2折扣 / 3限时购 */
    private Integer type;

    /** 1平台 / 2本店 */
    private Integer scope;

    /** 本店活动归属门店（平台活动为 NULL） */
    private Long storeId;

    /** 活动规则 JSON（门槛/优惠/封顶） */
    private String rules;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 0草稿 / 1进行中 / 2已结束 */
    private Integer status;

    @Version
    private Integer version;
}
