package com.herbaltea.module.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.marketing.dto.PointRecordVO;
import com.herbaltea.module.marketing.dto.PointsExpireBatch;
import com.herbaltea.module.marketing.dto.PointsExpireNotice;
import com.herbaltea.module.marketing.entity.PointRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * point_records 数据访问（积分流水）
 */
@Mapper
public interface PointRecordMapper extends BaseMapper<PointRecord> {

    /**
     * 会员积分流水分页（v26）：联订单号与门店名，按时间倒序。
     */
    @Select("""
            SELECT r.id AS id, r.user_id AS userId, r.store_id AS storeId,
                   s.store_name AS storeName, r.order_id AS orderId,
                   o.order_no AS orderNo, r.change_type AS changeType,
                   r.source_type AS sourceType, r.points AS points,
                   r.biz_key AS bizKey, r.created_at AS createdAt
            FROM point_records r
                     LEFT JOIN stores s ON s.id = r.store_id
                     LEFT JOIN orders o ON o.id = r.order_id
            WHERE r.user_id = #{userId}
              AND (#{changeType} IS NULL OR r.change_type = #{changeType})
            ORDER BY r.id DESC
            """)
    IPage<PointRecordVO> pageByUser(IPage<?> page,
                                    @Param("userId") Long userId,
                                    @Param("changeType") Integer changeType);

    /**
     * 扫描已到期待回收的发放批次（v27）：排除已回收（存在 change_type=4 且 biz_key=expire:{batchNo} 的流水）。
     */
    @Select("""
            SELECT r.user_id AS userId, r.batch_no AS batchNo, SUM(r.points) AS points
            FROM point_records r
            WHERE r.change_type = 1
              AND r.batch_no IS NOT NULL
              AND r.expire_at IS NOT NULL
              AND r.expire_at <= #{now}
              AND NOT EXISTS (SELECT 1 FROM point_records e
                               WHERE e.change_type = 4
                                 AND e.biz_key = CONCAT('expire:', r.batch_no))
            GROUP BY r.user_id, r.batch_no
            ORDER BY r.user_id
            """)
    List<PointsExpireBatch> selectExpireBatches(@Param("now") java.time.LocalDateTime now);

    /**
     * 查询某订单已发放、未回收且未过期清零的积分（v27：退款回收用，biz_key=grant:{orderNo}）。
     *
     * <p>两个 NOT EXISTS 缺一不可：
     * <ul>
     *   <li>排除已退款回收（change_type=3，同订单）</li>
     *   <li>排除已过期清零（change_type=4，按批次）——否则积分过期后退款会重复扣减</li>
     * </ul>
     */
    @Select("""
            SELECT COALESCE(SUM(r.points), 0)
            FROM point_records r
            WHERE r.change_type = 1 AND r.biz_key = CONCAT('grant:', #{orderNo})
              AND NOT EXISTS (SELECT 1 FROM point_records c
                               WHERE c.change_type = 3
                                 AND c.biz_key = CONCAT('reclaim:', #{orderNo}))
              AND NOT EXISTS (SELECT 1 FROM point_records e
                               WHERE e.change_type = 4
                                 AND e.biz_key = CONCAT('expire:', r.batch_no))
            """)
    Long sumUnreclaimedByOrder(@Param("orderNo") String orderNo);

    /**
     * 即将过期提醒（v27：到期前 7 天内且未回收的批次，按用户聚合）。
     */
    @Select("""
            SELECT r.user_id AS userId, SUM(r.points) AS points
            FROM point_records r
            WHERE r.change_type = 1
              AND r.batch_no IS NOT NULL
              AND r.expire_at IS NOT NULL
              AND r.expire_at > #{now}
              AND r.expire_at <= #{deadline}
              AND NOT EXISTS (SELECT 1 FROM point_records e
                               WHERE e.change_type = 4
                                 AND e.biz_key = CONCAT('expire:', r.batch_no))
            GROUP BY r.user_id
            """)
    List<PointsExpireNotice> selectExpiringUsers(@Param("now") java.time.LocalDateTime now,
                                                 @Param("deadline") java.time.LocalDateTime deadline);
}
