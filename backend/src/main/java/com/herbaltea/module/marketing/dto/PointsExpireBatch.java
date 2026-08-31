package com.herbaltea.module.marketing.dto;

import lombok.Data;

/**
 * 待过期积分批次（v27：过期回收任务的扫描结果）
 *
 * <p>按「发放批次（batch_no）× 用户」聚合，同一批次只回收一次
 * （回收后写 change_type=4 流水，biz_key=expire:{batchNo}，扫描时用 NOT EXISTS 排除）。
 */
@Data
public class PointsExpireBatch {

    private Long userId;

    private String batchNo;

    /** 该批次发放的积分总量 */
    private Long points;
}
