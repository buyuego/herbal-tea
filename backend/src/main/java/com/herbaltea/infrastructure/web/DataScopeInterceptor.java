package com.herbaltea.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Data Scope 数据权限拦截器（拦截器链第 2 道）
 *
 * <p>根据 {@link UserContext#dataScope} 为请求注入数据范围：
 * <ul>
 *   <li>ALL（总部管理员）：不追加条件，可看全部门店</li>
 *   <li>STORE（门店管理员）：Service 层通过 UserContext.storeId() 强制追加 store_id 条件</li>
 *   <li>防越权首道拦截：跨店资源访问在 Service 层二次校验（此处仅做语义标记与透传）</li>
 * </ul>
 * 实现要点：业务查询统一走 {@code TenantAware} 封装（示例见 module-settlement），
 * 禁止在 Controller 直接拼 store_id。
 */
@Component
public class DataScopeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        UserContext ctx = UserContext.get();
        if (ctx == null) {
            return true;
        }
        if (ctx.getType() == UserContext.PrincipalType.ADMIN) {
            if (ctx.getStoreId() == null || ctx.getStoreId() == 0L) {
                ctx.setDataScope("ALL");
            } else {
                ctx.setDataScope("STORE");
            }
        } else {
            ctx.setDataScope("SELF");
        }
        return true;
    }
}
