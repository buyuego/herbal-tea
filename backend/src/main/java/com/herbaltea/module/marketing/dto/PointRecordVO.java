package com.herbaltea.module.marketing.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水视图（v26：会员详情 / 积分明细展示用）
 *
 * <p>changeTypeDesc 与 sourceTypeDesc 由 Service 端按常量表填充，前端无需再映射。
 */
@Data
public class PointRecordVO {

    private Long id;

    private Long userId;

    private Long storeId;

    /** 归属门店名（平台活动积分为 NULL） */
    private String storeName;

    private Long orderId;

    /** 关联订单号 */
    private String orderNo;

    /** 1发放 / 2抵扣 / 3退款回收 / 4过期清零 / 5签到 */
    private Integer changeType;

    private String changeTypeDesc;

    /** 1门店营销 / 2平台活动（D15 归属） */
    private Integer sourceType;

    private String sourceTypeDesc;

    /** 变动积分（正发放 / 负抵扣回收） */
    private Long points;

    private String bizKey;

    private LocalDateTime createdAt;
}
