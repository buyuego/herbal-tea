package com.herbaltea.module.product.dto;

import lombok.Data;

/**
 * 平台商品分页查询参数（keyword 匹配名称/副标题；status 为 null 查全部）
 */
@Data
public class ProductPageQuery {

    /** 关键词（名称/副标题模糊） */
    private String keyword;

    /** 分类（null = 全部） */
    private Long categoryId;

    /** 状态（0下架/1在售，null = 全部） */
    private Integer status;

    private long page = 1;

    private long size = 10;
}
