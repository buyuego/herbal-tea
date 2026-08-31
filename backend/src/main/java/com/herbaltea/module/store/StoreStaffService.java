package com.herbaltea.module.store;

import com.herbaltea.common.result.PageResult;
import com.herbaltea.module.store.dto.StaffCreateRequest;
import com.herbaltea.module.store.dto.StaffUpdateRequest;
import com.herbaltea.module.store.dto.StaffVO;

/**
 * 员工账号管理（v11，store:staff:manage 门店自治）
 *
 * <p>职责：门店管理员管理「本店员工」（admin_users + store_admins 双表）。
 * 安全规则：
 * <ul>
 *   <li>操作目标必须属于本店（防跨店越权），且角色必须为 STORE_STAFF（防操作店主/管理员）</li>
 *   <li>创建强制员工角色（不接收 roleId，防提权建管理员）</li>
 *   <li>禁用/改密/移除 → token_version +1 即时吊销（R9），权限与状态变更秒级生效</li>
 *   <li>移除为软删（store_admins.status=0），账号保留可复绑；username 全局唯一</li>
 * </ul>
 */
public interface StoreStaffService {

    /**
     * 本店员工分页列表。
     *
     * @param storeId     门店 id（Controller 已从登录上下文校验非空）
     * @param boundStatus 绑定状态过滤：null 全部 / 1 正常 / 0 已移除
     */
    PageResult<StaffVO> pageStaff(Long storeId, Integer boundStatus, long page, long size);

    /**
     * 创建员工（或复绑已移除账号）：新建 admin_users（STORE_STAFF）+ 绑定本店。
     *
     * @return 员工 admin id
     */
    Long createStaff(Long storeId, StaffCreateRequest req);

    /** 更新员工（姓名/手机号/启用禁用）；禁用时即时吊销旧令牌 */
    void updateStaff(Long storeId, Long adminId, StaffUpdateRequest req);

    /** 重置密码；改密后旧令牌全部失效 */
    void resetPassword(Long storeId, Long adminId, String newPassword);

    /** 移除员工（软删绑定 + 即时吊销）；幂等（已移除直接返回） */
    void removeStaff(Long storeId, Long adminId);

    /**
     * 员工加绑当前门店（MULTI_STORE，v14）：把已存在员工账号绑定到当前上下文门店。
     *
     * <p>与创建（新账号）不同，加绑允许目标员工已正常绑定他店（一人多店）；
     * 加绑成功后即时吊销目标员工旧令牌（JWT "sids" 为快照，须重登刷新）。
     * 已绑定本店 → 40900；非员工角色 → 40000。
     */
    void bindStaff(Long storeId, Long adminId);
}
