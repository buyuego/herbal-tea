package com.herbaltea.module.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.infrastructure.security.JwtUtil;
import com.herbaltea.infrastructure.web.AuthInterceptor;
import com.herbaltea.module.user.dto.AddressRequest;
import com.herbaltea.module.user.dto.UserLoginVO;
import com.herbaltea.module.user.dto.UserProfileVO;
import com.herbaltea.module.user.dto.WxLoginRequest;
import com.herbaltea.module.user.entity.User;
import com.herbaltea.module.user.entity.UserAddress;
import com.herbaltea.module.user.mapper.UserAddressMapper;
import com.herbaltea.module.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 用户模块实现（C 端微信登录 + 会员 + 收货地址，设计 v10 §15.1 / A5 / D12 / D13 / R9）
 *
 * <p>关键决策：
 * <ul>
 *   <li>身份凭证 = 微信 openid（小程序端设备安全由微信平台保障），不做 C 端指纹强拦；
 *       指纹仅作登记（Redis 90 天信任，A5 语义对齐骨架接口）</li>
 *   <li>双令牌完全复用 JwtUtil（principalType=USER）+ Redis 刷新会话轮换（D12）；
 *       users.token_version（V3 补列）实现 R9 秒级吊销</li>
 *   <li>dev 走 mock 兑换（code → mock-openid-&lt;code&gt;）；prod 强制真实 code2session（未配置快速失败）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 刷新令牌会话存储（D12 轮换）：refresh:session:{jti} → USER:{userId} */
    private static final String REFRESH_SESSION_PREFIX = "refresh:session:";

    /** 设备指纹信任（A5）：device:trust:{userId} → {fingerprint}，90 天滚动续期 */
    private static final String DEVICE_TRUST_PREFIX = "device:trust:";
    private static final Duration DEVICE_TRUST_TTL = Duration.ofDays(90);

    private final UserMapper userMapper;
    private final UserAddressMapper userAddressMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;
    private final AuthInterceptor authInterceptor;

    @Value("${app.jwt.refresh-token-ttl:30d}")
    private Duration refreshTtl;

    /** dev 默认 mock 登录；生产 application-prod.yml 强制 false */
    @Value("${app.wx.mock:true}")
    private boolean wxMock;

    @Value("${app.wx.appid:}")
    private String wxAppid;

    /** token_version 比对器：注入鉴权拦截器（USER 主体，R9） */
    @PostConstruct
    void wireVersionValidator() {
        authInterceptor.registerVersionValidator("USER", (type, userId) -> {
            User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .select(User::getTokenVersion, User::getStatus)
                    .eq(User::getId, userId));
            return (u == null || u.getStatus() == null || u.getStatus() != User.STATUS_ENABLED)
                    ? null : (long) u.getTokenVersion();
        });
    }

    @Override
    @Transactional
    public UserLoginVO wxLogin(WxLoginRequest req, String ip) {
        String openid = resolveOpenid(req.code());
        User user = userMapper.selectByOpenid(openid);
        boolean firstLogin = user == null;
        if (firstLogin) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname(req.nickname());
            user.setAvatarUrl(req.avatarUrl());
            user.setStatus(User.STATUS_ENABLED);
            user.setTokenVersion(0);
            userMapper.insert(user);
            log.info("用户首次登录自动注册 openid={} ip={}", openid, ip);
        }
        if (user.getStatus() == null || user.getStatus() != User.STATUS_ENABLED) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已禁用，请联系客服");
        }
        if (StringUtils.hasText(req.deviceFingerprint())) {
            bindDevice(user.getId(), req.deviceFingerprint(), ip);
        }
        log.info("用户 {} 微信登录成功 openid={} ip={}", user.getId(), openid, ip);
        return issueTokenPair(user.getId(), "USER", user.getTokenVersion(), firstLogin);
    }

    @Override
    @Transactional
    public UserLoginVO refresh(String refreshToken) {
        Claims claims = jwtUtil.parse(refreshToken);
        if (!"refresh".equals(claims.get("kind", String.class))) {
            throw new BizException(ResultCode.UNAUTHORIZED, "无效的刷新令牌");
        }
        String jti = JwtUtil.sessionId(claims);
        String session = redis.opsForValue().get(REFRESH_SESSION_PREFIX + jti);
        if (session == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录");
        }
        Long userId = Long.valueOf(claims.getSubject());
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .select(User::getTokenVersion, User::getStatus)
                .eq(User::getId, userId));
        if (user == null || user.getStatus() != User.STATUS_ENABLED
                || user.getTokenVersion() != JwtUtil.tokenVersion(claims).intValue()) {
            redis.delete(REFRESH_SESSION_PREFIX + jti);
            throw new BizException(ResultCode.TOKEN_REVOKED, "登录已失效，请重新登录");
        }
        // 轮换：旧 jti 作废，签发新双令牌
        redis.delete(REFRESH_SESSION_PREFIX + jti);
        log.info("用户 {} 刷新令牌已轮换", userId);
        return issueTokenPair(userId, "USER", user.getTokenVersion(), false);
    }

    @Override
    public void logout(Long userId) {
        int rows = userMapper.bumpTokenVersion(userId);
        if (rows == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        log.info("用户 {} 已登出，token_version 已递增（R9 秒级吊销）", userId);
    }

    @Override
    public UserProfileVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return UserProfileVO.from(user);
    }

    @Override
    public void updateProfile(Long userId, String nickname, String avatarUrl) {
        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId);
        boolean any = false;
        if (StringUtils.hasText(nickname)) {
            uw.set(User::getNickname, nickname);
            any = true;
        }
        if (StringUtils.hasText(avatarUrl)) {
            uw.set(User::getAvatarUrl, avatarUrl);
            any = true;
        }
        if (!any) {
            return;
        }
        userMapper.update(null, uw);
    }

    @Override
    public void bindDevice(Long userId, String fingerprint, String ip) {
        // A5：指纹仅存哈希；Redis 90 天信任，每次登录滚动续期（last_used 语义）
        redis.opsForValue().set(DEVICE_TRUST_PREFIX + userId, fingerprint, DEVICE_TRUST_TTL);
        log.debug("用户 {} 设备指纹登记 ip={}", userId, ip);
    }

    // ===== 收货地址 =====

    @Override
    public List<UserAddress> listAddresses(Long userId) {
        return userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId));
    }

    @Override
    @Transactional
    public UserAddress addAddress(Long userId, AddressRequest req) {
        if (Integer.valueOf(1).equals(req.isDefault())) {
            clearDefault(userId);
        }
        UserAddress addr = toEntity(userId, req, null);
        userAddressMapper.insert(addr);
        return addr;
    }

    @Override
    @Transactional
    public UserAddress updateAddress(Long userId, Long addressId, AddressRequest req) {
        UserAddress existing = owned(userId, addressId);
        if (Integer.valueOf(1).equals(req.isDefault())) {
            clearDefault(userId);
        }
        UserAddress addr = toEntity(userId, req, existing.getId());
        userAddressMapper.updateById(addr);
        return addr;
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress existing = owned(userId, addressId);
        userAddressMapper.deleteById(existing.getId());
    }

    // ===== 私有方法 =====

    /** dev mock：code 确定性映射 openid（联测同一 code 即同一用户）；prod 走真实 code2session */
    private String resolveOpenid(String code) {
        if (wxMock) {
            return "mock-openid-" + code;
        }
        if (!StringUtils.hasText(wxAppid)) {
            throw new BizException(ResultCode.BIZ_ERROR, "微信登录未配置，请联系管理员");
        }
        // TODO: 生产接入微信 code2session：
        //   GET https://api.weixin.qq.com/sns/jscode2session?appid=&secret=&js_code=&grant_type=authorization_code
        //   → openid/unionid/session_key（session_key 服务端留存不落库）
        throw new BizException(ResultCode.BIZ_ERROR, "微信登录服务暂未开通，请联系管理员");
    }

    /** 签发双令牌（访问 2h + 刷新 30d），刷新会话写入 Redis 供轮换校验 */
    private UserLoginVO issueTokenPair(Long userId, String principalType, Integer tokenVersion, boolean firstLogin) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        long ver = tokenVersion == null ? 0L : tokenVersion;
        String accessToken = jwtUtil.createAccessToken(userId, principalType, sessionId, ver);
        String refreshToken = jwtUtil.createRefreshToken(userId, principalType, sessionId, ver);
        redis.opsForValue().set(REFRESH_SESSION_PREFIX + sessionId,
                principalType + ":" + userId, refreshTtl);
        return new UserLoginVO(accessToken, refreshToken, "Bearer", (int) refreshTtl.toSeconds(), firstLogin);
    }

    /** 清空该用户全部默认地址（新增/修改默认地址前调用，同事务） */
    private void clearDefault(Long userId) {
        userAddressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .set(UserAddress::getIsDefault, 0));
    }

    /** 归属校验：地址必须属于当前用户，否则 40400 */
    private UserAddress owned(Long userId, Long addressId) {
        UserAddress addr = userAddressMapper.selectById(addressId);
        if (addr == null || !addr.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "地址不存在");
        }
        return addr;
    }

    private UserAddress toEntity(Long userId, AddressRequest req, Long id) {
        UserAddress addr = new UserAddress();
        addr.setId(id);
        addr.setUserId(userId);
        addr.setReceiverName(req.receiverName());
        addr.setPhone(req.phone());
        addr.setProvince(req.province());
        addr.setCity(req.city());
        addr.setDistrict(req.district());
        addr.setDetail(req.detail());
        addr.setIsDefault(req.isDefault() == null ? 0 : req.isDefault());
        return addr;
    }
}
