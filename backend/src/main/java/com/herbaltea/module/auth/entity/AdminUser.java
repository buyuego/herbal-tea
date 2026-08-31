package com.herbaltea.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * admin_users 表实体（对齐 V1__schema.sql 权威结构）
 *
 * <p>说明：admin_users 无 version 乐观锁列，token_version 为「禁用/改密/删角色时 +1，
 * JWT 即时吊销」（R9/D12）专用，与乐观锁无关。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_users")
public class AdminUser extends BaseEntity {

    /** 正常 */
    public static final int STATUS_ENABLED = 1;
    /** 禁用 */
    public static final int STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名 */
    private String username;

    /** BCrypt 哈希 */
    private String passwordHash;

    private String realName;

    /** 手机号（短信验证） */
    private String phone;

    /** 角色 id（1:1 单绑定，5.1） */
    private Long roleId;

    /** 0禁用 / 1正常 */
    private Integer status;

    /** 禁用/改密/删角色时 +1，JWT 即时吊销（R9） */
    private Integer tokenVersion;

    private java.time.LocalDateTime lastLoginAt;
}
