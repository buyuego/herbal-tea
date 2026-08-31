package com.herbaltea.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.herbaltea.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
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
}
