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
     * 支付成功发放积分（order.paid 订阅者，v27 落地实现）
     *
     * <p>D15 双维归属：
     * <ul>
     *   <li>门店自主营销产生的积分（source_type=1）→ 成本归门店，结算单按门店积分行展示</li>
     *   <li>平台活动产生的积分（source_type=2）→ 平台补贴，结算单按平台补贴行展示</li>
     * </ul>
     *
     * <p>幂等：biz_key = {@code grant:{orderNo}}，命中 uk_ptr_biz(change_type, biz_key) 时跳过；
     * 积分有效期 12 个月（batch_no 为该批次的回收单元）。
     *
     * @param orderNo 订单号（幂等键，不可为空）
     */
    void grantPoints(Long userId, Long storeId, Long orderId, String orderNo, int amount, Integer sourceType);

    /**
     * 积分抵扣下单（v27 落地实现）：原子扣减（{@code balance >= amount}），余额不足抛业务异常。
     *
     * @param bizKey 幂等键（传订单号），uk_ptr_biz(change_type=2, biz_key) 防重复抵扣
     */
    void usePoints(Long userId, int amount, String bizKey);

    /**
     * 退款回收积分（v27）：按订单号回收此前发放且未回收的积分，余额不足时钳零。
     *
     * @return 实际回收的积分（0 = 无待回收记录）
     */
    long reclaimPoints(Long userId, Long orderId, String orderNo);

    /**
     * 每日积分过期回收（D8，v27 落地实现）：
     * 到期批次清零入流水（change_type=4）+ 到期前 7 天提醒。
     *
     * @return 本次清零的批次数
     */
    int expirePoints();

    /**
     * 会员积分流水分页（v26：B 端会员详情展示；跨模块只读入口，changeType 为 null 查全部）
     */
    IPage<PointRecordVO> pagePointRecords(Long userId, Integer changeType, long page, long size);
}
