package com.herbaltea.module.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.marketing.dto.CouponQuery;
import com.herbaltea.module.marketing.dto.CouponVO;
import com.herbaltea.module.marketing.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * coupons 数据访问（券模板）
 */
@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 券模板分页（v28）：联门店名，按创建时间倒序。
     */
    @Select("""
            SELECT c.id AS id, c.name AS name, c.type AS type, c.scope AS scope,
                   c.store_id AS storeId, s.store_name AS storeName,
                   c.threshold_amount AS thresholdAmount, c.discount_amount AS discountAmount,
                   c.rules AS rules, c.total_count AS totalCount,
                   c.received_count AS receivedCount, c.per_user_limit AS perUserLimit,
                   c.start_time AS startTime, c.end_time AS endTime,
                   c.status AS status, c.created_at AS createdAt, c.updated_at AS updatedAt
            FROM coupons c
                     LEFT JOIN stores s ON s.id = c.store_id
            WHERE (#{q.status} IS NULL OR c.status = #{q.status})
              AND (#{q.type} IS NULL OR c.type = #{q.type})
              AND (#{q.scope} IS NULL OR c.scope = #{q.scope})
              AND (#{q.storeId} IS NULL OR c.store_id = #{q.storeId})
              AND (#{q.keyword} IS NULL OR #{q.keyword} = '' OR c.name LIKE CONCAT('%', #{q.keyword}, '%'))
            ORDER BY c.id DESC
            """)
    IPage<CouponVO> pageCoupons(IPage<?> page, @Param("q") CouponQuery q);

    /**
     * 原子领取：{@code received_count + 1}，仅当仍有余量（{@code received_count < total_count}）。
     *
     * @return 影响行数（0 = 已领完）
     */
    @Update("""
            UPDATE coupons SET received_count = received_count + 1, version = version + 1
             WHERE id = #{id} AND status = 1 AND received_count < total_count
            """)
    int incrReceived(@Param("id") Long id);

    /**
     * 退还领取量（删除持券 / 退款退回时用，不会低于 0）。
     */
    @Update("""
            UPDATE coupons SET received_count = GREATEST(received_count - 1, 0), version = version + 1
             WHERE id = #{id}
            """)
    int decrReceived(@Param("id") Long id);
}
