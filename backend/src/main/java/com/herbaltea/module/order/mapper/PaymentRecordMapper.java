package com.herbaltea.module.order.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.order.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 支付单 Mapper（payment_records）
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    default PaymentRecord selectByPayNo(String payNo) {
        return selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPayNo, payNo).last("LIMIT 1"));
    }

    /**
     * 支付成功回填（幂等：仅 0待支付 → 1成功，重复回调影响行数 0 直接返回）
     */
    @Update("UPDATE payment_records SET status = 1, transaction_id = #{transactionId}, "
            + "paid_at = NOW(), updated_at = NOW() WHERE id = #{id} AND status = 0")
    int markPaid(@Param("id") Long id, @Param("transactionId") String transactionId);
}
