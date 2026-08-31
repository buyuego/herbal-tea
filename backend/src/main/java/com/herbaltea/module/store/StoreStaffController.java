package com.herbaltea.module.store;

import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.PageResult;
import com.herbaltea.common.result.Result;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.store.dto.StaffCreateRequest;
import com.herbaltea.module.store.dto.StaffPasswordRequest;
import com.herbaltea.module.store.dto.StaffUpdateRequest;
import com.herbaltea.module.store.dto.StaffVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工账号管理接口（v11，store:staff:manage）
 *
 * <p>门店自治：门店管理员（STORE_ADMIN）管理本店员工（STORE_STAFF 角色）。
 * 约束：
 * <ul>
 *   <li>全部接口 {@code store:staff:manage}（V2 预置仅 STORE_ADMIN 角色持有，员工/仓管 40300）</li>
 *   <li>storeId 一律取登录上下文（SINGLE_STORE 数据范围），总部无绑定门店 40000</li>
 *   <li>操作目标限本店员工角色；禁用/改密/移除即时吊销旧令牌（R9）</li>
 * </ul>
 * 路由：{@code /api/store/staff}。
 */
@Tag(name = "门店", description = "员工账号管理（store:staff:manage，本店自治）")
@RestController
@RequestMapping("/api/store/staff")
@RequiredArgsConstructor
public class StoreStaffController {

    private final StoreStaffService storeStaffService;

    @Operation(summary = "本店员工分页", description = "boundStatus：null 全部 / 1 正常绑定 / 0 已移除；含角色名/绑定时间")
    @GetMapping
    @RequirePermission("store:staff:manage")
    public Result<PageResult<StaffVO>> pageStaff(
            @RequestParam(required = false) Integer boundStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(storeStaffService.pageStaff(requireStore(), boundStatus, page, size));
    }

    @Operation(summary = "创建员工", description = "强制 STORE_STAFF 角色并绑定本店；username 全局唯一（重复 40900）；"
            + "已移除账号可复绑复用")
    @PostMapping
    @RequirePermission("store:staff:manage")
    @AuditLog(action = "创建员工")
    public Result<Long> createStaff(@Valid @RequestBody StaffCreateRequest req) {
        return Result.ok(storeStaffService.createStaff(requireStore(), req));
    }

    @Operation(summary = "更新员工", description = "姓名/手机号/启用禁用；禁用时旧令牌即时失效（40101）")
    @PutMapping("/{adminId}")
    @RequirePermission("store:staff:manage")
    @AuditLog(action = "更新员工")
    public Result<Void> updateStaff(@PathVariable Long adminId,
                                    @Valid @RequestBody StaffUpdateRequest req) {
        storeStaffService.updateStaff(requireStore(), adminId, req);
        return Result.ok();
    }

    @Operation(summary = "重置员工密码", description = "改密后旧令牌全部失效，员工需重新登录")
    @PutMapping("/{adminId}/password")
    @RequirePermission("store:staff:manage")
    @AuditLog(action = "重置员工密码")
    public Result<Void> resetPassword(@PathVariable Long adminId,
                                      @Valid @RequestBody StaffPasswordRequest req) {
        storeStaffService.resetPassword(requireStore(), adminId, req.newPassword());
        return Result.ok();
    }

    @Operation(summary = "移除员工", description = "软删绑定（store_admins.status=0）并即时吊销令牌；"
            + "账号保留，可重新添加复绑；已移除幂等")
    @DeleteMapping("/{adminId}")
    @RequirePermission("store:staff:manage")
    @AuditLog(action = "移除员工")
    public Result<Void> removeStaff(@PathVariable Long adminId) {
        storeStaffService.removeStaff(requireStore(), adminId);
        return Result.ok();
    }

    /** 本店上下文校验：SINGLE_STORE 数据范围强制本店操作，总部无绑定门店拒绝 */
    private Long requireStore() {
        Long storeId = UserContext.storeId();
        if (storeId == null || storeId == 0L) {
            throw new BizException(ResultCode.PARAM_ERROR, "当前账号未绑定门店，无法管理员工");
        }
        return storeId;
    }
}
