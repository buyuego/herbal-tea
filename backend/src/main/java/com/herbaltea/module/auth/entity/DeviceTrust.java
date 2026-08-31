package com.herbaltea.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * device_trusts 表实体（A5/D5 设备信任：常用设备免验证，90 天滚动续期）
 *
 * <p>归属 auth 模块：B 端管理员登录设备信任管理（C 端设备信任后续扩展）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_trusts")
public class DeviceTrust extends BaseEntity {

    /** 有效 */
    public static final int STATUS_ACTIVE = 1;
    /** 已撤销（店主可手动吊销，D13） */
    public static final int STATUS_REVOKED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 管理员 id（admin_users） */
    private Long adminId;

    /** 设备指纹（UA+Canvas+WebGL+字体+屏幕 五维哈希，A5/D5） */
    private String deviceFingerprint;

    /** 设备备注名 */
    private String deviceName;

    /** 0陌生 / 1常用（验证通过 5 次自动升级） */
    private Integer trustLevel;

    /** 累计验证通过次数 */
    private Integer verifyCount;

    private LocalDateTime firstSeenAt;

    private LocalDateTime lastUsedAt;

    /** 信任有效期（默认 90 天，last_used_at 滚动续期，D5） */
    private LocalDateTime expiresAt;

    /** 最近登录 IP（异地强制短信依据，D5） */
    private String lastLoginIp;

    /** IP 归属地 */
    private String lastLoginRegion;

    /** 1有效 / 0撤销 */
    private Integer status;
}
