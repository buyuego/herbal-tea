package com.herbaltea.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * users 表实体（V1__schema.sql）
 *
 * <p>tokenVersion 对应 R9 即时吊销（JWT claims.ver 比对）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("users")
public class User extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long userId;

    private String openid;

    private String unionid;

    private String nickname;

    private String avatarUrl;

    private String phone;

    /** 积分账户余额（冗余，与 user_points_accounts 一致；写积分走营销模块接口） */
    private Integer pointsBalance;

    /** R9 即时吊销版本号 */
    private Long tokenVersion;

    private Integer status;
}
