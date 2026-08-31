package com.herbaltea.infrastructure.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * B 端接口权限码标注（RBAC，设计文档 15.1）。
 *
 * <p>标注在 Controller 方法上，由 {@link PermissionInterceptor} 校验当前登录主体
 * 所属角色是否拥有该权限码（permissions.code，如 {@code order:ship}）。
 * 未标注的接口仅要求登录（白名单接口连登录都不需要）。
 *
 * <p>权限码全集见 permissions 表；角色-权限绑定见 role_permissions 表。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限码，如 order:ship / product:edit / menu:order */
    String value();
}
