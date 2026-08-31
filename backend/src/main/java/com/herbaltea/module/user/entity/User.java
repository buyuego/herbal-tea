package com.herbaltea.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * users 表实体（V1__schema.sql + V3__user_token_version.sql）
 *
 * <ul>
 *   <li>主键 id 为 AUTO_INCREMENT（覆盖全局 assign_id 策略）</li>
 *   <li>tokenVersion 对应 R9 即时吊销（JWT claims.ver 比对，V3 补列）</li>
 *   <li>积分账户余额查询走 user_points_accounts（营销模块），本表无冗余列</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("users")
public class User extends BaseEntity {

    /** 正常 */
    public static final int STATUS_ENABLED = 1;
    /** 禁用 */
    public static final int STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    private String unionid;

    private String nickname;

    private String avatarUrl;

    private String phone;

    private Integer status;

    /** R9 即时吊销版本号（V3 补列） */
    private Integer tokenVersion;
}
