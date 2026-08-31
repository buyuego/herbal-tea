package com.herbaltea.module.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.store.entity.Store;
import org.apache.ibatis.annotations.Mapper;

/**
 * stores 数据访问
 */
@Mapper
public interface StoreMapper extends BaseMapper<Store> {
}
