package com.herbaltea.module.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 店铺模块骨架实现
 *
 * <p>待实现：
 * <ol>
 *   <li>applyFranchise：写 franchise_applications（幂等键 idem:franchise:{applicant}）</li>
 *   <li>approveFranchise：审批通过 → 建 stores + store_settlement_configs（佣金 5%、T+1 日结）+ store_admins</li>
 *   <li>加盟保证金（franchise_deposits）收退</li>
 * </ol>
 */
@Slf4j
@Service
public class StoreServiceImpl implements StoreService {

    @Override
    public Long applyFranchise(Long userId, String storeName, String contactPhone) {
        // TODO
        return null;
    }

    @Override
    public void approveFranchise(Long applicationId, Long operatorAdminId) {
        // TODO: @AuditLog(action = "加盟审批")
    }

    @Override
    public void bindStoreAdmin(Long adminId, Long storeId) {
        // TODO: store_admins upsert（一店多管理员）
    }

    @Override
    public void listPendingCatalogReview(Long storeId) {
        // TODO: 查 store_products WHERE catalog_dirty = 1（D14 目录已更新待复核）
    }
}
