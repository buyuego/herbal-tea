package com.herbaltea.module.user.dto;

import com.herbaltea.module.marketing.dto.PointRecordVO;
import com.herbaltea.module.user.entity.UserAddress;
import lombok.Data;

import java.util.List;

/**
 * B 端会员详情（v26）：会员概览 + 收货地址 + 最近积分流水
 */
@Data
public class MemberDetailVO {

    /** 会员概览（含积分余额与订单统计） */
    private MemberVO member;

    /** 收货地址列表（默认地址置顶） */
    private List<UserAddress> addresses;

    /** 最近积分流水（默认最近 20 条，按时间倒序） */
    private List<PointRecordVO> pointRecords;
}
