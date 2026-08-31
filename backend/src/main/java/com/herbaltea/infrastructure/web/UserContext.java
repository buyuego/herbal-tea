package com.herbaltea.infrastructure.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录上下文（ThreadLocal，由 AuthInterceptor 填充）
 *
 * <p>类型区分：C 端用户（小程序）/ B 端管理员（Web 后台），Data Scope 依据 userId + dataScope 注入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {

    /** 身份类型：USER（C 端会员）/ ADMIN（B 端管理员） */
    public enum PrincipalType { USER, ADMIN }

    private PrincipalType type;
    private Long userId;

    /** B 端：管理员 id（admin_users） */
    private Long adminId;

    /** B 端：所属店铺 id（总部为 0，门店管理员为对应 store_id） */
    private Long storeId;

    /** B 端：数据范围（ALL=总部全量 / STORE=本店 / SELF=本人），由 Data Scope 拦截器转 SQL 条件 */
    private String dataScope;

    /** C 端：设备信任记录 id（device_trusts，A5 设备指纹） */
    private Long deviceTrustId;

    /** 登录会话 id（JWT jti，吊销时与 token_version 联合校验，R9/D13） */
    private String sessionId;

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    public static void set(UserContext ctx) {
        HOLDER.set(ctx);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static Long userId() {
        UserContext c = HOLDER.get();
        return c == null ? null : c.userId;
    }

    public static Long storeId() {
        UserContext c = HOLDER.get();
        return c == null ? null : c.storeId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
