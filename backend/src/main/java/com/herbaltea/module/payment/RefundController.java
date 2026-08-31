package com.herbaltea.module.payment;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.payment.dto.InspectRequest;
import com.herbaltea.module.payment.dto.RefundApplyRequest;
import com.herbaltea.module.payment.dto.RefundDetailVO;
import com.herbaltea.module.payment.dto.RefundPageQuery;
import com.herbaltea.module.payment.dto.RefundPageVO;
import com.herbaltea.module.payment.dto.RejectRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款售后接口（B 端后台，menu:refund 权限树）
 *
 * <p>权限矩阵（V2 预设角色）：
 * <ul>
 *   <li>menu:refund（104）：列表/详情——超管/财务/仓管/店长/店员均持有</li>
 *   <li>refund:submit（209）：退款申请提交——店员（角色 5）发起</li>
 *   <li>refund:approve（208）：退款审批——店长（角色 4）审批/驳回</li>
 *   <li>return:inspect（210）：退货验货——仓管（角色 3）验货</li>
 * </ul>
 * 门店数据范围：STORE 管理员仅本店退款单；总部全量。
 */
@Tag(name = "退款售后")
@RestController
@RequestMapping("/api/refund")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @Operation(summary = "退款单分页", description = "退款单号/订单号/门店/状态/分支筛选（menu:refund）")
    @GetMapping("/admin/page")
    @RequirePermission("menu:refund")
    public Result<IPage<RefundPageVO>> page(RefundPageQuery query) {
        return Result.ok(refundService.pageRefunds(query));
    }

    @Operation(summary = "退款单详情", description = "退款单 + 订单头 + 退货单（分支 3 时有值，menu:refund）")
    @GetMapping("/admin/{refundId}")
    @RequirePermission("menu:refund")
    public Result<RefundDetailVO> detail(@PathVariable Long refundId) {
        return Result.ok(refundService.detailRefund(refundId));
    }

    @Operation(summary = "退款申请", description = "门店/总部对已支付订单发起退款（refund:submit），"
            + "订单 20/30/40/50 → 60 退款中，分支 3 自动建退货单")
    @PostMapping("/admin/apply")
    @RequirePermission("refund:submit")
    @AuditLog(action = "退款申请")
    public Result<Long> apply(@Valid @RequestBody RefundApplyRequest req) {
        Long refundId = refundService.applyRefund(
                req.getOrderId(), UserContext.get().getAdminId(), req.getReason());
        return Result.ok(refundId);
    }

    @Operation(summary = "退款审批通过", description = "10待审批 → 20审批通过（refund:approve）；"
            + "未发货/在途直退直接退款，已签收退货等待验货")
    @PostMapping("/admin/{refundId}/approve")
    @RequirePermission("refund:approve")
    @AuditLog(action = "退款审批通过")
    public Result<Void> approve(@PathVariable Long refundId) {
        refundService.approveRefund(refundId, UserContext.get().getAdminId());
        return Result.ok();
    }

    @Operation(summary = "退款驳回", description = "10待审批 → 50已驳回（refund:approve）；"
            + "订单 60 退款中按分支恢复原阶段，退货单作废")
    @PostMapping("/admin/{refundId}/reject")
    @RequirePermission("refund:approve")
    @AuditLog(action = "退款驳回")
    public Result<Void> reject(@PathVariable Long refundId, @RequestBody RejectRequest req) {
        refundService.rejectRefund(refundId, UserContext.get().getAdminId(), req.getReason());
        return Result.ok();
    }

    @Operation(summary = "退货总部收货", description = "退货单 1 待收货 → 2 已收货、1 待寄回 → 3 待验货"
            + "（return:inspect）；收货后（warehouse_status=2）方可验货")
    @PostMapping("/admin/{refundId}/return/receive")
    @RequirePermission("return:inspect")
    @AuditLog(action = "退货总部收货")
    public Result<Void> receive(@PathVariable Long refundId) {
        refundService.receiveReturn(refundId, UserContext.get().getAdminId());
        return Result.ok();
    }

    @Operation(summary = "退货验货", description = "退货单已收货后方可验货（return:inspect）；"
            + "验货通过（完好/破损部分退）→ 退款 20→30→40 + 订单 80，"
            + "验货不通过（非质量问题拒退）→ 退款驳回、订单恢复已签收")
    @PostMapping("/admin/{refundId}/return/inspect")
    @RequirePermission("return:inspect")
    @AuditLog(action = "退货验货")
    public Result<Void> inspect(@PathVariable Long refundId, @Valid @RequestBody InspectRequest req) {
        refundService.inspectReturn(refundId, UserContext.get().getAdminId(), req);
        return Result.ok();
    }
}
