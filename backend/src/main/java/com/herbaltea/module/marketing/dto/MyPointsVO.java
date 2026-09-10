package com.herbaltea.module.marketing.dto;

import com.herbaltea.module.marketing.entity.UserPointsAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * C 端「我的积分」概览（v29）
 *
 * <p>从未产生积分往来（无账户行）时返回全零，避免小程序端做 null 判断。
 */
@Data
@Schema(description = "我的积分概览")
public class MyPointsVO {

    @Schema(description = "当前可用积分（1 积分 = 0.01 元）")
    private Long balance;

    @Schema(description = "累计获得")
    private Long totalEarned;

    @Schema(description = "累计已用（下单抵扣）")
    private Long totalUsed;

    @Schema(description = "累计过期清零")
    private Long totalExpired;

    public static MyPointsVO of(UserPointsAccount account) {
        MyPointsVO vo = new MyPointsVO();
        vo.setBalance(account == null || account.getBalance() == null ? 0L : account.getBalance());
        vo.setTotalEarned(account == null || account.getTotalEarned() == null ? 0L : account.getTotalEarned());
        vo.setTotalUsed(account == null || account.getTotalUsed() == null ? 0L : account.getTotalUsed());
        vo.setTotalExpired(account == null || account.getTotalExpired() == null ? 0L : account.getTotalExpired());
        return vo;
    }
}
