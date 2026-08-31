package com.herbaltea.module.settlement;

import com.herbaltea.common.result.PageResult;
import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.settlement.dto.SettlementDetailVO;
import com.herbaltea.module.settlement.dto.SettlementPageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 结算管理接口（B 端后台，menu:settlement 权限树）
 *
 * <p>权限矩阵（V2 预设角色）：
 * <ul>
 *   <li>menu:settlement（107）：列表/详情——超管、平台财务持有</li>
 *   <li>settlement:review（213）：平台审核 20→30——超管、平台财务</li>
 *   <li>settlement:payout（214，敏感）：打款确认 30→40——仅超管（财务不可打款）</li>
 *   <li>settlement:reconcile（215）：对账复核生成调整单——超管、平台财务</li>
 * </ul>
 *
 * <p>v24：结算异议申诉闭环——店长（menu:settlement + 本店）对结算单提出异议
 * （dispute → confirm_status=3），超管/财务复核生成调整单（reconcile → type=3）。
 */
@Tag(name = "结算管理")
@RestController
@RequestMapping("/api/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @Operation(summary = "结算单分页")
    @GetMapping("/admin/page")
    @RequirePermission("menu:settlement")
    public Result<PageResult<SettlementDetailVO>> page(@Valid SettlementPageQuery query) {
        return Result.ok(settlementService.page(query));
    }

    @Operation(summary = "结算单详情（含明细分行 D15）")
    @GetMapping("/admin/{settlementId}")
    @RequirePermission("menu:settlement")
    public Result<SettlementDetailVO> detail(@PathVariable Long settlementId) {
        return Result.ok(settlementService.detail(settlementId));
    }

    @Operation(summary = "生成结算单（dev 造数/定时任务入口）",
            description = "按店按周期聚合已完结未结算订单；storeId 为空 = 全部门店")
    @PostMapping("/admin/generate")
    @RequirePermission("menu:settlement")
    public Result<Void> generate(@RequestParam(required = false) Long storeId,
                                 @RequestParam String period) {
        settlementService.generate(storeId, period);
        return Result.ok();
    }

    @Operation(summary = "结算单确认（10→20；dev 手动触发，生产为 3 天自动确认任务）")
    @PostMapping("/admin/{settlementId}/confirm")
    @RequirePermission("menu:settlement")
    @AuditLog(action = "结算确认")
    public Result<Void> confirm(@PathVariable Long settlementId) {
        settlementService.confirm(settlementId, UserContext.get().getAdminId());
        return Result.ok();
    }

    @Operation(summary = "平台审核通过（20→30，settlement:review）")
    @PostMapping("/admin/{settlementId}/review")
    @RequirePermission("settlement:review")
    @AuditLog(action = "结算审核")
    public Result<Void> review(@PathVariable Long settlementId) {
        settlementService.review(settlementId, UserContext.get().getAdminId());
        return Result.ok();
    }

    @Operation(summary = "打款确认（30→40，settlement:payout 敏感权限仅超管；dev 模拟分账）")
    @PostMapping("/admin/{settlementId}/pay")
    @RequirePermission("settlement:payout")
    @AuditLog(action = "结算打款")
    public Result<Void> pay(@PathVariable Long settlementId) {
        settlementService.pay(settlementId, UserContext.get().getAdminId());
        return Result.ok();
    }

    @Operation(summary = "结算异议申诉（店长本店，confirm_status→3 有异议）",
            description = "仅待确认/审核期（status≤20）可申诉；有异议单不会被自动确认任务吞掉")
    @PostMapping("/admin/{settlementId}/dispute")
    @RequirePermission("menu:settlement")
    @AuditLog(action = "结算异议申诉")
    public Result<Void> dispute(@PathVariable Long settlementId, @RequestBody DisputeRequest req) {
        settlementService.dispute(settlementId, req.getNote());
        return Result.ok();
    }

    @Operation(summary = "复核生成调整单（settlement:reconcile，超管/财务）",
            description = "对「有异议」结算单复核：原单 adjust_amount/final_amount 更新 + type=8 调整行"
                    + " + 生成 type=3 调整单（parent 关联原单，复用确认→审核→打款状态机）")
    @PostMapping("/admin/{settlementId}/reconcile")
    @RequirePermission("settlement:reconcile")
    @AuditLog(action = "结算复核调整")
    public Result<Long> reconcile(@PathVariable Long settlementId, @Valid @RequestBody ReconcileRequest req) {
        return Result.ok(settlementService.reconcile(settlementId, req.getAdjustAmount(), req.getRemark()));
    }

    /** 申诉请求体 */
    public static class DisputeRequest {
        private String note;
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    /** 复核请求体 */
    public static class ReconcileRequest {
        @jakarta.validation.constraints.NotNull(message = "adjustAmount 必填")
        @jakarta.validation.constraints.DecimalMin(value = "0.01", message = "调整金额最小 0.01")
        private java.math.BigDecimal adjustAmount;
        private String remark;
        public java.math.BigDecimal getAdjustAmount() { return adjustAmount; }
        public void setAdjustAmount(java.math.BigDecimal adjustAmount) { this.adjustAmount = adjustAmount; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}
