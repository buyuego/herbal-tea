package com.herbaltea.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * products 数据访问（模块边界：仅 product 模块可读写）
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
