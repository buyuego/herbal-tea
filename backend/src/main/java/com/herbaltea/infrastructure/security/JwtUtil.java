package com.herbaltea.infrastructure.security;

import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具（设计文档 15.1 / D12）
 *
 * <ul>
 *   <li>访问令牌 2h 短时效；刷新令牌 30d 轮换</li>
 *   <li>claims 携带 token_version（R9 即时吊销）：服务端比对 users.token_version，
 *       不匹配即拒绝——配合数据库实现秒级吊销，无需 Redis 黑名单</li>
 *   <li>session_id（jti）用于 D13 设备级会话吊销与活跃会话上限 5</li>
 * </ul>
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.access-token-ttl}") Duration accessTtl,
                   @Value("${app.jwt.refresh-token-ttl}") Duration refreshTtl) {
        // HS256：密钥需 >= 256bit，生产环境必须通过 JWT_SECRET 注入强随机串
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    /** 签发访问令牌 */
    public String createAccessToken(Long userId, String principalType,
                                    String sessionId, Long tokenVersion) {
        return create(userId, principalType, sessionId, tokenVersion, accessTtl, "access");
    }

    /** 签发刷新令牌 */
    public String createRefreshToken(Long userId, String principalType,
                                     String sessionId, Long tokenVersion) {
        return create(userId, principalType, sessionId, tokenVersion, refreshTtl, "refresh");
    }

    private String create(Long userId, String principalType, String sessionId,
                          Long tokenVersion, Duration ttl, String kind) {
        Date now = new Date();
        return Jwts.builder()
                .id(sessionId)
                .subject(String.valueOf(userId))
                .claim("type", principalType)
                .claim("ver", tokenVersion)
                .claim("kind", kind)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验令牌。
     *
     * @throws BizException 令牌缺失/过期/签名非法
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw BizException.unauthorized("登录已过期，请重新登录");
        }
    }

    public static Long tokenVersion(Claims claims) {
        Object v = claims.get("ver");
        return v == null ? 0L : Long.valueOf(String.valueOf(v));
    }

    public static String sessionId(Claims claims) {
        return claims.getId();
    }
}
