package com.herbaltea.module.notification.controller;

import com.herbaltea.common.result.PageResult;
import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.notification.dto.BroadcastRequest;
import com.herbaltea.module.notification.dto.NotificationQuery;
import com.herbaltea.module.notification.dto.NotificationSummary;
import com.herbaltea.module.notification.dto.NotificationVO;
import com.herbaltea.module.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口（v32，B 端顶部铃铛 + /notification 通知页）
 *
 * <ul>
 *   <li>GET /summary：铃铛摘要（未读数 + 最近 10 条），每 30s 轮询</li>
 *   <li>GET /page：通知页全量分页（支持 unreadOnly / bizType 过滤）</li>
 *   <li>PUT /{id}/read：标记单条已读</li>
 *   <li>PUT /read-all：标记全部已读</li>
 *   <li>DELETE /{id}：删除单条</li>
 *   <li>POST /broadcast：管理员广播（仅超管 notification:manage）</li>
 * </ul>
 */
@Tag(name = "通知中心", description = "站内通知（铃铛 + 通知页 + 广播）")
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    private Long currentAdminId() {
        return UserContext.get().getAdminId();
    }

    @Operation(summary = "铃铛摘要：未读数 + 最近 10 条")
    @GetMapping("/summary")
    @RequirePermission("menu:notification")
    public Result<NotificationSummary> summary() {
        return Result.ok(notificationService.summary(currentAdminId()));
    }

    @Operation(summary = "通知页全量分页")
    @GetMapping("/page")
    @RequirePermission("menu:notification")
    public Result<PageResult<NotificationVO>> page(@ModelAttribute NotificationQuery query) {
        return Result.ok(notificationService.listPage(currentAdminId(), query));
    }

    @Operation(summary = "标记单条已读")
    @PutMapping("/{id}/read")
    @RequirePermission("menu:notification")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id, currentAdminId());
        return Result.ok();
    }

    @Operation(summary = "标记全部已读")
    @PutMapping("/read-all")
    @RequirePermission("menu:notification")
    public Result<Integer> markAllRead() {
        return Result.ok(notificationService.markAllRead(currentAdminId()));
    }

    @Operation(summary = "删除单条")
    @DeleteMapping("/{id}")
    @RequirePermission("menu:notification")
    public Result<Void> delete(@PathVariable Long id) {
        notificationService.delete(id, currentAdminId());
        return Result.ok();
    }

    @Operation(summary = "管理员广播（仅超管）")
    @PostMapping("/broadcast")
    @RequirePermission("notification:manage")
    public Result<Integer> broadcast(@Valid @RequestBody BroadcastRequest req) {
        return Result.ok(notificationService.broadcast(currentAdminId(), req));
    }
}