package com.herbaltea.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.module.auth.dto.PermissionNodeVO;
import com.herbaltea.module.auth.dto.RoleCreateRequest;
import com.herbaltea.module.auth.dto.RoleUpdateRequest;
import com.herbaltea.module.auth.dto.RoleVO;
import com.herbaltea.module.auth.entity.AdminUser;
import com.herbaltea.module.auth.entity.Permission;
import com.herbaltea.module.auth.entity.Role;
import com.herbaltea.module.auth.entity.RolePermission;
import com.herbaltea.module.auth.mapper.AdminUserMapper;
import com.herbaltea.module.auth.mapper.PermissionMapper;
import com.herbaltea.module.auth.mapper.RoleMapper;
import com.herbaltea.module.auth.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色权限管理实现（v10）
 *
 * <p>规则要点（对齐 V1 DDL 注释与设计 5.1/15.1）：
 * <ul>
 *   <li>自定义角色上限 10（roles 表注释）；预设角色 is_preset=1 不可删</li>
 *   <li>授权全量覆盖；敏感权限（permissions.is_sensitive=1）仅 SUPER_ADMIN（role 1）可授予</li>
 *   <li>授权变更 → 角色下管理员 token_version 批量 +1（R9 即时吊销）+ Redis 权限缓存失效</li>
 *   <li>删除角色：is_preset=1 拒删；有绑定管理员拒删（防权限悬空）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemRoleServiceImpl implements SystemRoleService {

    /** SUPER_ADMIN 角色 id（V2 初始数据固定） */
    private static final long SUPER_ADMIN_ROLE_ID = 1L;

    /** Redis 权限缓存前缀（与 AuthServiceImpl.RBAC_PERMS_PREFIX 约定一致） */
    private static final String RBAC_PERMS_PREFIX = "rbac:perms:";

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final AdminUserMapper adminUserMapper;
    private final StringRedisTemplate redis;

    @Override
    public List<RoleVO> listRoles() {
        List<Role> roles = roleMapper.selectList(
                new LambdaQueryWrapper<Role>().orderByAsc(Role::getId));
        // 批量收集权限 id 与管理员数，避免 N+1
        Map<Long, List<Long>> permsByRole = roles.stream()
                .collect(Collectors.toMap(Role::getId,
                        r -> permissionMapper.selectPermissionIdsByRoleId(r.getId())));
        Map<Long, Long> adminCountByRole = roles.stream()
                .collect(Collectors.toMap(Role::getId, r -> adminUserMapper.countByRole(r.getId())));
        return roles.stream().map(r -> toVO(r, permsByRole.get(r.getId()), adminCountByRole.get(r.getId())))
                .toList();
    }

    @Override
    public RoleVO getRole(Long id) {
        Role role = requireRole(id);
        return toVO(role, permissionMapper.selectPermissionIdsByRoleId(id),
                adminUserMapper.countByRole(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO createRole(RoleCreateRequest req) {
        // code 唯一（含预设角色撞名）
        Long dup = roleMapper.selectCount(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, req.code()));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.CONFLICT, "角色编码已存在");
        }
        // 自定义角色上限 10
        Long custom = roleMapper.selectCount(
                new LambdaQueryWrapper<Role>().eq(Role::getIsPreset, Role.PRESET_NO));
        if (custom != null && custom >= Role.MAX_CUSTOM_ROLES) {
            throw new BizException(ResultCode.CONFLICT, "自定义角色已达上限（10 个）");
        }
        Role role = new Role();
        role.setCode(req.code());
        role.setName(req.name());
        role.setDataScope(req.dataScope());
        role.setLevel(req.level());
        role.setIsPreset(Role.PRESET_NO);
        role.setDescription(req.description());
        roleMapper.insert(role);
        log.info("创建角色 id={} code={}（当前自定义角色 {} 个）", role.getId(), role.getCode(), custom + 1);
        return toVO(role, Collections.emptyList(), 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO updateRole(Long id, RoleUpdateRequest req) {
        Role role = requireRole(id);
        boolean preset = Objects.equals(role.getIsPreset(), Role.PRESET_YES);
        // 预设角色：data_scope / level 锁定（防破坏角色基线），仅可改 name/description
        if (preset
                && (!Objects.equals(role.getDataScope(), req.dataScope())
                || !Objects.equals(role.getLevel(), req.level()))) {
            throw new BizException(ResultCode.PARAM_ERROR, "预设角色不可修改数据范围与级别");
        }
        role.setName(req.name());
        role.setDataScope(req.dataScope());
        role.setLevel(req.level());
        role.setDescription(req.description());
        roleMapper.updateById(role);
        log.info("更新角色 id={} code={}{}", id, role.getCode(), preset ? "（预设角色）" : "");
        return toVO(role, permissionMapper.selectPermissionIdsByRoleId(id),
                adminUserMapper.countByRole(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        Role role = requireRole(id);
        if (Objects.equals(role.getIsPreset(), Role.PRESET_YES)) {
            throw new BizException(ResultCode.CONFLICT, "预设角色不可删除");
        }
        long bound = adminUserMapper.countByRole(id);
        if (bound > 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "该角色仍绑定 " + bound + " 个管理员，请先改绑后再删除");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, id));
        roleMapper.deleteById(id);
        redis.delete(RBAC_PERMS_PREFIX + id);
        log.info("删除角色 id={} code={}（级联清理授权关联）", id, role.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long id, List<Long> permissionIds) {
        Role role = requireRole(id);
        Set<Long> ids = new HashSet<>(permissionIds);
        // 1. 权限存在性校验（批量查一次）
        List<Permission> perms = ids.isEmpty()
                ? Collections.emptyList()
                : permissionMapper.selectByIds(new java.util.ArrayList<>(ids));
        if (perms.size() != ids.size()) {
            throw new BizException(ResultCode.PARAM_ERROR, "包含不存在的权限 id");
        }
        // 2. 敏感权限仅 SUPER_ADMIN 可授予
        if (id != SUPER_ADMIN_ROLE_ID) {
            Set<Long> sensitiveIds = perms.stream()
                    .filter(p -> Objects.equals(p.getIsSensitive(), Permission.SENSITIVE_YES))
                    .map(Permission::getId)
                    .collect(Collectors.toSet());
            if (!sensitiveIds.isEmpty()) {
                String codes = perms.stream()
                        .filter(p -> sensitiveIds.contains(p.getId()))
                        .map(Permission::getCode)
                        .collect(Collectors.joining(","));
                throw new BizException(ResultCode.PARAM_ERROR,
                        "敏感权限（" + codes + "）仅超管角色可授予");
            }
        }
        // 3. 全量覆盖：删旧插新
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, id));
        for (Long pid : ids) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(id);
            rp.setPermissionId(pid);
            rolePermissionMapper.insert(rp);
        }
        // 4. 该角色全部管理员 token_version 批量 +1：旧 JWT 即时失效（R9）
        int bumped = adminUserMapper.bumpTokenVersionByRole(id);
        // 5. Redis 权限缓存失效（下一次校验从 DB 回填）
        redis.delete(RBAC_PERMS_PREFIX + id);
        log.info("角色 id={} code={} 授权覆盖 {} 个权限，吊销 {} 个管理员令牌",
                id, role.getCode(), ids.size(), bumped);
    }

    @Override
    public List<PermissionNodeVO> permissionTree() {
        List<Permission> all = permissionMapper.selectAll();
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        // 父节点索引：先构建全部节点壳，再挂 children
        Map<Long, PermissionNodeVO> nodeMap = all.stream().collect(Collectors.toMap(
                Permission::getId,
                p -> new PermissionNodeVO(p.getId(), p.getCode(), p.getName(),
                        p.getModule(), p.getType(), p.getParentId(), p.getPath(), p.getIsSensitive())));
        List<PermissionNodeVO> roots = new java.util.ArrayList<>();
        for (Permission p : all) {
            PermissionNodeVO node = nodeMap.get(p.getId());
            if (p.getParentId() == null) {
                roots.add(node);
            } else {
                PermissionNodeVO parent = nodeMap.get(p.getParentId());
                if (parent != null) {
                    parent.children().add(node);
                }
            }
        }
        return roots;
    }

    // ==================== 私有工具 ====================

    private Role requireRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private RoleVO toVO(Role r, List<Long> permissionIds, Long adminCount) {
        return new RoleVO(r.getId(), r.getCode(), r.getName(), r.getDataScope(),
                r.getLevel(), r.getIsPreset(), r.getDescription(),
                permissionIds == null ? Collections.emptyList() : permissionIds,
                adminCount == null ? 0L : adminCount, r.getCreatedAt());
    }
}
