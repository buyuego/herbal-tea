package com.herbaltea.module.marketing;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.marketing.dto.CouponQuery;
import com.herbaltea.module.marketing.dto.CouponSaveRequest;
import com.herbaltea.module.marketing.dto.CouponUseResult;
import com.herbaltea.module.marketing.dto.CouponVO;
import com.herbaltea.module.marketing.dto.UserCouponVO;

import java.math.BigDecimal;

/**
 * 优惠券服务（v28：券模板 / 领券 / 核销，coupons + user_coupons）
 *
 * <p>券类型：1 满减券（discountAmount 直减）/ 2 折扣券（rules.discountRate，可选 maxDiscount 封顶）。
 * <p>成本归属（与积分 D15 同构）：
 * <ul>
 *   <li>scope=1 平台券 → 平台承担，结算单走「平台券补贴」行，不减店铺应付</li>
 *   <li>scope=2 本店券 → 店铺承担，结算单走「本店券成本」行，减店铺应付</li>
 * </ul>
 */
public interface CouponService {

    /** 券模板分页（menu:marketing 可见） */
    IPage<CouponVO> pageCoupons(CouponQuery query);

    /** 券模板详情 */
    CouponVO getCoupon(Long id);

    /**
     * 创建券模板（未发布态）。
     *
     * @param operatorStoreId 操作者所属门店（店长必填 → 强制本店券；总部为 null → 可建平台券）
     */
    Long createCoupon(CouponSaveRequest req, Long operatorStoreId);

    /** 编辑券模板（仅「未发布」可改，避免已发放券的规则漂移） */
    void updateCoupon(Long id, CouponSaveRequest req, Long operatorStoreId);

    /** 发布（0→1，进入发放中） */
    void publishCoupon(Long id);

    /** 停止发放（1→2，已领券仍可使用至过期） */
    void stopCoupon(Long id);

    /**
     * 领券 / 发券：校验发放中、在有效期、未领完、未超限领 → 原子递增 received_count → 写持券记录。
     *
     * @return 持券记录 id（user_coupons.id）
     */
    Long grantCoupon(Long couponId, Long userId);

    /** 某会员的持券列表（status 为 null 查全部） */
    IPage<UserCouponVO> pageUserCoupons(Long userId, Integer status, long page, long size);

    /** 某券模板的领取记录 */
    IPage<UserCouponVO> pageCouponGrants(Long couponId, long page, long size);

    /**
     * 下单核销（v28，Order 模块跨模块调用入口）：
     * 校验归属 / 未使用 / 未过期 / 门店匹配 / 门槛 → 计算优惠 → 原子核销（status 0→1）。
     *
     * @param orderAmount 订单商品小计（门槛与折扣计算基数）
     * @return 核销结果（优惠金额 + 券归属 scope）
     */
    CouponUseResult useCoupon(Long userCouponId, Long userId, Long storeId, BigDecimal orderAmount);

    /** 订单创建后回填持券记录的 order_id（下单流程中券先核销、订单后落库） */
    void bindOrderId(Long userCouponId, Long orderId);

    /**
     * 退款退回：订单已核销的券置为「退款退回」（v28，保留审计不恢复为未使用）。
     *
     * @return 退回的券数量
     */
    int refundCoupons(Long orderId);

    /**
     * 预计算优惠金额（不下单核销，供前端/下单前展示）。
     *
     * @return 可抵扣金额（不满足条件时为 0）
     */
    BigDecimal calcDiscount(Long userCouponId, Long userId, Long storeId, BigDecimal orderAmount);
}
