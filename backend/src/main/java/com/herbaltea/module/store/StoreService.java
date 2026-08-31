package com.herbaltea.module.store;

/**
 * 店铺模块（stores / store_admins / franchise_applications / franchise_deposits）
 *
 * <p>职责：店铺与门店管理员、加盟申请与保证金、本店商品目录（store_products，含 D14 目录标记）。
 */
public interface StoreService {

    /** 加盟申请（幂等：idem:franchise:{applicant_id}） */
    Long applyFranchise(Long userId, String storeName, String contactPhone);

    /** 总部审批加盟 → 创建店铺 + 结算配置（T+1 日结，V2 初始数据） */
    void approveFranchise(Long applicationId, Long operatorAdminId);

    /** 门店管理员绑定店铺 */
    void bindStoreAdmin(Long adminId, Long storeId);

    /**
     * 查询管理员主店（登录时签发 JWT storeId 用）。
     *
     * <p>规则：is_owner=1 优先；无店主标记则取 status=1 最近绑定；
     * 未绑定返回 null（= 总部管理员，dataScope=ALL）。
     * 多店场景（store_ids[]）由后续 MULTI_STORE 扩展支持。
     */
    Long storeIdOfAdmin(Long adminId);

    /** D14：总部商品目录变更后，查询"目录已更新"待复核的本店商品 */
    void listPendingCatalogReview(Long storeId);
}
