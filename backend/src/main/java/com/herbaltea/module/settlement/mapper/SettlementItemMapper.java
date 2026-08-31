package com.herbaltea.module.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.settlement.entity.SettlementItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 结算单明细 Mapper（settlement_items）
 *
 * <p>按结算单查明细（D15 分行展示：门店营销积分行 / 平台补贴行一目了然）。
 */
@Mapper
public interface SettlementItemMapper extends BaseMapper<SettlementItem> {

    @Select("""
            SELECT id, settlement_id, order_id, order_no, item_type, direction, amount, remark, created_at
            FROM settlement_items
            WHERE settlement_id = #{settlementId}
            ORDER BY id
            """)
    List<SettlementItem> listBySettlementId(Long settlementId);
}
