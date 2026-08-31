package com.herbaltea.module.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.settlement.dto.SettlementPageVO;
import com.herbaltea.module.settlement.entity.Settlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算单 Mapper（settlements）
 *
 * <p>核心：
 * <ul>
 *   <li>{@link #pageVO}：JOIN stores 分页联查，STORE 管理员按 storeIds 过滤（与退款分页同款手法）</li>
 *   <li>{@link #casStatus}：审核/打款 CAS(status, version) 双条件更新防并发（D1）</li>
 *   <li>{@link #markReviewed}/{@link #markPaid}：回填审计字段</li>
 *   <li>{@link #countUnsettledOrders}：生成结算单时判定「已完成且未参与过结算」的订单</li>
 * </ul>
 */
@Mapper
public interface SettlementMapper extends BaseMapper<Settlement> {

    /**
     * 分页联查（settlements JOIN stores）
     *
     * @param storeIds 门店过滤；null 或空 = 总部全量
     */
    @Select("""
            <script>
            SELECT s.id, s.settle_no, s.store_id, st.store_name, s.period, s.type,
                   s.order_count, s.total_amount, s.commission_amount,
                   s.points_deduct_amount, s.points_cost_store, s.points_cost_platform,
                   s.coupon_cost_store, s.refund_adjust, s.adjust_amount, s.final_amount,
                   s.confirm_status, s.status, s.auto_confirm_at, s.confirmed_at,
                   s.reviewed_by, s.paid_at, s.payout_no, s.dispute_note,
                   s.created_at, s.version
            FROM settlements s
            JOIN stores st ON st.id = s.store_id
            <where>
                <if test="q.settleNo != null and q.settleNo != ''">
                    AND s.settle_no LIKE CONCAT('%', #{q.settleNo}, '%')
                </if>
                <if test="q.storeId != null">
                    AND s.store_id = #{q.storeId}
                </if>
                <if test="q.status != null">
                    AND s.status = #{q.status}
                </if>
                <if test="q.period != null and q.period != ''">
                    AND s.period LIKE CONCAT('%', #{q.period}, '%')
                </if>
                <if test="storeIds != null and storeIds.size() > 0">
                    AND s.store_id IN
                    <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
                </if>
            </where>
            ORDER BY s.id DESC
            </script>
            """)
    List<SettlementPageVO> pageVO(@Param("q") Object query, @Param("storeIds") List<Long> storeIds);

    /**
     * 分页计数（与 pageVO 同条件）
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM settlements s
            JOIN stores st ON st.id = s.store_id
            <where>
                <if test="q.settleNo != null and q.settleNo != ''">
                    AND s.settle_no LIKE CONCAT('%', #{q.settleNo}, '%')
                </if>
                <if test="q.storeId != null">
                    AND s.store_id = #{q.storeId}
                </if>
                <if test="q.status != null">
                    AND s.status = #{q.status}
                </if>
                <if test="q.period != null and q.period != ''">
                    AND s.period LIKE CONCAT('%', #{q.period}, '%')
                </if>
                <if test="storeIds != null and storeIds.size() > 0">
                    AND s.store_id IN
                    <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
                </if>
            </where>
            </script>
            """)
    long countVO(@Param("q") Object query, @Param("storeIds") List<Long> storeIds);

    /**
     * 状态 CAS：status=#{oldStatus} 且 version=#{version} 时才更新为 #{newStatus}（乐观锁防并发）
     *
     * @return 1=成功；0=状态已变化或版本不匹配（调用方按业务提示）
     */
    @Update("""
            UPDATE settlements
            SET status = #{newStatus}, version = version + 1
            WHERE id = #{id} AND status = #{oldStatus} AND version = #{version}
            """)
    int casStatus(@Param("id") Long id, @Param("oldStatus") int oldStatus,
                  @Param("newStatus") int newStatus, @Param("version") int version);

    /**
     * 平台审核回填（20→30 通过后）
     */
    @Update("UPDATE settlements SET reviewed_by = #{adminId} WHERE id = #{id} AND reviewed_by IS NULL")
    int markReviewed(@Param("id") Long id, @Param("adminId") Long adminId);

    /**
     * 打款回填（30→40 通过后）
     */
    @Update("""
            UPDATE settlements
            SET paid_at = #{paidAt}, payout_no = #{payoutNo}
            WHERE id = #{id} AND paid_at IS NULL
            """)
    int markPaid(@Param("id") Long id, @Param("paidAt") LocalDateTime paidAt, @Param("payoutNo") String payoutNo);

    /**
     * 生成结算单用：统计某门店「已完结（status=90）且尚未参与过任何结算单」的订单数
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM orders o
            WHERE o.store_id = #{storeId} AND o.status = 90
              AND NOT EXISTS (
                  SELECT 1 FROM settlement_items si WHERE si.order_id = o.id
              )
            <if test="start != null">
              AND o.finished_at &gt;= #{start}
            </if>
            <if test="end != null">
              AND o.finished_at &lt; #{end}
            </if>
            </script>
            """)
    long countUnsettledOrders(@Param("storeId") Long storeId,
                              @Param("start") java.time.LocalDateTime start,
                              @Param("end") java.time.LocalDateTime end);

    /**
     * 生成结算单用：取某门店未结算的已完成订单（带佣金率，按时间升序）
     */
    @Select("""
            <script>
            SELECT o.id, o.order_no, o.store_id, o.total_amount, o.pay_amount,
                   o.points_deduct_amount, o.points_earned, o.points_source,
                   o.coupon_amount, o.commission_rate, o.finished_at
            FROM orders o
            WHERE o.store_id = #{storeId} AND o.status = 90
              AND NOT EXISTS (
                  SELECT 1 FROM settlement_items si WHERE si.order_id = o.id
              )
            <if test="start != null">
              AND o.finished_at &gt;= #{start}
            </if>
            <if test="end != null">
              AND o.finished_at &lt; #{end}
            </if>
            ORDER BY o.id
            </script>
            """)
    List<SettlementMapper.OrderRow> listUnsettledOrders(@Param("storeId") Long storeId,
                                                        @Param("start") java.time.LocalDateTime start,
                                                        @Param("end") java.time.LocalDateTime end);

    /**
     * 生成结算单用：取有未结算已完成订单的门店列表（全门店生成入口）
     */
    @Select("""
            <script>
            SELECT DISTINCT o.store_id FROM orders o
            WHERE o.status = 90
              AND NOT EXISTS (SELECT 1 FROM settlement_items si WHERE si.order_id = o.id)
            <if test="start != null">
              AND o.finished_at &gt;= #{start}
            </if>
            <if test="end != null">
              AND o.finished_at &lt; #{end}
            </if>
            </script>
            """)
    List<Long> listStoreIdsWithUnsettled(@Param("start") java.time.LocalDateTime start,
                                         @Param("end") java.time.LocalDateTime end);

    /** 生成结算单的中间行结构 */
    @lombok.Data
    class OrderRow {
        private Long id;
        private String orderNo;
        private Long storeId;
        private java.math.BigDecimal totalAmount;
        private java.math.BigDecimal payAmount;
        private java.math.BigDecimal pointsDeductAmount;
        private java.lang.Long pointsEarned;
        private java.lang.Integer pointsSource;
        private java.math.BigDecimal couponAmount;
        private java.math.BigDecimal commissionRate;
        private java.time.LocalDateTime finishedAt;
    }
}
