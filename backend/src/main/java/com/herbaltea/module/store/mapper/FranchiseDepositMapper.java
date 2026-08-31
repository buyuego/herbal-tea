package com.herbaltea.module.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.herbaltea.module.store.dto.DepositVO;
import com.herbaltea.module.store.entity.FranchiseDeposit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * franchise_deposits 数据访问
 */
@Mapper
public interface FranchiseDepositMapper extends BaseMapper<FranchiseDeposit> {

    /**
     * 保证金流水分页（联查 stores 展示门店编号/名称）。
     * type 过滤：null 全部 / 1缴纳 / 2退还；status 过滤：null 全部 / 0待处理 / 1完成。
     */
    @Select("""
            <script>
            SELECT d.id, d.store_id, s.store_no, s.store_name, d.type, d.amount,
                   d.status, d.biz_no, d.paid_at, d.refunded_at, d.created_at
            FROM franchise_deposits d
            JOIN stores s ON s.id = d.store_id
            <where>
              <if test="type != null">AND d.type = #{type}</if>
              <if test="status != null">AND d.status = #{status}</if>
              <if test="storeId != null">AND d.store_id = #{storeId}</if>
            </where>
            ORDER BY d.id DESC
            </script>
            """)
    IPage<DepositVO> selectDepositPage(Page<?> page,
                                       @Param("type") Integer type,
                                       @Param("status") Integer status,
                                       @Param("storeId") Long storeId);
}
