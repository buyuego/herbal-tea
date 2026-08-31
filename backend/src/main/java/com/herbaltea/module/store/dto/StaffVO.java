package com.herbaltea.module.store.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工列表 VO（store_admins 联查 admin_users + roles）
 *
 * <p>仅展示员工信息；密码哈希/令牌等敏感字段绝不透出。
 */
@Data
public class StaffVO {

    /** admin_users.id */
    private Long adminId;

    /** 登录名 */
    private String username;

    /** 姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** admin_users.status：0禁用 / 1正常 */
    private Integer adminStatus;

    /** store_admins.status：1 正常绑定 / 0 已移除（软删可复绑） */
    private Integer bindStatus;

    /** 角色 id（员工应为 5 = STORE_STAFF） */
    private Long roleId;

    /** 角色名（联查 roles） */
    private String roleName;

    /** 1店主 / 0普通店员（员工管理接口操作目标限非店主） */
    private Integer isOwner;

    /** store_admins.created_at（绑定时间） */
    private LocalDateTime boundAt;

    /** 最近登录时间 */
    private LocalDateTime lastLoginAt;
}
