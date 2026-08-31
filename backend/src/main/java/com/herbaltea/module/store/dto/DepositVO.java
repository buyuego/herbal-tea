package com.herbaltea.module.store.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 加盟保证金流水 VO（franchise_deposits 联查 stores 展示门店信息）
 *
 * <p>type=1 缴纳 / 2 退还；status=0 待处理 / 1 完成。
 */
@Data
public class DepositVO {

    /** franchise_deposits.id */
    private Long id;

    /** 门店（stores.id） */
    private Long storeId;

    /** 联查：门店编号 */
    private String storeNo;

    /** 联查：门店名称 */
    private String storeName;

    /** 1缴纳 / 2退还 */
    private Integer type;

    /** 金额 */
    private BigDecimal amount;

    /** 0待处理 / 1完成 */
    private Integer status;

    /** 关联单号（缴纳 FR-{申请}；退还同号关联） */
    private String bizNo;

    /** 缴纳时间（财务确认收款时落库） */
    private LocalDateTime paidAt;

    /** 退还时间（财务确认退还时落库） */
    private LocalDateTime refundedAt;

    /** 流水创建时间 */
    private LocalDateTime createdAt;
}
