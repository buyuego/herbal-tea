package com.herbaltea.module.marketing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券模板新建 / 编辑请求（v28）
 *
 * <p>折扣券（type=2）须在 rules 中提供 discountRate，如 {@code {"discountRate":0.85,"maxDiscount":20.00}}；
 * 满减券（type=1）用 discountAmount。
 */
@Data
public class CouponSaveRequest {

    @NotBlank(message = "券名不能为空")
    private String name;

    /** 1满减券 / 2折扣券 */
    @NotNull(message = "券类型不能为空")
    private Integer type;

    /** 1平台券 / 2本店券 */
    @NotNull(message = "券归属不能为空")
    private Integer scope;

    /** 本店券必填门店 id；平台券须为空 */
    private Long storeId;

    /** 使用门槛（0 = 无门槛） */
    @DecimalMin(value = "0.00", message = "门槛不能为负")
    private BigDecimal thresholdAmount;

    /** 优惠金额（满减券必填且 > 0） */
    private BigDecimal discountAmount;

    /** 扩展规则 JSON 字符串（折扣券必填 discountRate） */
    private String rules;

    /** 发行总量（> 0） */
    @NotNull(message = "发行总量不能为空")
    private Integer totalCount;

    /** 每人限领（>= 1） */
    @NotNull(message = "每人限领不能为空")
    private Integer perUserLimit;

    @NotNull(message = "生效时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "失效时间不能为空")
    private LocalDateTime endTime;
}
