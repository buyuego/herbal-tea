package com.herbaltea.module.marketing;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.marketing.dto.PointRecordVO;

/**
 * 营销模块（promotions / coupons / user_coupons / point_records / banners）
 *
 * <p>职责：优惠券、积分规则与发放/过期回收（D8 points.expired）、
 * 积分成本双维归属（D15：门店营销积分归门店 / 平台活动积分平台补贴）。
 */
public interface MarketingService {

    /**
     * 支付成功发放积分（order.paid 订阅者）
     *
     * <p>D15 双维归属：
     * <ul>
     *   <li>门店自主营销产生的积分（source_type=1）→ 成本归门店，结算单按门店积分行展示</li>
     *   <li>平台活动产生的积分（source_type=2）→ 平台补贴，结算单按平台补贴行展示</li>
     * </ul>
     */
    void grantPoints(Long userId, Long storeId, Long orderId, int amount, Integer sourceType);

    /** 每日积分过期回收（points.expired 事件：清零入流水 + 到期前 7 天提醒，D8） */
    void expirePoints();

    /** 积分抵扣下单（校验余额 + 原子扣减） */
    void usePoints(Long userId, int amount);

    /**
     * 会员积分流水分页（v26：B 端会员详情展示；跨模块只读入口，changeType 为 null 查全部）
     */
    IPage<PointRecordVO> pagePointRecords(Long userId, Integer changeType, long page, long size);
}
