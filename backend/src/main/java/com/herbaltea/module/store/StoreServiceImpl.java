package com.herbaltea.module.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.herbaltea.module.store.entity.StoreAdmin;
import com.herbaltea.module.store.mapper.StoreAdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 店铺模块实现（stores / store_admins / franchise_applications / franchise_deposits）
 *
 * <p>已落地：
 * <ol>
 *   <li>storeIdOfAdmin：管理员主店查询（登录 JWT 签发 + Data Scope 数据源，A5/A6）</li>
 * </ol>
 * 待实现：
 * <ol start="2">
 *   <li>applyFranchise：写 franchise_applications（幂等键 idem:franchise:{applicant}）</li>
 *   <li>approveFranchise：审批通过 → 建 stores + store_settlement_configs（佣金 5%、T+1 日结）+ store_admins</li>
 *   <li>加盟保证金（franchise_deposits）收退</li>
 *   <li>D14：listPendingCatalogReview 本店目录变更复核</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreAdminMapper storeAdminMapper;

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
    public Long storeIdOfAdmin(Long adminId) {
        // 店主优先（is_owner=1）；无店主则取 status=1 最近绑定的一条
        StoreAdmin owner = storeAdminMapper.selectOne(new LambdaQueryWrapper<StoreAdmin>()
                .eq(StoreAdmin::getAdminId, adminId)
                .eq(StoreAdmin::getIsOwner, StoreAdmin.IS_OWNER)
                .eq(StoreAdmin::getStatus, StoreAdmin.STATUS_OK)
                .last("LIMIT 1"));
        if (owner != null) {
            return owner.getStoreId();
        }
        StoreAdmin latest = storeAdminMapper.selectOne(new LambdaQueryWrapper<StoreAdmin>()
                .eq(StoreAdmin::getAdminId, adminId)
                .eq(StoreAdmin::getStatus, StoreAdmin.STATUS_OK)
                .orderByDesc(StoreAdmin::getId)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getStoreId();
    }

    @Override
    public void listPendingCatalogReview(Long storeId) {
        // TODO: 查 store_products WHERE catalog_dirty = 1（D14 目录已更新待复核）
    }
}
