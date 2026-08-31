package com.herbaltea.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 订单 Mapper（orders）
 *
 * <p>状态流转核心：{@link #casStatus} 乐观锁双条件更新（16.2），
 * 返回影响行数 0 = version 已变或状态已变，调用方须按冲突处理。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * CAS 状态流转：WHERE status=旧值 AND version=旧值 → SET status=新值, version=version+1
     *
     * @return 1=成功；0=并发冲突或非法状态（调用方抛 40900）
     */
    @Update("UPDATE orders SET status = #{newStatus}, version = version + 1, updated_at = NOW() "
            + "WHERE id = #{id} AND status = #{oldStatus} AND version = #{version}")
    int casStatus(@Param("id") Long id, @Param("oldStatus") int oldStatus,
                  @Param("newStatus") int newStatus, @Param("version") int version);

    /** 支付成功回填：paid_at + 总部发货状态置待发货(2) */
    @Update("UPDATE orders SET paid_at = NOW(), warehouse_status = 2, updated_at = NOW() WHERE id = #{id}")
    int markPaid(@Param("id") Long id);

    /** 发货回填：物流单号/快递公司/发货人/时间 + 总部状态置已发货(3) */
    @Update("UPDATE orders SET tracking_no = #{trackingNo}, carrier = #{carrier}, "
            + "shipped_by = #{shippedBy}, shipped_at = NOW(), warehouse_status = 3, updated_at = NOW() "
            + "WHERE id = #{id}")
    int markShipped(@Param("id") Long id, @Param("trackingNo") String trackingNo,
                    @Param("carrier") String carrier, @Param("shippedBy") Long shippedBy);

    /** 签收回填：总部状态置已签收(4) + 完成时间（15 天自动签收同样走此方法） */
    @Update("UPDATE orders SET warehouse_status = 4, finished_at = NOW(), "
            + "auto_signed_at = IFNULL(auto_signed_at, NOW()), updated_at = NOW() WHERE id = #{id}")
    int markSigned(@Param("id") Long id);

    /** 关单标记（1=超时自动关单 / 2=用户取消） */
    @Update("UPDATE orders SET auto_close_status = #{flag}, updated_at = NOW() WHERE id = #{id}")
    int markAutoClose(@Param("id") Long id, @Param("flag") int flag);

    /** 超时扫描：待支付且已过 expire_at（OrderCloseTask 每分钟轮询） */
    @Select("SELECT * FROM orders WHERE status = 10 AND expire_at IS NOT NULL "
            + "AND expire_at < NOW() ORDER BY id ASC LIMIT 100")
    List<Order> selectExpiredPending();
}
