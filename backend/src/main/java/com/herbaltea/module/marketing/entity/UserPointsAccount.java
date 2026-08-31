package com.herbaltea.module.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * user_points_accounts 表实体（用户积分账户，品牌级、全店通用）
 *
 * <p>注意：本表 DDL 只有 updated_at（无 created_at），故不继承 {@code BaseEntity}，
 * 避免 INSERT 时带上不存在的 created_at 列（与 settlement_items 同类坑）。
 * <p>所有余额变动均为单条原子 SQL（{@code balance >= amount} 条件 + version +1），
 * 与库存扣减（16.4 ③）同款手法，无分布式锁。
 */
@Data
@TableName("user_points_accounts")
public class UserPointsAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 当前可用积分 */
    private Long balance;

    /** 累计获得 */
    private Long totalEarned;

    /** 累计使用 */
    private Long totalUsed;

    /** 累计过期清零 */
    private Long totalExpired;

    /** 乐观锁 */
    @Version
    private Integer version;

    private LocalDateTime updatedAt;
}
