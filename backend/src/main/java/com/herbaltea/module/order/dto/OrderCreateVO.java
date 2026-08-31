package com.herbaltea.module.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 下单响应（C 端拉起收银台用）
 *
 * @param orderNo     订单号
 * @param payNo       支付单号
 * @param payAmount   实付金额（分，供收银台）
 * @param expireAt    关单时间（30 分钟倒计时）
 */
@Schema(description = "下单响应")
public record OrderCreateVO(String orderNo, String payNo, BigDecimal payAmount, LocalDateTime expireAt) {
}
