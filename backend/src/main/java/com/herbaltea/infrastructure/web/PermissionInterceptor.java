package com.herbaltea.infrastructure.web;

import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限校验拦截器（设计文档 2.2 拦截器链第 3 道，位于 AuthInterceptor 之后）。
 *
 * <p>对标注了 {@link RequirePermission} 的接口按权限码校验：
 * <ul>
 *   <li>未登录（白名单路径）→ 放行（鉴权已由 AuthInterceptor 负责）</li>
 *   <li>主体类型未注册校验器（如 C 端 USER 访问 B 端管理接口）→ 40300 拒绝</li>
 *   <li>角色未绑定该权限码 → 40300 拒绝</li>
 * </ul>
 *
 * <p>校验能力由各模块注册（与 {@link AuthInterceptor#registerVersionValidator} 对称）：
 * Auth 模块注册 ADMIN（权限挂角色，role_permissions 表 + Redis 缓存），
 * USER 主体不注册——C 端令牌无法访问任何标注了权限码的管理接口。
 */
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    /** 权限校验器：按主体类型注册 */
    public interface PermissionProvider {
        /** @return 该主体（经角色）是否拥有权限码 code */
        boolean hasPermission(Long roleId, String code);
    }

    private final Map<String, PermissionProvider> providers = new ConcurrentHashMap<>();

    public void registerProvider(String principalType, PermissionProvider provider) {
        this.providers.put(principalType, provider);
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        RequirePermission rp = hm.getMethodAnnotation(RequirePermission.class);
        if (rp == null) {
            return true;
        }
        // 白名单路径：无登录主体，鉴权归 AuthInterceptor，这里放行
        UserContext ctx = UserContext.get();
        if (ctx == null) {
            return true;
        }
        String principalType = ctx.getType() == UserContext.PrincipalType.ADMIN ? "ADMIN" : "USER";
        PermissionProvider provider = providers.get(principalType);
        // USER 主体不注册校验器：C 端令牌禁止访问管理接口
        if (provider == null || !provider.hasPermission(ctx.getRoleId(), rp.value())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限执行该操作");
        }
        return true;
    }
}
