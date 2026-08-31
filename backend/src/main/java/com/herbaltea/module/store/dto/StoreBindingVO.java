package com.herbaltea.module.store.dto;

import lombok.Data;

/**
 * 当前账号可切换门店 VO（MULTI_STORE：my-stores 返回）
 *
 * <p>联查 store_admins → stores；current 标记当前上下文门店（JWT sid），前端据此渲染切换下拉。
 */
@Data
public class StoreBindingVO {

    private Long storeId;

    private String storeNo;

    private String storeName;

    /** 1店主（主店，登录默认进入） */
    private Integer isOwner;

    /** 是否当前上下文门店（sid） */
    private Boolean current;
}
