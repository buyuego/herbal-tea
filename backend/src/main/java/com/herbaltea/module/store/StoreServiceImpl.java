package com.herbaltea.module.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.auth.entity.AdminUser;
import com.herbaltea.module.auth.mapper.AdminUserMapper;
import com.herbaltea.module.store.dto.DepositVO;
import com.herbaltea.module.store.dto.PendingCatalogReviewVO;
import com.herbaltea.module.store.dto.StoreAdminVO;
import com.herbaltea.module.store.dto.StoreBindingVO;
import com.herbaltea.module.store.dto.StoreProductReviewRow;
import com.herbaltea.module.store.entity.FranchiseApplication;
import com.herbaltea.module.store.entity.FranchiseDeposit;
import com.herbaltea.module.store.entity.Store;
import com.herbaltea.module.store.entity.StoreAdmin;
import com.herbaltea.module.store.entity.StoreSettlementConfig;
import com.herbaltea.module.store.mapper.FranchiseApplicationMapper;
import com.herbaltea.module.store.mapper.FranchiseDepositMapper;
import com.herbaltea.module.store.mapper.StoreAdminMapper;
import com.herbaltea.module.store.mapper.StoreBindingMapper;
import com.herbaltea.module.store.mapper.StoreMapper;
import com.herbaltea.module.store.mapper.StoreProductReadMapper;
import com.herbaltea.module.store.mapper.StoreProductWriteMapper;
import com.herbaltea.module.store.mapper.StoreSettlementConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 店铺模块实现（stores / store_admins / franchise_applications / franchise_deposits）
 *
 * <p>已落地（v9）：
 * <ol>
 *   <li>applyFranchise：加盟申请（同用户待审核幂等）</li>
 *   <li>approveFranchise：审批通过 → 建 stores + store_settlement_configs（佣金 5%、T+1 日结）
 *       + store_admins 待绑定 + franchise_deposits 保证金缴纳流水（事务）</li>
 *   <li>rejectFranchise：审批拒绝（留审核意见）</li>
 *   <li>bindStoreAdmin：门店管理员绑定（upsert，首绑自动店主）</li>
 *   <li>storeIdOfAdmin：管理员主店查询（登录 JWT 签发 + Data Scope 数据源）</li>
 *   <li>listPendingCatalogReview：D14 本店目录变更复核</li>
 *   <li>pageDeposits / confirmDeposit / refundDeposit：加盟保证金收退确认（v12）</li>
 *   <li>confirmCatalogReview / rejectCatalogReview：D14 目录变更复核确认/驳回（v13）</li>
 *   <li>storeIdsOfAdmin / listMyStores：多店绑定（MULTI_STORE，v14）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    /** 加盟保证金默认金额（元），审批通过时写一笔「缴纳-待处理」流水 */
    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("20000.00");

    /** 平台佣金默认比例 5%（T+1 日结，与 V2 直营店一致） */
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.0500");

    private final StoreAdminMapper storeAdminMapper;
    private final StoreBindingMapper storeBindingMapper;
    private final FranchiseApplicationMapper franchiseApplicationMapper;
    private final StoreMapper storeMapper;
    private final StoreSettlementConfigMapper storeSettlementConfigMapper;
    private final FranchiseDepositMapper franchiseDepositMapper;
    private final StoreProductReadMapper storeProductReadMapper;
    private final StoreProductWriteMapper storeProductWriteMapper;
    private final AdminUserMapper adminUserMapper;

    @Override
    public Long applyFranchise(Long userId, String applicantName, String phone,
                               String intendedRegion, String experience) {
        // 业务幂等：franchise_applications 无 applicant_user 列（V1 DDL），
        // 以「手机号 + 待审核」近似用户维度（手机号为申请人唯一标识），重复提交直接拒绝。
        Long pending = franchiseApplicationMapper.selectCount(
                new LambdaQueryWrapper<FranchiseApplication>()
                        .eq(FranchiseApplication::getPhone, phone)
                        .eq(FranchiseApplication::getStatus, FranchiseApplication.STATUS_PENDING));
        if (pending != null && pending > 0) {
            throw BizException.conflict("该手机号已有待审核的加盟申请");
        }
        FranchiseApplication app = new FranchiseApplication();
        app.setApplicantName(applicantName);
        app.setPhone(phone);
        app.setIntendedRegion(intendedRegion);
        app.setExperience(experience);
        app.setStatus(FranchiseApplication.STATUS_PENDING);
        franchiseApplicationMapper.insert(app);
        log.info("加盟申请提交: id={} phone={} name={}", app.getId(), phone, applicantName);
        return app.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long approveFranchise(Long applicationId, Long operatorAdminId) {
        FranchiseApplication app = requireApplication(applicationId);
        if (app.getStatus() != FranchiseApplication.STATUS_PENDING) {
            throw BizException.conflict("该申请已处理（当前状态=" + statusText(app.getStatus()) + "）");
        }

        // 1. 建门店：store_no = ST + 既有编号最大值顺延（ST001 直营旗舰 / ST002+ 加盟）
        Long nextSeq = nextStoreSeq();
        Store store = new Store();
        store.setStoreNo(String.format("ST%03d", nextSeq));
        store.setStoreName(app.getApplicantName() + "加盟店");
        store.setStoreType(Store.TYPE_FRANCHISE);
        store.setStatus(Store.STATUS_OK);
        store.setContactName(app.getApplicantName());
        store.setContactPhone(app.getPhone());
        storeMapper.insert(store);

        // 2. 结算配置：佣金 5%、T+1 日结、72h 自动确认（对齐 V2 直营店默认值）
        StoreSettlementConfig cfg = new StoreSettlementConfig();
        cfg.setStoreId(store.getId());
        cfg.setCommissionRate(DEFAULT_COMMISSION_RATE);
        cfg.setCycleType(StoreSettlementConfig.CYCLE_DAILY);
        cfg.setAutoConfirmHours(72);
        cfg.setForceCatalogSync(0);
        storeSettlementConfigMapper.insert(cfg);

        // 3. 保证金缴纳流水（待处理：线下打款后由财务确认 status=1）
        FranchiseDeposit deposit = new FranchiseDeposit();
        deposit.setStoreId(store.getId());
        deposit.setType(FranchiseDeposit.TYPE_PAY);
        deposit.setAmount(DEPOSIT_AMOUNT);
        deposit.setStatus(FranchiseDeposit.STATUS_PENDING);
        deposit.setBizNo("FR-" + applicationId);
        franchiseDepositMapper.insert(deposit);

        // 4. 申请置通过
        app.setStatus(FranchiseApplication.STATUS_APPROVED);
        app.setReviewedBy(operatorAdminId);
        app.setReviewedAt(LocalDateTime.now());
        franchiseApplicationMapper.updateById(app);

        log.info("加盟审批通过: applicationId={} storeId={} storeNo={} operator={}",
                applicationId, store.getId(), store.getStoreNo(), operatorAdminId);
        return store.getId();
    }

    @Override
    public void rejectFranchise(Long applicationId, Long operatorAdminId, String reviewNote) {
        FranchiseApplication app = requireApplication(applicationId);
        if (app.getStatus() != FranchiseApplication.STATUS_PENDING) {
            throw BizException.conflict("该申请已处理（当前状态=" + statusText(app.getStatus()) + "）");
        }
        app.setStatus(FranchiseApplication.STATUS_REJECTED);
        app.setReviewNote(reviewNote);
        app.setReviewedBy(operatorAdminId);
        app.setReviewedAt(LocalDateTime.now());
        franchiseApplicationMapper.updateById(app);
        log.info("加盟审批拒绝: applicationId={} operator={} note={}",
                applicationId, operatorAdminId, reviewNote);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindStoreAdmin(Long adminId, Long storeId) {
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw new BizException(ResultCode.NOT_FOUND, "管理员不存在");
        }
        Store store = storeMapper.selectById(storeId);
        if (store == null) {
            throw new BizException(ResultCode.NOT_FOUND, "门店不存在");
        }

        StoreAdmin exist = storeAdminMapper.selectOne(new LambdaQueryWrapper<StoreAdmin>()
                .eq(StoreAdmin::getAdminId, adminId)
                .eq(StoreAdmin::getStoreId, storeId)
                .last("LIMIT 1"));
        if (exist != null) {
            // 复绑：恢复状态即可，is_owner 不降级（店主要走解绑流程）
            if (exist.getStatus() != StoreAdmin.STATUS_OK) {
                exist.setStatus(StoreAdmin.STATUS_OK);
                storeAdminMapper.updateById(exist);
            }
            log.info("门店管理员复绑: adminId={} storeId={}（已存在，恢复 status=1）", adminId, storeId);
            return;
        }

        // 该店尚无任何正常绑定 → 首个绑定自动为店主（加盟店成立后的主店责任人）
        boolean firstOwner = storeAdminMapper.selectCount(new LambdaQueryWrapper<StoreAdmin>()
                .eq(StoreAdmin::getStoreId, storeId)
                .eq(StoreAdmin::getStatus, StoreAdmin.STATUS_OK)) == 0;

        StoreAdmin sa = new StoreAdmin();
        sa.setAdminId(adminId);
        sa.setStoreId(storeId);
        sa.setIsOwner(firstOwner ? StoreAdmin.IS_OWNER : StoreAdmin.NOT_OWNER);
        sa.setStatus(StoreAdmin.STATUS_OK);
        storeAdminMapper.insert(sa);
        log.info("门店管理员绑定: adminId={} storeId={} isOwner={}", adminId, storeId, sa.getIsOwner());
    }

    @Override
    public Long storeIdOfAdmin(Long adminId) {
        // 店主优先（is_owner=1）；无店主则取 status=1 最近绑定
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
    public List<Long> storeIdsOfAdmin(Long adminId) {
        List<StoreAdmin> binds = storeAdminMapper.selectList(new LambdaQueryWrapper<StoreAdmin>()
                .eq(StoreAdmin::getAdminId, adminId)
                .eq(StoreAdmin::getStatus, StoreAdmin.STATUS_OK)
                .orderByDesc(StoreAdmin::getIsOwner)
                .orderByAsc(StoreAdmin::getId));
        return binds.stream().map(StoreAdmin::getStoreId).toList();
    }

    @Override
    public List<StoreBindingVO> listMyStores(Long adminId, Long currentStoreId) {
        List<StoreBindingVO> binds = storeBindingMapper.selectBindingsOf(adminId);
        binds.forEach(v -> v.setCurrent(Objects.equals(v.getStoreId(), currentStoreId)));
        return binds;
    }

    @Override
    public IPage<FranchiseApplication> pageApplications(Integer status, long page, long size) {
        return franchiseApplicationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<FranchiseApplication>()
                        .eq(status != null, FranchiseApplication::getStatus, status)
                        .orderByDesc(FranchiseApplication::getId));
    }

    @Override
    public List<StoreAdminVO> listStoreAdmins(Long storeId) {
        // 联查 admin_users 展示信息（@Select 内联 SQL，见 StoreAdminMapper.listByStore）
        return storeAdminMapper.listByStore(storeId);
    }

    @Override
    public List<PendingCatalogReviewVO> listPendingCatalogReview(Long storeId) {
        return storeProductReadMapper.listPendingCatalogReview(storeId);
    }

    // ==================== 加盟保证金收退确认（v12） ====================

    @Override
    public IPage<DepositVO> pageDeposits(Integer type, Integer status, Long storeId, long page, long size) {
        return franchiseDepositMapper.selectDepositPage(
                new Page<>(page, size), type, status, storeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmDeposit(Long depositId, Long operatorAdminId) {
        FranchiseDeposit deposit = requireDeposit(depositId);
        if (deposit.getType() != FranchiseDeposit.TYPE_PAY) {
            throw new BizException(ResultCode.PARAM_ERROR, "仅缴纳流水需确认收款");
        }
        if (deposit.getStatus() == FranchiseDeposit.STATUS_DONE) {
            throw new BizException(ResultCode.CONFLICT, "该保证金已确认收款，请勿重复操作");
        }
        deposit.setStatus(FranchiseDeposit.STATUS_DONE);
        deposit.setPaidAt(LocalDateTime.now());
        franchiseDepositMapper.updateById(deposit);
        log.info("保证金确认收款: depositId={} storeId={} bizNo={} amount={} operator={}",
                depositId, deposit.getStoreId(), deposit.getBizNo(), deposit.getAmount(), operatorAdminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundDeposit(Long depositId, Long operatorAdminId) {
        FranchiseDeposit deposit = requireDeposit(depositId);
        if (deposit.getType() != FranchiseDeposit.TYPE_PAY) {
            throw new BizException(ResultCode.PARAM_ERROR, "仅缴纳流水可发起退还");
        }
        if (deposit.getStatus() != FranchiseDeposit.STATUS_DONE) {
            throw new BizException(ResultCode.CONFLICT, "该保证金未确认收款，无法退还");
        }
        // 幂等：同 biz_no 已存在退还流水 → 已退
        Long refunded = franchiseDepositMapper.selectCount(new LambdaQueryWrapper<FranchiseDeposit>()
                .eq(FranchiseDeposit::getBizNo, deposit.getBizNo())
                .eq(FranchiseDeposit::getType, FranchiseDeposit.TYPE_REFUND));
        if (refunded != null && refunded > 0) {
            throw new BizException(ResultCode.CONFLICT, "该保证金已退还，请勿重复操作");
        }
        // 全额退还：写入退还流水（同 biz_no 关联缴纳流水），refunded_at 落库
        FranchiseDeposit refund = new FranchiseDeposit();
        refund.setStoreId(deposit.getStoreId());
        refund.setType(FranchiseDeposit.TYPE_REFUND);
        refund.setAmount(deposit.getAmount());
        refund.setStatus(FranchiseDeposit.STATUS_DONE);
        refund.setBizNo(deposit.getBizNo());
        refund.setRefundedAt(LocalDateTime.now());
        franchiseDepositMapper.insert(refund);
        log.info("保证金退还: depositId={} storeId={} bizNo={} amount={} operator={}",
                depositId, deposit.getStoreId(), deposit.getBizNo(), deposit.getAmount(), operatorAdminId);
    }

    // ==================== D14 目录变更复核确认/驳回（v13） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmCatalogReview(Long storeProductId, Long operatorAdminId) {
        StoreProductReviewRow row = requireReviewRow(storeProductId);
        if (row.getReviewStatus() != null && row.getReviewStatus() == 1) {
            throw new BizException(ResultCode.CONFLICT, "该商品已确认复核，请勿重复操作");
        }
        if (row.getCatalogDirty() == null || row.getCatalogDirty() != 1) {
            throw new BizException(ResultCode.CONFLICT, "该商品不在复核队列");
        }
        storeProductWriteMapper.confirmReview(storeProductId, row.getStoreId(), operatorAdminId);
        log.info("目录变更复核确认: storeProductId={} storeId={} operator={}",
                storeProductId, row.getStoreId(), operatorAdminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectCatalogReview(Long storeProductId, Long operatorAdminId, String note) {
        StoreProductReviewRow row = requireReviewRow(storeProductId);
        if (row.getReviewStatus() != null && row.getReviewStatus() == 2) {
            throw new BizException(ResultCode.CONFLICT, "该商品已驳回，请勿重复操作");
        }
        if (row.getReviewStatus() != null && row.getReviewStatus() == 1) {
            throw new BizException(ResultCode.CONFLICT, "已确认复核的商品不可驳回");
        }
        if (row.getCatalogDirty() == null || row.getCatalogDirty() != 1) {
            throw new BizException(ResultCode.CONFLICT, "该商品不在复核队列");
        }
        storeProductWriteMapper.rejectReview(storeProductId, row.getStoreId(), operatorAdminId, note);
        log.info("目录变更复核驳回: storeProductId={} storeId={} note={} operator={}",
                storeProductId, row.getStoreId(), note, operatorAdminId);
    }

    // ==================== 私有工具 ====================

    private FranchiseApplication requireApplication(Long applicationId) {
        FranchiseApplication app = franchiseApplicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BizException(ResultCode.NOT_FOUND, "加盟申请不存在");
        }
        return app;
    }

    private FranchiseDeposit requireDeposit(Long depositId) {
        FranchiseDeposit deposit = franchiseDepositMapper.selectById(depositId);
        if (deposit == null) {
            throw new BizException(ResultCode.NOT_FOUND, "保证金流水不存在");
        }
        return deposit;
    }

    /**
     * 复核目标行校验：必须存在且属于登录门店。
     * SELECT 直接带 store_id 条件——不存在/跨店统一 40400（不暴露他店商品存在性）。
     */
    private StoreProductReviewRow requireReviewRow(Long storeProductId) {
        Long storeId = UserContext.storeId();
        if (storeId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "当前账号未绑定门店，无法复核商品");
        }
        StoreProductReviewRow row = storeProductWriteMapper.selectReviewRow(storeProductId, storeId);
        if (row == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在或不属于本店");
        }
        return row;
    }

    /** 下一个门店编号序号：按既有编号最大值顺延（编号不复用；与主键 id 解耦，防删除后错位） */
    private Long nextStoreSeq() {
        Long max = storeMapper.selectMaxStoreSeq();
        return (max == null ? 0L : max) + 1;
    }

    private String statusText(int status) {
        return switch (status) {
            case FranchiseApplication.STATUS_PENDING -> "待审核";
            case FranchiseApplication.STATUS_APPROVED -> "通过";
            case FranchiseApplication.STATUS_REJECTED -> "拒绝";
            default -> "未知(" + status + ")";
        };
    }
}
