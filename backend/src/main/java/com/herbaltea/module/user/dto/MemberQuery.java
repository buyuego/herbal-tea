package com.herbaltea.module.user.dto;

import lombok.Data;

/**
 * B 端会员分页查询（v26）
 *
 * <p>筛选：关键词（昵称 / 手机号 / openid 模糊）、状态（0禁用 / 1正常）。
 */
@Data
public class MemberQuery {

    /** 关键词（昵称 / 手机号 / openid 模糊匹配） */
    private String keyword;

    /** 状态（0禁用 / 1正常，null = 全部） */
    private Integer status;

    private long page = 1;

    private long size = 10;
}
