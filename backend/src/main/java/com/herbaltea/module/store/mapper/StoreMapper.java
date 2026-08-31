package com.herbaltea.module.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.store.entity.Store;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * stores 数据访问
 */
@Mapper
public interface StoreMapper extends BaseMapper<Store> {

    /**
     * 最大门店编号序号（STxxx 的 xxx 部分最大值）。
     * 门店编号生成与主键 id 解耦：删除门店后 AUTO_INCREMENT 不回退，
     * 若按 MAX(id)+1 生成会与既有编号撞号/错位；按编号最大值顺延保证唯一且不复用。
     */
    @Select("SELECT MAX(CAST(SUBSTRING(store_no, 3) AS UNSIGNED)) FROM stores")
    Long selectMaxStoreSeq();
}
