package com.herbaltea.module.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.marketing.dto.PointRecordVO;
import com.herbaltea.module.marketing.entity.PointRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
