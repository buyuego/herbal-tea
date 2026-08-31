package com.herbaltea.module.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.store.dto.StoreAdminVO;
import com.herbaltea.module.store.entity.StoreAdmin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * store_admins 数据访问（模块边界：仅 store 模块可读写）
 *
 * <p>门店绑定查询（{@link com.herbaltea.module.store.StoreService#storeIdOfAdmin}）：
 * 登录时确定主店，Auth 模块经本服务查询，不直读表（跨模块只读约束）。
 */
@Mapper
public interface StoreAdminMapper extends BaseMapper<StoreAdmin> {

    /**
     * 门店管理员列表（联查 admin_users 展示信息，总部运营用）。
     *
     * @param storeId 门店 id
     */
    @Select("""
            SELECT sa.id, sa.admin_id, sa.store_id, sa.is_owner, sa.status,
                   u.username, u.real_name, u.phone, u.role_id
            FROM store_admins sa
            JOIN admin_users u ON u.id = sa.admin_id
            WHERE sa.store_id = #{storeId}
            ORDER BY sa.is_owner DESC, sa.id
            """)
    List<StoreAdminVO> listByStore(@Param("storeId") Long storeId);
}
