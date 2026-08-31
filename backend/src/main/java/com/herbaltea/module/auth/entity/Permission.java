package com.herbaltea.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseCreatedOnlyEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * permissions 表实体（对齐 V1__schema.sql 权威结构）
 *
 * <p>三级权限体系（设计 5.5）：type=1 菜单 → type=2 按钮 → type=3 接口，
 * parent_id 构成树；is_sensitive=1 为敏感权限（成本价/打款确认/删除订单/角色配置，
 * 超管专属，v10 授权校验：仅 SUPER_ADMIN 角色可授予）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("permissions")
public class Permission extends BaseCreatedOnlyEntity {

    /** 菜单权限 */
    public static final int TYPE_MENU = 1;
    /** 按钮权限 */
    public static final int TYPE_BUTTON = 2;
    /** 接口权限 */
    public static final int TYPE_API = 3;

    /** 非敏感（普通权限） */
    public static final int SENSITIVE_NO = 0;
    /** 敏感（超管专属） */
    public static final int SENSITIVE_YES = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限编码（如 order:refund:approve） */
    private String code;

    /** 权限名 */
    private String name;

    /** 所属模块 */
    private String module;

    /** 1 菜单 / 2 按钮 / 3 接口 */
    private Integer type;

    /** 父权限 id（菜单树） */
    private Long parentId;

    /** 菜单路由 / 接口路径 */
    private String path;

    /** 1 敏感权限（超管专属） */
    private Integer isSensitive;
}
