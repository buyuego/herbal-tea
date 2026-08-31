package com.herbaltea.module.auth;

import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.module.auth.dto.PermissionNodeVO;
import com.herbaltea.module.auth.dto.RoleAuthRequest;
import com.herbaltea.module.auth.dto.RoleCreateRequest;
import com.herbaltea.module.auth.dto.RoleUpdateRequest;
import com.herbaltea.module.auth.dto.RoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色权限管理接口（v10，RBAC 管理面）
 *
 * <p>全部接口要求 {@code system:role:config}（permissions.is_sensitive=1 敏感权限，
 * V2 仅超管角色持有），门店管理员等非超管一律 40300；写操作落审计日志。
 * 路由：{@code /api/system/roles}（角色 CRUD + 授权）、{@code /api/system/permissions}（权限树）。
 */
@Tag(name = "系统设置", description = "角色权限管理（RBAC，system:role:config 超管专属）")
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemRoleController {

    private final SystemRoleService systemRoleService;

    // ==================== 角色 CRUD ====================

    @Operation(summary = "角色列表", description = "含权限 id 集合（授权回显）与绑定管理员数（删除前置判断）")
    @GetMapping("/roles")
    @RequirePermission("system:role:config")
    public Result<List<RoleVO>> listRoles() {
        return Result.ok(systemRoleService.listRoles());
    }

    @Operation(summary = "角色详情")
    @GetMapping("/roles/{id}")
    @RequirePermission("system:role:config")
    public Result<RoleVO> getRole(@PathVariable Long id) {
        return Result.ok(systemRoleService.getRole(id));
    }

    @Operation(summary = "创建角色", description = "code 唯一且不可改；自定义角色上限 10；level 1 平台级 / 2 店铺级")
    @PostMapping("/roles")
    @RequirePermission("system:role:config")
    @AuditLog(action = "创建角色")
    public Result<RoleVO> createRole(@Valid @RequestBody RoleCreateRequest req) {
        return Result.ok(systemRoleService.createRole(req));
    }

    @Operation(summary = "更新角色", description = "预设角色（is_preset=1）仅可改 name/description，data_scope/level 锁定")
    @PutMapping("/roles/{id}")
    @RequirePermission("system:role:config")
    @AuditLog(action = "更新角色")
    public Result<RoleVO> updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest req) {
        return Result.ok(systemRoleService.updateRole(id, req));
    }

    @Operation(summary = "删除角色", description = "预设角色不可删；有绑定管理员拒删（先改绑）；级联清理授权关联")
    @DeleteMapping("/roles/{id}")
    @RequirePermission("system:role:config")
    @AuditLog(action = "删除角色")
    public Result<Void> deleteRole(@PathVariable Long id) {
        systemRoleService.deleteRole(id);
        return Result.ok();
    }

    // ==================== 授权 ====================

    @Operation(summary = "角色授权（全量覆盖）", description = "敏感权限（is_sensitive=1）仅超管角色可授予；"
            + "授权后该角色全部管理员旧令牌即时失效（token_version 批量 +1），权限变更秒级生效")
    @PutMapping("/roles/{id}/permissions")
    @RequirePermission("system:role:config")
    @AuditLog(action = "角色授权")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @Valid @RequestBody RoleAuthRequest req) {
        systemRoleService.assignPermissions(id, req.permissionIds());
        return Result.ok();
    }

    // ==================== 权限树 ====================

    @Operation(summary = "权限树", description = "菜单→按钮→接口三级，isSensitive=1 节点仅超管角色可勾选")
    @GetMapping("/permissions")
    @RequirePermission("system:role:config")
    public Result<List<PermissionNodeVO>> permissionTree() {
        return Result.ok(systemRoleService.permissionTree());
    }
}
