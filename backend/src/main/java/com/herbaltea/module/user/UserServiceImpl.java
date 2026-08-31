package com.herbaltea.module.user;

import com.herbaltea.infrastructure.security.JwtUtil;
import com.herbaltea.module.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户模块骨架实现
 *
 * <p>待实现（按优先级）：
 * <ol>
 *   <li>wxLogin：WxJava MaService.code2Session → 查/建 users → 签发双令牌（15.1）</li>
 *   <li>bindDevice：五维指纹哈希登记 device_trusts（A5），异地强制短信验证</li>
 *   <li>tokenVersionValidator 用户侧接入（R9）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final JwtUtil jwtUtil;

    @Override
    public String wxLogin(String code, String deviceFingerprint) {
        // TODO: wx.miniapp WxMaService.getUserService().getSessionInfo(code)
        //       → openid 查 users → 不存在则创建（事务）
        //       → 五维指纹比对 device_trusts：命中信任（90 天内同指纹）直接放行，
        //         否则要求短信验证（DEVICE_RISK 40310，A5）
        //       → users.token_version 比对 → 签发 access+refresh
        log.info("[骨架] wxLogin code={} fingerprint={}", code, deviceFingerprint);
        return jwtUtil.createAccessToken(1L, "USER", "dev-session", 1L);
    }

    @Override
    public String refresh(String refreshToken) {
        // TODO: 与 AuthServiceImpl.refresh 共用刷新令牌轮换逻辑（可抽公共组件）
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void bindDevice(Long userId, String fingerprint, String ip) {
        // TODO: device_trusts upsert（五维哈希 + 首次登录 IP/地区），expires_at = now + 90 天
    }

    @Override
    public User getProfile(Long userId) {
        // TODO: 查 users 脱敏返回
        return null;
    }
}
