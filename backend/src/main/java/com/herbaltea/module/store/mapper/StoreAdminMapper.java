package com.herbaltea.module.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.store.entity.StoreAdmin;
import org.apache.ibatis.annotations.Mapper;

/**
 * store_admins 数据访问（模块边界：仅 store 模块可读写）
 *
 * <p>门店绑定查询（{@link com.herbaltea.module.store.StoreService#storeIdOfAdmin}）：
 * 登录时确定主店，Auth 模块经本服务查询，不直读表（跨模块只读约束）。
 */
@Mapper
public interface StoreAdminMapper extends BaseMapper<StoreAdmin> {
}
