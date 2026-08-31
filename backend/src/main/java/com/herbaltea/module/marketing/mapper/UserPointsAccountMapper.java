package com.herbaltea.module.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.marketing.entity.UserPointsAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * user_points_accounts 数据访问（积分账户）
 *
 * <p>积分账户不放宽到"先查后写"：累计写入用 upsert（首次自动建户），
 * 扣减用带 {@code balance >= amount} 条件的原子 UPDATE，零超扣。
 */
@Mapper
public interface UserPointsAccountMapper extends BaseMapper<UserPointsAccount> {

    /**
     * 累计获得（发放 / 首次自动建户）：无账户则插入，有账户则累加。
     *
     * @return 影响行数（MySQL upsert：1 插入 / 2 更新）
     */
    @Insert("""
            INSERT INTO user_points_accounts (user_id, balance, total_earned, total_used, total_expired, version)
            VALUES (#{userId}, #{amount}, #{amount}, 0, 0, 0)
            ON DUPLICATE KEY UPDATE
                balance = balance + #{amount},
                total_earned = total_earned + #{amount},
                version = version + 1
            """)
    int upsertEarn(@Param("userId") Long userId, @Param("amount") Long amount);

    /**
     * 原子扣减（下单抵扣）：仅当 {@code balance >= amount} 时扣减，total_used 累加。
     *
     * @return 影响行数（0 = 账户不存在或积分余额不足）
     */
    @Update("""
            UPDATE user_points_accounts
               SET balance = balance - #{amount},
                   total_used = total_used + #{amount},
                   version = version + 1
             WHERE user_id = #{userId} AND balance >= #{amount}
            """)
    int deductIfEnough(@Param("userId") Long userId, @Param("amount") Long amount);

    /**
     * 过期清零扣减：按批次积分扣减并钳零（用户可能已提前用掉部分积分）。
     *
     * <p>MySQL 的 SET 子句中右侧引用的是列的更新前值，故
     * {@code total_expired + LEAST(balance, amount)} 累加的是「实际被清零的数量」。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE user_points_accounts
               SET balance = GREATEST(balance - #{amount}, 0),
                   total_expired = total_expired + LEAST(balance, #{amount}),
                   version = version + 1
             WHERE user_id = #{userId}
            """)
    int expireToBalance(@Param("userId") Long userId, @Param("amount") Long amount);

    @Select("SELECT * FROM user_points_accounts WHERE user_id = #{userId} LIMIT 1")
    UserPointsAccount selectByUserId(@Param("userId") Long userId);
}
