package com.herbaltea.module.marketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 促销活动新建 / 编辑请求（v30）
 *
 * <p>rules 为 JSON 字符串，按活动类型约定：
 * <ul>
 *   <li>type=1 满减：{@code {"thresholdAmount":100.00,"discountAmount":15.00}}</li>
 *   <li>type=2 折扣：{@code {"thresholdAmount":0,"discountRate":0.88,"maxDiscount":30.00}}（discountRate 必填，区间 (0,1)）</li>
 *   <li>type=3 限时购：{@code {"thresholdAmount":0,"discountAmount":20.00}}</li>
 * </ul>
 */
@Data
public class PromotionSaveRequest {

    @NotBlank(message = "活动名称不能为空")
    private String title;

    /** 1满减 / 2折扣 / 3限时购 */
    @NotNull(message = "活动类型不能为空")
    private Integer type;

    /** 1平台活动（平台承担） / 2本店活动（店铺承担） */
    @NotNull(message = "活动归属不能为空")
    private Integer scope;

    /** 本店活动必填门店 id；平台活动须为空 */
    private Long storeId;

    /** 活动规则 JSON 字符串 */
    @NotBlank(message = "活动规则不能为空")
    private String rules;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
}
