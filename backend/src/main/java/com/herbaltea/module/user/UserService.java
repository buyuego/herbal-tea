package com.herbaltea.module.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.user.dto.AddressRequest;
import com.herbaltea.module.user.dto.MemberDetailVO;
import com.herbaltea.module.user.dto.MemberQuery;
import com.herbaltea.module.user.dto.MemberVO;
import com.herbaltea.module.user.dto.UserLoginVO;
import com.herbaltea.module.user.dto.UserProfileVO;
import com.herbaltea.module.user.dto.WxLoginRequest;
import com.herbaltea.module.user.entity.UserAddress;

import java.util.List;

/**
 * 用户模块（C 端会员：users / user_addresses）
 *
 * <p>职责：微信小程序登录（15.1）、会员资料、收货地址；
 * 设备指纹登记（A5 五维哈希，Redis 90 天信任）。
 * 积分账户（user_points_accounts）归营销模块，本模块不直读。
 */
public interface UserService {

    /** 小程序 code 登录：首次自动注册，签发双令牌（15.1）；dev 走 mock 兑换 */
    UserLoginVO wxLogin(WxLoginRequest req, String ip);

    /** 刷新令牌轮换（D12：旧 jti 作废，签发新双令牌） */
    UserLoginVO refresh(String refreshToken);

    /** 登出：token_version +1 使全部已签发 JWT 秒级失效（R9） */
    void logout(Long userId);

    /** 会员资料（脱敏） */
    UserProfileVO getProfile(Long userId);

    /** 更新资料（昵称/头像，空值不更新） */
    void updateProfile(Long userId, String nickname, String avatarUrl);

    /** 设备指纹登记（A5 五维哈希 → Redis 信任 90 天，last_used 滚动续期） */
    void bindDevice(Long userId, String fingerprint, String ip);

    // ===== 收货地址 =====

    List<UserAddress> listAddresses(Long userId);

    UserAddress addAddress(Long userId, AddressRequest req);

    UserAddress updateAddress(Long userId, Long addressId, AddressRequest req);

    void deleteAddress(Long userId, Long addressId);

    // ===== B 端会员管理（v26，menu:member 109 / member:edit 220） =====

    /**
     * 会员分页（B 端）：users × 积分账户 × 订单聚合（仅统计已支付及之后的有效订单）
     */
    IPage<MemberVO> pageMembers(MemberQuery query);

    /** 会员详情：概览 + 收货地址 + 最近 20 条积分流水 */
    MemberDetailVO getMemberDetail(Long userId);

    /** 会员启停（0禁用 / 1正常）；禁用同时 token_version +1 即时吊销其 JWT（R9） */
    void updateMemberStatus(Long userId, Integer status);
}
