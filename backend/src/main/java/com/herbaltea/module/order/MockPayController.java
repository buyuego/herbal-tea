package com.herbaltea.module.order;

import com.herbaltea.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 模拟支付接口（仅 dev 环境加载，生产 profile 不注册）
 *
 * <p>用于本地全链路联调：调用 {@link OrderService#handlePaid} 模拟微信支付成功回调
 * （真实环境由 WxPayNotifyController 验签后调用，见支付模块 TODO）。
 */
@Tag(name = "内部接口（dev 专用）")
@Profile("dev")
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class MockPayController {

    private final OrderService orderService;

    @Operation(summary = "模拟支付成功", description = "按支付单号触发 handlePaid（10→20→30 + order_paid 事件）")
    @PostMapping("/mock-pay")
    public Result<Void> mockPay(@RequestBody Map<String, String> body) {
        String payNo = body.get("payNo");
        if (payNo == null || payNo.isBlank()) {
            return Result.fail(com.herbaltea.common.result.ResultCode.PARAM_ERROR, "payNo 必填");
        }
        orderService.handlePaid(payNo, "MOCK-" + System.currentTimeMillis());
        return Result.ok();
    }
}
