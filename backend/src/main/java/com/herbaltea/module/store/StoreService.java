package com.herbaltea.module.store;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.module.store.dto.PendingCatalogReviewVO;
import com.herbaltea.module.store.dto.StoreAdminVO;
import com.herbaltea.module.store.entity.FranchiseApplication;

import java.util.List;

/**
 * 店铺模块（stores / store_admins / franchise_applications / franchise_deposits）
 *
 * <p>职责：店铺与门店管理员、加盟申请与保证金、本店商品目录（store_products，含 D14 目录标记）。
 */
public interface StoreService {

    /** 加盟申请（C 端登录用户提交；同用户仅允许一笔待审核，业务幂等） */
    Long applyFranchise(Long userId, String applicantName, String phone,
                        String intendedRegion, String experience);

    /** 总部审批加盟 → 创建店铺 + 结算配置（T+1 日结，佣金 5%）+ 保证金缴纳流水（事务） */
    Long approveFranchise(Long applicationId, Long operatorAdminId);

    /** 总部拒绝加盟（仅待审核可拒绝） */
    void rejectFranchise(Long applicationId, Long operatorAdminId, String reviewNote);

    /** 门店管理员绑定店铺（upsert；该店首个绑定自动置为店主） */
    void bindStoreAdmin(Long adminId, Long storeId);

    /**
     * 查询管理员主店（登录时签发 JWT storeId 用）。
     *
     * <p>规则：is_owner=1 优先；无店主标记则取 status=1 最近绑定；
     * 未绑定返回 null（= 总部管理员，dataScope=ALL）。
     * 多店场景（store_ids[]）由后续 MULTI_STORE 扩展支持。
     */
    Long storeIdOfAdmin(Long adminId);

    /** 加盟申请分页（总部；status 过滤：null 全部 / 0待审核 / 1通过 / 2拒绝） */
    IPage<FranchiseApplication> pageApplications(Integer status, long page, long size);

    /** 门店管理员列表（总部；联查 admin_users 展示信息） */
    List<StoreAdminVO> listStoreAdmins(Long storeId);

    /** D14：总部商品目录变更后，查询"目录已更新"待复核的本店商品 */
    List<PendingCatalogReviewVO> listPendingCatalogReview(Long storeId);
}
