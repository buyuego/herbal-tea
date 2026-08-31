package com.herbaltea.module.store.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.herbaltea.module.store.dto.StaffVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 员工账号查询（v11，员工管理专用只读 Mapper）
 *
 * <p>一条 SQL 分页联查 store_admins → admin_users → roles，无 N+1；
 * boundStatus 过滤绑定状态（null 全部 / 1 正常 / 0 已移除）。
 */
@Mapper
public interface StoreStaffMapper {

    /**
     * 本店员工分页列表（联查登录名/姓名/角色/绑定时间）。
     *
     * @param page        MyBatis-Plus 分页参数（自动 LIMIT）
     * @param storeId     门店 id（由登录上下文注入，SINGLE_STORE 数据范围）
     * @param boundStatus 绑定状态过滤：null 全部 / 1 正常 / 0 已移除
     */
    @Select("""
            <script>
            SELECT sa.admin_id, u.username, u.real_name, u.phone, u.status AS admin_status,
                   u.role_id, r.name AS role_name, u.last_login_at,
                   sa.is_owner, sa.status AS bind_status, sa.created_at AS bound_at
            FROM store_admins sa
            JOIN admin_users u ON u.id = sa.admin_id
            JOIN roles r ON r.id = u.role_id
            WHERE sa.store_id = #{storeId}
            <if test="boundStatus != null">
              AND sa.status = #{boundStatus}
            </if>
            ORDER BY sa.is_owner DESC, sa.id DESC
            </script>
            """)
    IPage<StaffVO> selectStaffPage(Page<?> page,
                                   @Param("storeId") Long storeId,
                                   @Param("boundStatus") Integer boundStatus);
}
