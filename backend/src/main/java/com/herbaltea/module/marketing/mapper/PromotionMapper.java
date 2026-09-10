package com.herbaltea.module.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.marketing.dto.PromotionQuery;
import com.herbaltea.module.marketing.dto.PromotionVO;
import com.herbaltea.module.marketing.entity.Promotion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * promotions 数据访问（促销活动，v30）
 */
@Mapper
public interface PromotionMapper extends BaseMapper<Promotion> {

    /**
     * 活动分页（v30）：联门店名，按 id 倒序。
     */
    @Select("""
            SELECT p.id AS id, p.title AS title, p.type AS type, p.scope AS scope,
                   p.store_id AS storeId, s.store_name AS storeName, p.rules AS rules,
                   p.start_time AS startTime, p.end_time AS endTime,
                   p.status AS status, p.created_at AS createdAt, p.updated_at AS updatedAt
            FROM promotions p
                     LEFT JOIN stores s ON s.id = p.store_id
            WHERE (#{q.status} IS NULL OR p.status = #{q.status})
              AND (#{q.type} IS NULL OR p.type = #{q.type})
              AND (#{q.scope} IS NULL OR p.scope = #{q.scope})
              AND (#{q.storeId} IS NULL OR p.store_id = #{q.storeId})
              AND (#{q.keyword} IS NULL OR #{q.keyword} = '' OR p.title LIKE CONCAT('%', #{q.keyword}, '%'))
            ORDER BY p.id DESC
            """)
    IPage<PromotionVO> pagePromotions(IPage<?> page, @Param("q") PromotionQuery q);

    /**
     * 门店当前可命中的活动：平台活动（scope=1，全店通用）+ 本店活动（scope=2，仅归属门店）。
     * 仅取「已发布且时间窗口命中」的活动；叠加规则由服务层决定（取优惠最大者）。
     */
    @Select("""
            SELECT * FROM promotions
             WHERE status = 1
               AND start_time <= NOW() AND end_time >= NOW()
               AND (scope = 1 OR (scope = 2 AND store_id = #{storeId}))
             ORDER BY id DESC
            """)
    List<Promotion> listActive(@Param("storeId") Long storeId);

    /**
     * 平台活动（全店通用，无需门店维度，供 C 端未选门店时展示）。
     */
    @Select("""
            SELECT * FROM promotions
             WHERE status = 1
               AND scope = 1
               AND start_time <= NOW() AND end_time >= NOW()
             ORDER BY id DESC
            """)
    List<Promotion> listActivePlatform();

    /**
     * 到点自动结束：进行中且已过结束时间（供定时任务调用）。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE promotions SET status = 2, version = version + 1
             WHERE status = 1 AND end_time < NOW()
            """)
    int closeExpired();
}
