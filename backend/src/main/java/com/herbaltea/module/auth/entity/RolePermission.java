package com.herbaltea.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * role_permissions 表实体（角色-权限关联，对齐 V1__schema.sql）
 *
 * <p>纯关联表（无时间列），由 roles 增删与授权操作维护：
 * 删除角色 → 级联删关联；授权 → 全量覆盖（删旧插新）。
 */
@Data
@TableName("role_permissions")
public class RolePermission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色 id */
    private Long roleId;

    /** 权限 id */
    private Long permissionId;
}
