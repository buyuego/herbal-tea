package com.herbaltea.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.auth.entity.DeviceTrust;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * device_trusts 数据访问（模块边界：仅 auth 模块可读写）
 */
@Mapper
public interface DeviceTrustMapper extends BaseMapper<DeviceTrust> {

    /**
     * D13 设备级吊销：某管理员全部设备信任置失效（登出/风控触发）。
     *
     * @param adminId 管理员 id
     * @return 影响行数
     */
    @Update("UPDATE device_trusts SET status = 0 WHERE admin_id = #{adminId} AND status = 1")
    int revokeByAdmin(@Param("adminId") Long adminId);
}
