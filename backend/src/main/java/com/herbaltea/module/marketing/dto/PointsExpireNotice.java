package com.herbaltea.module.marketing.dto;

import lombok.Data;

/**
 * 即将过期提醒对象（v27：到期前 7 天，按用户聚合的待过期积分）
 */
@Data
public class PointsExpireNotice {

    private Long userId;

    /** 7 天内将过期的积分合计 */
    private Long points;
}
