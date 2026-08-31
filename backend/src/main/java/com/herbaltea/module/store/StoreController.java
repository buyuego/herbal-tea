package com.herbaltea.module.store;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.store.dto.DepositVO;
import com.herbaltea.module.store.dto.FranchiseApplyRequest;
import com.herbaltea.module.store.dto.PendingCatalogReviewVO;
import com.herbaltea.module.store.dto.StoreAdminBindRequest;
import com.herbaltea.module.store.dto.StoreAdminVO;
import com.herbaltea.module.store.entity.FranchiseApplication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店接口（加盟申请 C 端 / 审批与绑定 B 端总部 / D14 复核门店端）
 *
 * <p>路径分组：
 * <ul>
 *   <li>{@code /api/store/franchise/apply}：C 端登录用户提交加盟申请</li>
 *   <li>{@code /api/store/admin/**}：总部专属（{@code store:franchise:approve}，
 *       门店管理员角色 4 虽有 menu:store 但不持有该码，天然隔离）</li>
 *   <li>{@code /api/store/pending-catalog-review}：本店 D14 目录变更复核（门店）</li>
 * </ul>
 */
@Tag(name = "门店", description = "加盟申请（C端）/ 审批与管理员绑定（总部）/ 目录变更复核（门店）")
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    // ==================== C 端：加盟申请 ====================

    @Operation(summary = "提交加盟申请", description = "登录用户提交；同手机号存在待审核申请则返回 40900")
    @PostMapping("/franchise/apply")
    public Result<Long> applyFranchise(@Valid @RequestBody FranchiseApplyRequest req) {
        return Result.ok(storeService.applyFranchise(UserContext.userId(),
                req.applicantName(), req.phone(), req.intendedRegion(), req.experience()));
    }

    // ==================== B 端总部：加盟审批（store:franchise:approve） ====================

    @Operation(summary = "加盟申请分页", description = "status 过滤：null 全部 / 0待审核 / 1通过 / 2拒绝")
    @GetMapping("/admin/franchise/applications")
    @RequirePermission("store:franchise:approve")
    public Result<IPage<FranchiseApplication>> pageApplications(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(storeService.pageApplications(status, page, size));
    }

    @Operation(summary = "审批通过", description = "事务：建门店 STxxx + 结算配置（佣金5%/T+1）+ 保证金缴纳流水 + 申请置通过；返回新门店 id")
    @PostMapping("/admin/franchise/applications/{id}/approve")
    @RequirePermission("store:franchise:approve")
    @AuditLog(action = "加盟审批通过")
    public Result<Long> approveFranchise(@PathVariable Long id) {
        return Result.ok(storeService.approveFranchise(id, UserContext.get().getAdminId()));
    }

    @Operation(summary = "审批拒绝", description = "仅待审核可拒绝；reviewNote 为审核意见")
    @PostMapping("/admin/franchise/applications/{id}/reject")
    @RequirePermission("store:franchise:approve")
    @AuditLog(action = "加盟审批拒绝")
    public Result<Void> rejectFranchise(@PathVariable Long id,
                                        @RequestParam(required = false) String reviewNote) {
        storeService.rejectFranchise(id, UserContext.get().getAdminId(), reviewNote);
        return Result.ok();
    }

    // ==================== B 端总部：门店管理员绑定 ====================

    @Operation(summary = "绑定门店管理员", description = "upsert：已绑定则恢复 status=1；该店首个绑定自动置为店主（is_owner=1）")
    @PostMapping("/admin/admins/bind")
    @RequirePermission("store:franchise:approve")
    @AuditLog(action = "门店管理员绑定")
    public Result<Void> bindStoreAdmin(@Valid @RequestBody StoreAdminBindRequest req) {
        storeService.bindStoreAdmin(req.adminId(), req.storeId());
        return Result.ok();
    }

    @Operation(summary = "门店管理员列表", description = "联查 admin_users 展示登录名/姓名/手机号/角色")
    @GetMapping("/admin/admins")
    @RequirePermission("store:franchise:approve")
    public Result<List<StoreAdminVO>> listStoreAdmins(
            @RequestParam @NotNull(message = "门店不能为空") Long storeId) {
        return Result.ok(storeService.listStoreAdmins(storeId));
    }

    // ==================== B 端总部：加盟保证金收退确认（store:deposit:confirm） ====================

    @Operation(summary = "保证金流水分页", description = "type 过滤：null 全部 / 1缴纳 / 2退还；status 过滤：null 全部 / 0待处理 / 1完成")
    @GetMapping("/admin/deposits")
    @RequirePermission("store:deposit:confirm")
    public Result<IPage<DepositVO>> pageDeposits(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(storeService.pageDeposits(type, status, storeId, page, size));
    }

    @Operation(summary = "确认收款", description = "缴纳流水 0待处理 → 1完成 + paid_at；重复确认 40900")
    @PostMapping("/admin/deposits/{id}/confirm")
    @RequirePermission("store:deposit:confirm")
    @AuditLog(action = "保证金确认收款")
    public Result<Void> confirmDeposit(@PathVariable Long id) {
        storeService.confirmDeposit(id, UserContext.get().getAdminId());
        return Result.ok();
    }

    @Operation(summary = "退还保证金", description = "对已确认收款的缴纳流水全额退还，写入退还流水（type=2）；未确认 40900 / 已退 40900")
    @PostMapping("/admin/deposits/{id}/refund")
    @RequirePermission("store:deposit:confirm")
    @AuditLog(action = "保证金退还")
    public Result<Void> refundDeposit(@PathVariable Long id) {
        storeService.refundDeposit(id, UserContext.get().getAdminId());
        return Result.ok();
    }

    // ==================== 门店端：D14 目录变更复核 ====================

    @Operation(summary = "本店目录变更复核", description = "catalog_dirty=1 的商品列表（目录已更新，店铺端角标提示复核）；storeId 取自登录上下文")
    @GetMapping("/pending-catalog-review")
    @RequirePermission("menu:product")
    public Result<List<PendingCatalogReviewVO>> listPendingCatalogReview() {
        return Result.ok(storeService.listPendingCatalogReview(UserContext.storeId()));
    }
}
