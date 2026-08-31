package com.herbaltea.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.product.entity.InventoryRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * inventory_records 数据访问（库存流水，只写 + 分页查，不更新不删除）
 */
@Mapper
public interface InventoryRecordMapper extends BaseMapper<InventoryRecord> {
}
