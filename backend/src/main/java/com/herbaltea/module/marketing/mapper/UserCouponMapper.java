package com.herbaltea.module.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.marketing.dto.UserCouponVO;
import com.herbaltea.module.marketing.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * user_coupons 数据访问（用户持券）
 */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /**
     * 某用户某券模板的已领取数量（限领校验）。
     */
    @Select("SELECT COUNT(*) FROM user_coupons WHERE user_id = #{userId} AND coupon_id = #{couponId}")
    int countReceived(@Param("userId") Long userId, @Param("couponId") Long couponId);

    /**
     * 原子核销：仅当 {@code status = 0} 未使用时置为已使用并回填订单。
     *
     * @return 影响行数（0 = 已使用/已过期/不存在，防重复核销）
     */
    @Update("""
            UPDATE user_coupons
               SET status = 1, used_at = NOW(), order_id = #{orderId}
             WHERE id = #{id} AND user_id = #{userId} AND status = 0
            """)
    int markUsed(@Param("id") Long id, @Param("userId") Long userId, @Param("orderId") Long orderId);

    /**
     * 退款退回：已核销的券退回为「退款退回」状态（v28：不恢复为未使用，保留审计）。
     */
    @Update("""
            UPDATE user_coupons SET status = 3 WHERE id = #{id} AND status = 1
            """)
    int markRefunded(@Param("id") Long id);

    /**
     * 会员可用券列表（v28：未使用且未过期，按到期时间升序）。
     */
    @Select("""
            SELECT uc.id AS id, uc.user_id AS userId, uc.coupon_id AS couponId,
                   c.name AS couponName, c.type AS type, c.scope AS scope,
                   uc.store_id AS storeId, s.store_name AS storeName,
                   c.threshold_amount AS thresholdAmount, c.discount_amount AS discountAmount,
                   c.rules AS rules, uc.status AS status, uc.order_id AS orderId,
                   o.order_no AS orderNo, uc.received_at AS receivedAt,
                   uc.used_at AS usedAt, uc.expire_at AS expireAt
            FROM user_coupons uc
                     JOIN coupons c ON c.id = uc.coupon_id
                     LEFT JOIN stores s ON s.id = uc.store_id
                     LEFT JOIN orders o ON o.id = uc.order_id
            WHERE uc.user_id = #{userId}
              AND (#{status} IS NULL OR uc.status = #{status})
            ORDER BY uc.expire_at ASC, uc.id ASC
            """)
    IPage<UserCouponVO> pageByUser(IPage<?> page,
                                   @Param("userId") Long userId,
                                   @Param("status") Integer status);

    /**
     * 券模板的领取记录（v28：B 端查看发放情况）。
     */
    @Select("""
            SELECT uc.id AS id, uc.user_id AS userId, uc.coupon_id AS couponId,
                   c.name AS couponName, c.type AS type, c.scope AS scope,
                   uc.store_id AS storeId, s.store_name AS storeName,
                   c.threshold_amount AS thresholdAmount, c.discount_amount AS discountAmount,
                   c.rules AS rules, uc.status AS status, uc.order_id AS orderId,
                   o.order_no AS orderNo, uc.received_at AS receivedAt,
                   uc.used_at AS usedAt, uc.expire_at AS expireAt
            FROM user_coupons uc
                     JOIN coupons c ON c.id = uc.coupon_id
                     LEFT JOIN stores s ON s.id = uc.store_id
                     LEFT JOIN orders o ON o.id = uc.order_id
            WHERE uc.coupon_id = #{couponId}
            ORDER BY uc.id DESC
            """)
    IPage<UserCouponVO> pageByCoupon(IPage<?> page, @Param("couponId") Long couponId);
}
