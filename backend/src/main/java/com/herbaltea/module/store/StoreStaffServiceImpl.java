package com.herbaltea.module.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.PageResult;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.module.auth.entity.AdminUser;
import com.herbaltea.module.auth.mapper.AdminUserMapper;
import com.herbaltea.module.store.dto.StaffCreateRequest;
import com.herbaltea.module.store.dto.StaffUpdateRequest;
import com.herbaltea.module.store.dto.StaffVO;
import com.herbaltea.module.store.entity.StoreAdmin;
import com.herbaltea.module.store.mapper.StoreAdminMapper;
import com.herbaltea.module.store.mapper.StoreStaffMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 员工账号管理实现（v11）
 *
 * <p>数据面：admin_users（auth 模块表，本模块经 AdminUserMapper 读写——与 v9 bindStoreAdmin
 * 既有先例一致）+ store_admins（本模块表）。模块化单体同库事务，员工创建/绑定原子生效。
 *
 * <p>角色约定：员工 = STORE_STAFF（roles.id=5，V2 预置）；门店管理员不可操作店主/管理员账号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreStaffServiceImpl implements StoreStaffService {

    /** STORE_STAFF 角色 id（V2 预置固定） */
    private static final long STORE_STAFF_ROLE_ID = 5L;

    private final StoreStaffMapper storeStaffMapper;
    private final StoreAdminMapper storeAdminMapper;
    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<StaffVO> pageStaff(Long storeId, Integer boundStatus, long page, long size) {
        IPage<StaffVO> p = storeStaffMapper.selectStaffPage(
                new Page<>(page, size), storeId, boundStatus);
        return PageResult.of(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStaff(Long storeId, StaffCreateRequest req) {
        AdminUser exist = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, req.username()));
        if (exist != null) {
            // 复用既有账号：仅允许员工角色（管理员/店主账号不可被拉为员工）
            requireStaffRole(exist.getId(), exist.getRoleId(), "该账号为管理员角色，不可作为员工");
            // 已绑定其他门店（正常）→ 拒绝（账号全局唯一，需原门店先移除再流转）
            StoreAdmin other = storeAdminMapper.selectOne(new LambdaQueryWrapper<StoreAdmin>()
                    .eq(StoreAdmin::getAdminId, exist.getId())
                    .eq(StoreAdmin::getStatus, StoreAdmin.STATUS_OK)
                    .ne(StoreAdmin::getStoreId, storeId)
                    .last("LIMIT 1"));
            if (other != null) {
                throw new BizException(ResultCode.CONFLICT,
                        "该账号已绑定其他门店（门店 id=" + other.getStoreId() + "），请先由原门店移除");
            }
            StoreAdmin bound = boundOf(exist.getId(), storeId);
            if (bound != null && Objects.equals(bound.getStatus(), StoreAdmin.STATUS_OK)) {
                throw new BizException(ResultCode.CONFLICT, "该员工已在本店，请勿重复添加");
            }
            // 复绑：恢复绑定 + 同步更新姓名/手机号 + 重置密码
            // （「添加员工」统一语义：发放新初始密码；移除时已吊销旧令牌，无需再次 bump）
            exist.setRealName(req.realName());
            exist.setPhone(req.phone());
            exist.setPasswordHash(passwordEncoder.encode(req.password()));
            adminUserMapper.updateById(exist);
            if (bound != null) {
                bound.setStatus(StoreAdmin.STATUS_OK);
                storeAdminMapper.updateById(bound);
                log.info("员工复绑并更新: adminId={} storeId={}", exist.getId(), storeId);
            } else {
                insertBinding(exist.getId(), storeId);
            }
            return exist.getId();
        }

        // 新建员工账号（角色强制 STORE_STAFF）
        AdminUser staff = new AdminUser();
        staff.setUsername(req.username());
        staff.setPasswordHash(passwordEncoder.encode(req.password()));
        staff.setRealName(req.realName());
        staff.setPhone(req.phone());
        staff.setRoleId(STORE_STAFF_ROLE_ID);
        staff.setStatus(AdminUser.STATUS_ENABLED);
        staff.setTokenVersion(0);
        adminUserMapper.insert(staff);
        insertBinding(staff.getId(), storeId);
        log.info("创建员工: adminId={} username={} storeId={}", staff.getId(), req.username(), storeId);
        return staff.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStaff(Long storeId, Long adminId, StaffUpdateRequest req) {
        AdminUser staff = requireStaffOfStore(storeId, adminId);
        boolean disabling = Objects.equals(staff.getStatus(), AdminUser.STATUS_ENABLED)
                && Objects.equals(req.status(), AdminUser.STATUS_DISABLED);
        staff.setRealName(req.realName());
        staff.setPhone(req.phone());
        staff.setStatus(req.status());
        adminUserMapper.updateById(staff);
        if (disabling) {
            // 禁用 → 旧 JWT 即时失效（R9）；启用无需（禁用时已吊销）
            int bumped = adminUserMapper.bumpTokenVersion(adminId);
            log.info("禁用员工: adminId={} storeId={} 吊销令牌 {}", adminId, storeId, bumped);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long storeId, Long adminId, String newPassword) {
        requireStaffOfStore(storeId, adminId);
        adminUserMapper.update(null, new LambdaUpdateWrapper<AdminUser>()
                .eq(AdminUser::getId, adminId)
                .set(AdminUser::getPasswordHash, passwordEncoder.encode(newPassword)));
        // 改密 → 旧令牌全部失效（安全兜底：旧密码会话立即下线）
        int bumped = adminUserMapper.bumpTokenVersion(adminId);
        log.info("重置员工密码: adminId={} storeId={} 吊销令牌 {}", adminId, storeId, bumped);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeStaff(Long storeId, Long adminId) {
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ResultCode.NOT_FOUND, "员工不存在");
        }
        requireStaffRole(adminId, admin.getRoleId(), "仅可管理员工角色账号（店主/管理员不受员工管理接口管辖）");
        StoreAdmin sa = boundOf(adminId, storeId);
        if (sa == null || !Objects.equals(sa.getStatus(), StoreAdmin.STATUS_OK)) {
            return; // 幂等：不存在本店绑定或已移除
        }
        sa.setStatus(StoreAdmin.STATUS_REMOVED);
        storeAdminMapper.updateById(sa);
        int bumped = adminUserMapper.bumpTokenVersion(adminId);
        log.info("移除员工: adminId={} storeId={} 吊销令牌 {}", adminId, storeId, bumped);
    }

    // ==================== 私有工具 ====================

    private void insertBinding(Long adminId, Long storeId) {
        StoreAdmin sa = new StoreAdmin();
        sa.setAdminId(adminId);
        sa.setStoreId(storeId);
        sa.setIsOwner(StoreAdmin.NOT_OWNER);
        sa.setStatus(StoreAdmin.STATUS_OK);
        storeAdminMapper.insert(sa);
    }

    private StoreAdmin boundOf(Long adminId, Long storeId) {
        return storeAdminMapper.selectOne(new LambdaQueryWrapper<StoreAdmin>()
                .eq(StoreAdmin::getAdminId, adminId)
                .eq(StoreAdmin::getStoreId, storeId)
                .last("LIMIT 1"));
    }

    /**
     * 操作目标必须存在、属于本店（正常绑定）、且为员工角色。
     * 不属于本店返回 40400（不暴露其他店员工存在性）；非员工角色返回 40000。
     */
    private AdminUser requireStaffOfStore(Long storeId, Long adminId) {
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ResultCode.NOT_FOUND, "员工不存在");
        }
        requireStaffRole(adminId, admin.getRoleId(), "仅可管理员工角色账号（店主/管理员不受员工管理接口管辖）");
        StoreAdmin sa = boundOf(adminId, storeId);
        if (sa == null || !Objects.equals(sa.getStatus(), StoreAdmin.STATUS_OK)) {
            throw new BizException(ResultCode.NOT_FOUND, "员工不存在或不属于本店");
        }
        return admin;
    }

    private void requireStaffRole(Long adminId, Long roleId, String message) {
        if (!Objects.equals(roleId, STORE_STAFF_ROLE_ID)) {
            log.warn("员工管理目标角色校验失败: adminId={} roleId={}", adminId, roleId);
            throw new BizException(ResultCode.PARAM_ERROR, message);
        }
    }
}
