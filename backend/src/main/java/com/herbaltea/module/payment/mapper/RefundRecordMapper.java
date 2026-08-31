package com.herbaltea.module.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.herbaltea.module.payment.dto.RefundPageQuery;
import com.herbaltea.module.payment.dto.RefundPageVO;
import com.herbaltea.module.payment.entity.RefundRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 退款单 Mapper（refund_records）
 *
 * <p>状态流转核心：{@link #casStatus} 乐观锁双条件更新（16.2），
 * 返回影响行数 0 = version 已变或状态已变，调用方须按冲突处理。
 *
 * <p>分页联查：refund_records 无 store_id 列，门店归属经 orders.store_id 联查
 * （Data Scope：门店管理员由 Service 层注入 storeIds 强制过滤，防跨店越权）。
 */
@Mapper
public interface RefundRecordMapper extends BaseMapper<RefundRecord> {

    /**
     * 退款单分页（JOIN orders / stores / users 联查门店、订单号、买家信息）
     *
     * @param storeIds 门店管理员可访问的门店列表（总部传 null 不加过滤）
     */
    @Select("""
            <script>
            SELECT r.id, r.refund_no, r.order_id, r.user_id, r.amount, r.reason,
                   r.refund_branch, r.status, r.escalation_status, r.approved_by_level,
                   r.approved_by, r.approved_at, r.handled_at, r.created_at,
                   o.order_no, o.store_id,
                   s.store_name,
                   u.nickname AS user_name, u.phone AS user_phone,
                   ro.status AS return_status, ro.warehouse_status,
                   ro.return_tracking_no
            FROM refund_records r
            JOIN orders o ON o.id = r.order_id
            LEFT JOIN stores s ON s.id = o.store_id
            LEFT JOIN users u ON u.id = r.user_id
            LEFT JOIN return_orders ro ON ro.refund_id = r.id
            <where>
              <if test="q.refundNo != null and q.refundNo != ''"> AND r.refund_no = #{q.refundNo}</if>
              <if test="q.orderNo != null and q.orderNo != ''"> AND o.order_no = #{q.orderNo}</if>
              <if test="q.storeId != null"> AND o.store_id = #{q.storeId}</if>
              <if test="q.status != null"> AND r.status = #{q.status}</if>
              <if test="q.refundBranch != null"> AND r.refund_branch = #{q.refundBranch}</if>
              <if test="storeIds != null and storeIds.size() > 0">
                AND o.store_id IN
                <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
              </if>
            </where>
            ORDER BY r.id DESC
            </script>
            """)
    IPage<RefundPageVO> pageRefunds(Page<?> page, @Param("q") RefundPageQuery q,
                                    @Param("storeIds") List<Long> storeIds);

    /**
     * CAS 状态流转：WHERE status=旧值 AND version=旧值 → SET status=新值, version=version+1
     *
     * @return 1=成功；0=并发冲突或非法状态（调用方抛 40900）
     */
    @Update("UPDATE refund_records SET status = #{newStatus}, version = version + 1, updated_at = NOW() "
            + "WHERE id = #{id} AND status = #{oldStatus} AND version = #{version}")
    int casStatus(@Param("id") Long id, @Param("oldStatus") int oldStatus,
                  @Param("newStatus") int newStatus, @Param("version") int version);

    /** 审批回填：审批方级别/审批人/时间 */
    @Update("UPDATE refund_records SET approved_by_level = #{level}, approved_by = #{approvedBy}, "
            + "approved_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int markApproved(@Param("id") Long id, @Param("level") int level,
                     @Param("approvedBy") Long approvedBy);

    /** 驳回回填：驳回原因/驳回人/时间 */
    @Update("UPDATE refund_records SET reject_reason = #{reason}, rejected_by = #{rejectedBy}, "
            + "rejected_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int markRejected(@Param("id") Long id, @Param("rejectedBy") Long rejectedBy,
                     @Param("reason") String reason);

    /** 退款完成回填（handled_at） */
    @Update("UPDATE refund_records SET handled_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int markHandled(@Param("id") Long id);
}
