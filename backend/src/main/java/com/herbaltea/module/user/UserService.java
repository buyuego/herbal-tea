package com.herbaltea.module.user;

import com.herbaltea.module.user.entity.User;

/**
 * 用户模块（C 端会员：users / user_addresses / user_points_accounts）
 *
 * <p>职责：微信小程序登录、会员资料、收货地址、积分账户（与营销模块积分规则联动）。
 */
public interface UserService {

    /** 小程序 code2session 登录：首次自动注册，签发双令牌（15.1） */
    String wxLogin(String code, String deviceFingerprint);

    /** 刷新令牌 */
    String refresh(String refreshToken);

    /** 设备指纹登记（A5 五维哈希：UA/Canvas/WebGL/字体/屏幕），异地强制短信验证 */
    void bindDevice(Long userId, String fingerprint, String ip);

    /** 会员信息 */
    User getProfile(Long userId);
}
