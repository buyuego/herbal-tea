package com.herbaltea.module.auth;

/**
 * 权限模块（B 端账号体系：admin_users / roles / permissions / role_permissions）
 *
 * <p>职责：
 * <ul>
 *   <li>管理员登录 / 刷新令牌轮换 / 登出（token_version +1 即时吊销，D12）</li>
 *   <li>设备会话吊销 + 活跃会话上限 5（D13）</li>
 *   <li>角色权限校验（敏感权限超管专属，V2 初始数据）</li>
 * </ul>
 */
public interface AuthService {

    /** B 端管理员密码登录（登录失败计数限流 5 次锁定，15.1） */
    String adminLogin(String username, String password);

    /** 刷新令牌轮换（旧 refresh 失效，D12） */
    String refresh(String refreshToken);

    /** 登出 / 设备吊销：token_version +1，全部会话失效（D13 设备级） */
    void revoke(Long adminId);
}
