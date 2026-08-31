package com.herbaltea.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.user.dto.MemberQuery;
import com.herbaltea.module.user.dto.MemberVO;
import com.herbaltea.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * C 端用户 Mapper（users）
 *
 * <p>openid 唯一索引（uk_users_openid）为首次注册并发兜底；
 * bumpTokenVersion 对应 R9 即时吊销（JWT claims.ver 不匹配即拒绝）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE openid = #{openid} LIMIT 1")
    User selectByOpenid(String openid);

    @Update("UPDATE users SET token_version = token_version + 1, updated_at = NOW() WHERE id = #{id}")
    int bumpTokenVersion(Long id);

    // ==================== B 端会员管理（v26） ====================

    /**
     * 会员分页：users LEFT JOIN 积分账户 + 订单聚合子查询（仅统计已支付及之后的有效订单）。
     */
    @Select("""
            SELECT u.id AS id, u.openid AS openid, u.nickname AS nickname,
                   u.avatar_url AS avatarUrl, u.phone AS phone, u.status AS status,
                   u.created_at AS createdAt,
                   COALESCE(pa.balance, 0) AS pointsBalance,
                   COALESCE(pa.total_earned, 0) AS totalEarned,
                   COALESCE(pa.total_used, 0) AS totalUsed,
                   (SELECT COUNT(*) FROM orders o
                     WHERE o.user_id = u.id AND o.status IN (20,30,40,50,90)) AS orderCount,
                   (SELECT COALESCE(SUM(o.pay_amount), 0) FROM orders o
                     WHERE o.user_id = u.id AND o.status IN (20,30,40,50,90)) AS payTotalAmount,
                   (SELECT MAX(o.created_at) FROM orders o
                     WHERE o.user_id = u.id AND o.status IN (20,30,40,50,90)) AS lastOrderAt
            FROM users u
                     LEFT JOIN user_points_accounts pa ON pa.user_id = u.id
            WHERE (#{q.status} IS NULL OR u.status = #{q.status})
              AND (#{q.keyword} IS NULL OR #{q.keyword} = ''
                   OR u.nickname LIKE CONCAT('%', #{q.keyword}, '%')
                   OR u.phone LIKE CONCAT('%', #{q.keyword}, '%')
                   OR u.openid LIKE CONCAT('%', #{q.keyword}, '%'))
            ORDER BY u.id DESC
            """)
    IPage<MemberVO> pageMembers(IPage<?> page, @Param("q") MemberQuery q);

    /** 单个会员概览（与 pageMembers 同口径，供详情复用） */
    @Select("""
            SELECT u.id AS id, u.openid AS openid, u.nickname AS nickname,
                   u.avatar_url AS avatarUrl, u.phone AS phone, u.status AS status,
                   u.created_at AS createdAt,
                   COALESCE(pa.balance, 0) AS pointsBalance,
                   COALESCE(pa.total_earned, 0) AS totalEarned,
                   COALESCE(pa.total_used, 0) AS totalUsed,
                   (SELECT COUNT(*) FROM orders o
                     WHERE o.user_id = u.id AND o.status IN (20,30,40,50,90)) AS orderCount,
                   (SELECT COALESCE(SUM(o.pay_amount), 0) FROM orders o
                     WHERE o.user_id = u.id AND o.status IN (20,30,40,50,90)) AS payTotalAmount,
                   (SELECT MAX(o.created_at) FROM orders o
                     WHERE o.user_id = u.id AND o.status IN (20,30,40,50,90)) AS lastOrderAt
            FROM users u
                     LEFT JOIN user_points_accounts pa ON pa.user_id = u.id
            WHERE u.id = #{userId}
            """)
    MemberVO selectMemberVO(@Param("userId") Long userId);
}
