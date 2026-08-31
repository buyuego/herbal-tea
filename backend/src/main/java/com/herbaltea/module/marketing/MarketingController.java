package com.herbaltea.module.marketing;

import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.web.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 营销接口（B 端后台，v27）
 *
 * <p>积分过期回收由 {@code PointsExpireTask} 每日 03:00 自动执行；
 * 本接口供运维手动补偿执行（例如补跑、数据修复后），幂等可重复调用。
 */
@Tag(name = "营销", description = "积分过期回收（手动触发）")
@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingController {

    private final MarketingService marketingService;

    @Operation(summary = "手动执行积分过期回收", description = "到期批次清零入流水 + 到期前 7 天提醒；幂等，可安全重复执行")
    @PostMapping("/admin/points/expire")
    @RequirePermission("marketing:points:run")
    @AuditLog(action = "手动积分过期回收")
    public Result<Integer> expirePoints() {
        return Result.ok(marketingService.expirePoints());
    }
}
