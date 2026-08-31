package com.herbaltea.module.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.order.dto.CreateOrderRequest;
import com.herbaltea.module.order.dto.OrderCreateVO;
import com.herbaltea.module.order.dto.OrderDetailVO;
import com.herbaltea.module.order.dto.OrderPageQuery;
import com.herbaltea.module.order.dto.ShipRequest;
import com.herbaltea.module.order.entity.Order;
import com.herbaltea.module.order.entity.OrderShippingLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单接口（B 端后台 + C 端小程序）
 *
 * <p>B 端：分页/详情/代客下单/发货/物流轨迹（order:ship 等权限，由后续权限拦截器校验）。
 * C 端：下单/取消/签收/我的订单——userId 取自登录上下文（User 模块联测后启用）。
 */
@Tag(name = "订单模块")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ==================== B 端（总部/门店后台） ====================

    @Operation(summary = "订单分页查询", description = "订单号精确 / 用户 / 门店 / 状态过滤")
    @GetMapping("/admin/page")
    @RequirePermission("menu:order")
    public Result<IPage<Order>> page(OrderPageQuery query) {
        return Result.ok(orderService.pageOrders(query));
    }

    @Operation(summary = "订单详情", description = "订单头 + 明细 + 支付单 + 规格 JSON 解析")
    @GetMapping("/admin/{orderId}")
    @RequirePermission("menu:order")
    public Result<OrderDetailVO> detail(@PathVariable Long orderId) {
        return Result.ok(orderService.getOrderDetail(orderId));
    }

    @Operation(summary = "代客下单", description = "门店/总部为会员下单（order:create:behalf），"
            + "Idempotency-Key 请求头必填（24h 防重），库存原子扣减，返回收银台参数")
    @PostMapping("/admin/behalf")
    @RequirePermission("order:create:behalf")
    @AuditLog(action = "代客下单")
    public Result<OrderCreateVO> createForUser(
            @Valid @RequestBody CreateOrderRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Parameter(description = "幂等键（UUID，24h 窗口防重）") String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Result.fail(com.herbaltea.common.result.ResultCode.PARAM_ERROR,
                    "缺少 Idempotency-Key 请求头");
        }
        return Result.ok(orderService.createOrderForUser(req, idempotencyKey));
    }

    @Operation(summary = "订单发货", description = "30待发货→40已发货，写物流单+轨迹日志（order:ship）")
    @PostMapping("/admin/{orderId}/ship")
    @RequirePermission("order:ship")
    @AuditLog(action = "订单发货")
    public Result<Void> ship(@PathVariable Long orderId, @Valid @RequestBody ShipRequest req) {
        orderService.ship(orderId, UserContext.get().getAdminId(), req);
        return Result.ok();
    }

    @Operation(summary = "物流轨迹", description = "订单全部物流日志（时间升序）")
    @GetMapping("/admin/{orderId}/shipping-logs")
    @RequirePermission("menu:order")
    public Result<List<OrderShippingLog>> shippingLogs(@PathVariable Long orderId) {
        return Result.ok(orderService.shippingLogs(orderId));
    }

    // ==================== C 端（小程序，User 模块联测后启用） ====================

    @Operation(summary = "下单", description = "C 端下单：userId 取自登录上下文，"
            + "Idempotency-Key 必填防重；库存原子扣减 + 支付单同库事务")
    @PostMapping("/create")
    public Result<OrderCreateVO> create(
            @Valid @RequestBody CreateOrderRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Result.fail(com.herbaltea.common.result.ResultCode.PARAM_ERROR,
                    "缺少 Idempotency-Key 请求头");
        }
        Long userId = UserContext.userId();
        if (userId == null) {
            return Result.fail(com.herbaltea.common.result.ResultCode.UNAUTHORIZED, "请先登录");
        }
        return Result.ok(orderService.createOrder(userId, req, idempotencyKey));
    }

    @Operation(summary = "取消订单", description = "仅待支付可取消（10→70），回滚库存")
    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId) {
        orderService.cancel(orderId, UserContext.userId());
        return Result.ok();
    }

    @Operation(summary = "确认签收", description = "40已发货→50已签收（归属校验），解锁积分/结算")
    @PostMapping("/{orderId}/sign")
    public Result<Void> sign(@PathVariable Long orderId) {
        orderService.sign(orderId, UserContext.userId());
        return Result.ok();
    }

    @Operation(summary = "我的订单", description = "当前登录用户订单分页")
    @GetMapping("/mine")
    public Result<IPage<Order>> mine(OrderPageQuery query) {
        query.setUserId(UserContext.userId());
        return Result.ok(orderService.pageOrders(query));
    }
}
