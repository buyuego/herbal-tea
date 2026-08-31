package com.herbaltea.module.marketing;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.marketing.dto.CouponQuery;
import com.herbaltea.module.marketing.dto.CouponSaveRequest;
import com.herbaltea.module.marketing.dto.CouponVO;
import com.herbaltea.module.marketing.dto.UserCouponVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 营销接口（B 端后台，v27 积分 / v28 优惠券）
 *
 * <p>积分过期回收由 {@code PointsExpireTask} 每日 03:00 自动执行，
 * {@code /admin/points/expire} 供运维手动补偿，幂等可重复调用。
 */
@Tag(name = "营销", description = "积分过期回收（手动触发） / 优惠券模板与领券")
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingController {

    private final MarketingService marketingService;

    private final CouponService couponService;

    // ==================== 积分 ====================

    @Operation(summary = "手动执行积分过期回收", description = "到期批次清零入流水 + 到期前 7 天提醒；幂等，可安全重复执行")
    @PostMapping("/admin/points/expire")
    @RequirePermission("marketing:points:run")
    @AuditLog(action = "手动积分过期回收")
    public Result<Integer> expirePoints() {
        return Result.ok(marketingService.expirePoints());
    }

    // ==================== 优惠券模板 ====================

    @Operation(summary = "券模板分页", description = "关键词/类型/归属/门店/状态筛选；含剩余可领数量")
    @GetMapping("/admin/coupons")
    @RequirePermission("menu:marketing")
    public Result<IPage<CouponVO>> pageCoupons(@ModelAttribute CouponQuery query) {
        return Result.ok(couponService.pageCoupons(query));
    }

    @Operation(summary = "券模板详情")
    @GetMapping("/admin/coupons/{id}")
    @RequirePermission("menu:marketing")
    public Result<CouponVO> getCoupon(@PathVariable Long id) {
        return Result.ok(couponService.getCoupon(id));
    }

    @Operation(summary = "创建券模板", description = "创建后为「未发布」；门店账号只能建本店券（自动归属本店）")
    @PostMapping("/admin/coupons")
    @RequirePermission("marketing:coupon")
    @AuditLog(action = "创建券模板")
    public Result<Long> createCoupon(@Valid @RequestBody CouponSaveRequest req) {
        return Result.ok(couponService.createCoupon(req, currentStoreId()));
    }

    @Operation(summary = "编辑券模板", description = "仅「未发布」且无人领取的券可编辑")
    @PutMapping("/admin/coupons/{id}")
    @RequirePermission("marketing:coupon")
    @AuditLog(action = "编辑券模板")
    public Result<Void> updateCoupon(@PathVariable Long id, @Valid @RequestBody CouponSaveRequest req) {
        couponService.updateCoupon(id, req, currentStoreId());
        return Result.ok();
    }

    @Operation(summary = "发布券模板", description = "未发布 → 发放中（0→1）")
    @PostMapping("/admin/coupons/{id}/publish")
    @RequirePermission("marketing:coupon")
    @AuditLog(action = "发布券模板")
    public Result<Void> publishCoupon(@PathVariable Long id) {
        couponService.publishCoupon(id);
        return Result.ok();
    }

    @Operation(summary = "停止发放", description = "发放中 → 已停止（1→2）；已领券仍可使用至过期")
    @PostMapping("/admin/coupons/{id}/stop")
    @RequirePermission("marketing:coupon")
    @AuditLog(action = "停止发放券")
    public Result<Void> stopCoupon(@PathVariable Long id) {
        couponService.stopCoupon(id);
        return Result.ok();
    }

    @Operation(summary = "券领取记录", description = "按券模板查看发放与核销情况")
    @GetMapping("/admin/coupons/{id}/grants")
    @RequirePermission("menu:marketing")
    public Result<IPage<UserCouponVO>> pageCouponGrants(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "10") long size) {
        return Result.ok(couponService.pageCouponGrants(id, page, size));
    }

    @Operation(summary = "发券给指定会员", description = "校验发放中/有效期/余量/限领后原子发券（C 端领券接口未建前的代发入口）")
    @PostMapping("/admin/coupons/{id}/grant")
    @RequirePermission("marketing:coupon")
    @AuditLog(action = "发放优惠券")
    public Result<Long> grantCoupon(@PathVariable Long id, @RequestParam Long userId) {
        return Result.ok(couponService.grantCoupon(id, userId));
    }

    @Operation(summary = "会员持券列表", description = "status：0未使用 / 1已使用 / 2已过期 / 3退款退回，不传查全部")
    @GetMapping("/admin/members/{userId}/coupons")
    @RequirePermission("menu:marketing")
    public Result<IPage<UserCouponVO>> pageUserCoupons(@PathVariable Long userId,
                                                       @RequestParam(required = false) Integer status,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size) {
        return Result.ok(couponService.pageUserCoupons(userId, status, page, size));
    }

    /** 当前登录主体的门店 id（总部账号为 null） */
    private Long currentStoreId() {
        UserContext ctx = UserContext.get();
        if (ctx == null) {
            return null;
        }
        Long sid = ctx.getStoreId();
        return sid != null && sid > 0 ? sid : null;
    }
}
