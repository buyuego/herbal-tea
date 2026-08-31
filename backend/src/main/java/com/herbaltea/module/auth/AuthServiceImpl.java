package com.herbaltea.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.infrastructure.security.JwtUtil;
import com.herbaltea.infrastructure.web.AuthInterceptor;
import com.herbaltea.infrastructure.web.RateLimitInterceptor;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.auth.entity.AdminUser;
import com.herbaltea.module.auth.mapper.AdminUserMapper;
import com.herbaltea.module.auth.mapper.DeviceTrustMapper;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 权限模块实现（B 端账号体系，设计文档 15.1 / D12 / D13 / R9）
 *
 * <p>模块边界：仅本模块可读写 admin_users / device_trusts 等权限表；
 * 其余模块通过本接口校验权限（禁止跨模块直读表）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 登录失败锁定阈值（15.1：5 次锁 15 分钟） */
    private static final int LOGIN_FAIL_MAX = 5;
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(15);

    /** 刷新令牌会话存储（D12 轮换）：refresh:session:{jti} → ADMIN:{adminId} */
    private static final String REFRESH_SESSION_PREFIX = "refresh:session:";

    /** 不存在用户的假哈希：执行相同 BCrypt 成本，防用户名时序枚举 */
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi6X8H5QoYb7m8vZzU5pC1k3yY6nW4S";

    private final AdminUserMapper adminUserMapper;
    private final DeviceTrustMapper deviceTrustMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;
    private final RateLimitInterceptor rateLimit;
    private final AuthInterceptor authInterceptor;

    @Value("${app.jwt.refresh-token-ttl:30d}")
    private Duration refreshTtl;

    /** token_version 比对器：注入鉴权拦截器，实现 R9 即时吊销 */
    @PostConstruct
    void wireVersionValidator() {
        authInterceptor.setVersionValidator((type, userId) -> {
            if ("ADMIN".equals(type)) {
                AdminUser admin = adminUserMapper.selectOne(
                        new LambdaQueryWrapper<AdminUser>()
                                .select(AdminUser::getTokenVersion)
                                .eq(AdminUser::getId, userId));
                return admin == null ? null : (long) admin.getTokenVersion();
            }
            // C 端：users 表无 token_version 列（V1 DDL），C 端登录未实现前一律拒绝
            return null;
        });
    }

    @Override
    public TokenPair adminLogin(String username, String password) {
        AdminUser admin = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, username));
        if (admin == null) {
            // 防时序枚举：假哈希走一次 BCrypt，再统一报错
            passwordEncoder.matches(password, DUMMY_HASH);
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (admin.getStatus() == null || admin.getStatus() != AdminUser.STATUS_ENABLED) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            // 仅失败计数（5 次锁定 15 分钟，15.1）
            rateLimit.check("login", "admin:" + username, LOGIN_FAIL_MAX, LOGIN_FAIL_WINDOW);
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        // 登录成功：清除失败计数 + 更新最近登录时间
        redis.delete("limit:login:admin:" + username);
        adminUserMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AdminUser>()
                .eq(AdminUser::getId, admin.getId())
                .set(AdminUser::getLastLoginAt, LocalDateTime.now()));
        log.info("管理员 {} 登录成功", admin.getUsername());
        return issueTokenPair(admin.getId(), "ADMIN", admin.getTokenVersion());
    }

    @Override
    @Transactional
    public TokenPair refresh(String refreshToken) {
        Claims claims = jwtUtil.parse(refreshToken);
        if (!"refresh".equals(claims.get("kind", String.class))) {
            throw new BizException(ResultCode.UNAUTHORIZED, "无效的刷新令牌");
        }
        String jti = JwtUtil.sessionId(claims);
        String session = redis.opsForValue().get(REFRESH_SESSION_PREFIX + jti);
        if (session == null) {
            // 已轮换 / 已登出 / 已过期：拒绝并提示重新登录
            throw new BizException(ResultCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录");
        }
        Long adminId = Long.valueOf(claims.getSubject());
        // 吊销兜底：刷新时仍比对 token_version（R9）
        AdminUser admin = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>()
                        .select(AdminUser::getTokenVersion, AdminUser::getStatus)
                        .eq(AdminUser::getId, adminId));
        if (admin == null || admin.getStatus() != AdminUser.STATUS_ENABLED
                || admin.getTokenVersion() != JwtUtil.tokenVersion(claims).intValue()) {
            redis.delete(REFRESH_SESSION_PREFIX + jti);
            throw new BizException(ResultCode.TOKEN_REVOKED, "登录已失效，请重新登录");
        }
        // 轮换：旧 jti 作废，签发新双令牌
        redis.delete(REFRESH_SESSION_PREFIX + jti);
        log.info("管理员 {} 刷新令牌已轮换", adminId);
        return issueTokenPair(adminId, "ADMIN", admin.getTokenVersion());
    }

    @Override
    @Transactional
    public void revoke(Long adminId) {
        // R9：token_version + 1，已签发 JWT 全部失效（秒级吊销）
        int rows = adminUserMapper.bumpTokenVersion(adminId);
        if (rows == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "账号不存在");
        }
        // D13：设备级吊销（device_trusts 全部置失效；按设备指纹精确吊销由业务层扩展）
        deviceTrustMapper.revokeByAdmin(adminId);
        log.info("管理员 {} 已登出，token_version 已递增，设备信任已吊销", adminId);
    }

    /** 签发双令牌（访问 2h + 刷新 30d），刷新会话写入 Redis 供轮换校验 */
    private TokenPair issueTokenPair(Long adminId, String principalType, Integer tokenVersion) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        long ver = tokenVersion == null ? 0L : tokenVersion;
        String accessToken = jwtUtil.createAccessToken(adminId, principalType, sessionId, ver);
        String refreshToken = jwtUtil.createRefreshToken(adminId, principalType, sessionId, ver);
        // 刷新会话：TTL 与刷新令牌一致（30d），吊销时 token_version 兜底
        redis.opsForValue().set(REFRESH_SESSION_PREFIX + sessionId,
                principalType + ":" + adminId, refreshTtl);
        return new TokenPair(accessToken, refreshToken, "Bearer", (int) refreshTtl.toSeconds());
    }

    /** 登录/刷新成功返回的令牌对 */
    public record TokenPair(String accessToken, String refreshToken, String tokenType, int expiresIn) {
    }
}
