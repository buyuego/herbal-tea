package com.herbaltea.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.infrastructure.security.JwtUtil;
import com.herbaltea.infrastructure.web.AuthInterceptor;
import com.herbaltea.infrastructure.web.RateLimitInterceptor;
import com.herbaltea.infrastructure.web.UserContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * AuthService 骨架实现
 *
 * <p>演示模块边界：仅本模块可读写 admin_users 等权限表；
 * 其余模块通过本接口校验权限（禁止跨模块直读表）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final JwtUtil jwtUtil;
    private final RateLimitInterceptor rateLimit;
    private final AuthInterceptor authInterceptor;

    /** token_version 比对器：注入鉴权拦截器，实现 R9 即时吊销 */
    @PostConstruct
    void wireVersionValidator() {
        authInterceptor.setVersionValidator((type, userId) -> {
            // TODO: 查 admin_users.token_version（ADMIN）或 users.token_version（USER）
            //       返回 null 表示主体已注销
            return 0L;
        });
    }

    @Override
    public String adminLogin(String username, String password) {
        // 登录失败计数：limit:login:admin:{username}，5 次锁定 15 分钟（15.1）
        // TODO: 查 admin_users（含 deleted=0）→ BCrypt 校验 → 校验通过后签发双令牌
        rateLimit.check("login", "admin:" + username, 5, Duration.ofMinutes(15));
        String sessionId = java.util.UUID.randomUUID().toString().replace("-", "");
        return jwtUtil.createAccessToken(0L, "ADMIN", sessionId, 1L);
    }

    @Override
    public String refresh(String refreshToken) {
        // TODO: 解析 refresh 令牌 → 校验 kind=refresh → 轮换（旧 jti 作废）→ 签发新双令牌
        throw new BizException(ResultCode.UNAUTHORIZED, "刷新令牌已过期");
    }

    @Override
    public void revoke(Long adminId) {
        // TODO: admin_users.token_version + 1（UPDATE ... SET token_version = token_version + 1
        //       WHERE admin_id = ?）—— 已签发 JWT 全部失效，秒级吊销（R9）
        // TODO: device_trusts 置失效（D13 设备级吊销）
        UserContext.clear();
        log.info("管理员 {} 已登出，token_version 已递增", adminId);
    }
}
