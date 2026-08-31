package com.herbaltea.module.auth;

/**
 * 权限模块（B 端账号体系：admin_users / roles / permissions / role_permissions / device_trusts）
 *
 * <p>职责：
 * <ul>
 *   <li>管理员登录 / 刷新令牌轮换 / 登出（token_version +1 即时吊销，D12/R9）</li>
 *   <li>设备会话吊销 + 活跃会话上限 5（D13）</li>
 *   <li>角色权限校验（敏感权限超管专属，V2 初始数据）</li>
 * </ul>
 */
public interface AuthService {

    /** B 端管理员密码登录（登录失败计数限流 5 次锁定 15 分钟，15.1） */
    AuthServiceImpl.TokenPair adminLogin(String username, String password);

    /** 刷新令牌轮换（旧 refresh 作废，D12） */
    AuthServiceImpl.TokenPair refresh(String refreshToken);

    /** 登出 / 设备吊销：token_version +1 全会话失效 + device_trusts 置失效（D13） */
    void revoke(Long adminId);

    /**
     * 切换当前门店（MULTI_STORE，v14）：目标店须在本人正常绑定内（实时查库，不信任 JWT 快照）。
     * 重签令牌：sid=目标店、sids=全量绑定；返回新双令牌。
     */
    AuthServiceImpl.TokenPair switchStore(Long adminId, Long targetStoreId);

    /** 当前管理员信息（登录后拉取账号/角色/权限码，驱动前端菜单与路由权限过滤） */
    AuthServiceImpl.AdminProfile me(Long adminId);
}
