package com.herbaltea.module.store.dto;

import lombok.Data;

/**
 * 门店管理员列表 VO（store_admins 联查 admin_users）
 */
@Data
public class StoreAdminVO {

    /** store_admins.id */
    private Long id;
    private Long adminId;
    private Long storeId;

    /** 1店主 / 0普通店员 */
    private Integer isOwner;

    /** 0移除 / 1正常 */
    private Integer status;

    /** 联查：登录名 */
    private String username;
    /** 联查：姓名 */
    private String realName;
    /** 联查：手机号 */
    private String phone;
    /** 联查：角色 id */
    private Long roleId;
}
