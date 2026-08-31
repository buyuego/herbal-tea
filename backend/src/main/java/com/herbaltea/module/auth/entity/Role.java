package com.herbaltea.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * roles 表实体（对齐 V1__schema.sql 权威结构）
 *
 * <p>角色规则（v10 角色权限管理落地）：
 * <ul>
 *   <li>单角色 + DataScope（GLOBAL/MULTI_STORE/SINGLE_STORE），自定义角色上限 10</li>
 *   <li>level：1 平台级 / 2 店铺级（权限互斥校验依据）</li>
 *   <li>is_preset=1 预设角色不可删除（超管/财务/仓管/店长/店员）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("roles")
public class Role extends BaseEntity {

    /** 平台级角色 */
    public static final int LEVEL_PLATFORM = 1;
    /** 店铺级角色 */
    public static final int LEVEL_STORE = 2;

    /** 预设角色（不可删除） */
    public static final int PRESET_YES = 1;
    /** 自定义角色（可删除，上限 10 个） */
    public static final int PRESET_NO = 0;

    /** 自定义角色数量上限 */
    public static final int MAX_CUSTOM_ROLES = 10;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码（唯一，创建后不可改） */
    private String code;

    /** 角色名 */
    private String name;

    /** 数据范围：GLOBAL / MULTI_STORE / SINGLE_STORE */
    private String dataScope;

    /** 1 平台级 / 2 店铺级（权限互斥校验依据） */
    private Integer level;

    /** 1 预设角色（不可删） */
    private Integer isPreset;

    /** 角色描述 */
    private String description;
}
