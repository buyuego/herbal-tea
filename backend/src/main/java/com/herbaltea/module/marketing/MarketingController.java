package com.herbaltea.module.marketing;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.marketing.dto.CouponQuery;
import com.herbaltea.module.marketing.dto.CouponSaveRequest;
import com.herbaltea.module.marketing.dto.CouponVO;
import com.herbaltea.module.marketing.dto.MyPointsVO;
import com.herbaltea.module.marketing.dto.PointRecordVO;
import com.herbaltea.module.marketing.dto.PromotionQuery;
import com.herbaltea.module.marketing.dto.PromotionSaveRequest;
import com.herbaltea.module.marketing.dto.PromotionVO;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * 营销接口（B 端后台，v27 积分 / v28 优惠券 / v30 促销活动）
 *
 * <p>积分过期回收由 {@code PointsExpireTask} 每日 03:00 自动执行，
 * {@code /admin/points/expire} 供运维手动补偿，幂等可重复调用。
 * <p>活动到点结束由 {@code PromotionCloseTask} 每 5 分钟扫描。
 */
@Tag(name = "营销", description = "积分过期回收（手动触发） / 优惠券模板与领券 / 促销活动")
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingController {

    private final MarketingService marketingService;

    private final CouponService couponService;

    private final PromotionService promotionService;

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

    // ==================== 促销活动（v30） ====================

    @Operation(summary = "活动分页", description = "关键词/类型/归属/门店/状态筛选；含规则摘要与当前是否生效")
    @GetMapping("/admin/promotions")
    @RequirePermission("menu:marketing")
    public Result<IPage<PromotionVO>> pagePromotions(@ModelAttribute PromotionQuery query) {
        return Result.ok(promotionService.pagePromotions(query));
    }

    @Operation(summary = "活动详情")
    @GetMapping("/admin/promotions/{id}")
    @RequirePermission("menu:marketing")
    public Result<PromotionVO> getPromotion(@PathVariable Long id) {
        return Result.ok(promotionService.getPromotion(id));
    }

    @Operation(summary = "创建活动", description = "创建后为「草稿」；门店账号只能建本店活动（自动归属本店），平台活动仅总部可建")
    @PostMapping("/admin/promotions")
    @RequirePermission("marketing:promotion")
    @AuditLog(action = "创建促销活动")
    public Result<Long> createPromotion(@Valid @RequestBody PromotionSaveRequest req) {
        return Result.ok(promotionService.createPromotion(req, currentStoreId()));
    }

    @Operation(summary = "编辑活动", description = "仅「草稿」状态可编辑")
    @PutMapping("/admin/promotions/{id}")
    @RequirePermission("marketing:promotion")
    @AuditLog(action = "编辑促销活动")
    public Result<Void> updatePromotion(@PathVariable Long id, @Valid @RequestBody PromotionSaveRequest req) {
        promotionService.updatePromotion(id, req, currentStoreId());
        return Result.ok();
    }

    @Operation(summary = "发布活动", description = "草稿 → 进行中（0→1）；结束时间已过则拒绝发布")
    @PostMapping("/admin/promotions/{id}/publish")
    @RequirePermission("marketing:promotion")
    @AuditLog(action = "发布促销活动")
    public Result<Void> publishPromotion(@PathVariable Long id) {
        promotionService.publishPromotion(id);
        return Result.ok();
    }

    @Operation(summary = "结束活动", description = "进行中 → 已结束（1→2）；未到结束时间也可提前结束")
    @PostMapping("/admin/promotions/{id}/stop")
    @RequirePermission("marketing:promotion")
    @AuditLog(action = "结束促销活动")
    public Result<Void> stopPromotion(@PathVariable Long id) {
        promotionService.stopPromotion(id);
        return Result.ok();
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

    // ==================== C 端（小程序，v29） ====================

    @Operation(summary = "我的积分", description = "当前登录用户积分账户概览；从未产生往来时返回零值账户")
    @GetMapping("/points/my")
    public Result<MyPointsVO> myPoints() {
        return Result.ok(MyPointsVO.of(marketingService.pointsAccount(UserContext.userId())));
    }

    @Operation(summary = "我的积分明细", description = "当前登录用户积分流水分页（changeType：1发放 / 2抵扣 / 3退款回收 / 4过期清零 / 5签到）")
    @GetMapping("/points/my/records")
    public Result<IPage<PointRecordVO>> myPointRecords(
            @RequestParam(required = false) Integer changeType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(marketingService.pagePointRecords(UserContext.userId(), changeType, page, size));
    }

    @Operation(summary = "我的券包",
            description = "当前登录用户持券分页；storeId/usableAmount 用于下单页筛选「本单可用券」，status 不传查全部")
    @GetMapping("/coupons/my")
    public Result<IPage<UserCouponVO>> myCoupons(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) BigDecimal usableAmount,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(couponService.pageMyCoupons(
                UserContext.userId(), storeId, usableAmount, status, page, size));
    }

    @Operation(summary = "门店生效活动", description = "C 端：平台活动 + 该门店本店活动（进行中且时间窗口命中）；storeId 不传仅返回平台活动")
    @GetMapping("/promotions/active")
    public Result<List<PromotionVO>> activePromotions(@RequestParam(required = false) Long storeId) {
        return Result.ok(promotionService.listActive(storeId));
    }
}
