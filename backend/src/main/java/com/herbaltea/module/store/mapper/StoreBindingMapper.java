package com.herbaltea.module.store.mapper;

import com.herbaltea.module.store.dto.StoreBindingVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 门店绑定查询专用只读 Mapper（MULTI_STORE）
 *
 * <p>my-stores 数据源：联查 store_admins → stores 返回当前账号全部正常绑定门店
 * （含门店编号/名称，前端切店下拉展示）。
 */
@Mapper
public interface StoreBindingMapper {

    @Select("""
            SELECT sa.store_id, s.store_no, s.store_name, sa.is_owner
            FROM store_admins sa
            JOIN stores s ON s.id = sa.store_id
            WHERE sa.admin_id = #{adminId} AND sa.status = 1
            ORDER BY sa.is_owner DESC, sa.id ASC
            """)
    List<StoreBindingVO> selectBindingsOf(@Param("adminId") Long adminId);
}
