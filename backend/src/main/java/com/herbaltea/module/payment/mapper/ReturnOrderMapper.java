package com.herbaltea.module.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.payment.entity.ReturnOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 退货单 Mapper（return_orders）
 *
 * <p>收货（return:inspect）：{@link #casReceive} 仅在待收货（warehouse_status=1）时执行，
 * 推进 2 已收货并置退货单 3 待验货，防重复收货。
 * <p>验货动作（return:inspect）核心：{@link #casInspect} 仅在已收货（warehouse_status=2）
 * 时允许执行，防重复验货；状态以乐观锁语义双条件更新。
 */
@Mapper
public interface ReturnOrderMapper extends BaseMapper<ReturnOrder> {

    /**
     * 总部收货 CAS：warehouse_status 1 待收货 → 2 已收货，退货单 1 待寄回 → 3 待验货，
     * 回填收货人与收货时间。
     *
     * @return 1=成功；0=已收货或状态异常（调用方按业务提示）
     */
    @Update("UPDATE return_orders SET warehouse_status = 2, received_by = #{receivedBy}, "
            + "received_at = NOW(), status = 3, updated_at = NOW() "
            + "WHERE id = #{id} AND warehouse_status = 1")
    int casReceive(@Param("id") Long id, @Param("receivedBy") Long receivedBy);

    /**
     * 验货 CAS：warehouse_status 2 已收货 → 3 验货通过 / 4 验货不通过，
     * 退货单整体置 4 已完结。
     *
     * @return 1=成功；0=未收货或已验货（调用方按业务提示）
     */
    @Update("UPDATE return_orders SET warehouse_status = #{whStatus}, "
            + "inspection_result = #{result}, inspected_by = #{inspectedBy}, "
            + "status = 4, updated_at = NOW() "
            + "WHERE id = #{id} AND warehouse_status = 2")
    int casInspect(@Param("id") Long id, @Param("whStatus") int whStatus,
                   @Param("result") String result, @Param("inspectedBy") Long inspectedBy);
}
