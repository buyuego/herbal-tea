package com.herbaltea.module.export.controller;

import com.herbaltea.common.result.PageResult;
import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.export.dto.ExportRequest;
import com.herbaltea.module.export.dto.ExportTaskVO;
import com.herbaltea.module.export.service.ExportTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导出任务 Controller（v33）
 *
 * <p>权限约定：
 * <ul>
 *   <li>所有端点需 menu:export（个人任务页菜单）</li>
 *   <li>/create 需 export:run（敏感仅超管）</li>
 *   <li>其余端点默认本人隔离</li>
 * </ul>
 */
@Tag(name = "导出中心", description = "v33 B 端 Excel 导出任务管理")
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportTaskController {

    private final ExportTaskService exportTaskService;

    /** 创建导出任务（需 export:run 敏感权限，仅超管可触发） */
    @PostMapping("/tasks")
    @RequirePermission("export:run")
    public Result<ExportTaskVO> create(@RequestBody ExportRequest request) {
        Long adminId = UserContext.get().getAdminId();
        String name = exportTaskService.resolveOperatorName(adminId);
        return Result.ok(exportTaskService.create(request, adminId, name));
    }

    /** 我的任务列表 */
    @GetMapping("/tasks")
    @RequirePermission("menu:export")
    public Result<PageResult<ExportTaskVO>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long adminId = UserContext.get().getAdminId();
        return Result.ok(exportTaskService.pageByOperator(adminId, status, page, size));
    }

    /** 删除任务（本人） */
    @DeleteMapping("/tasks/{id}")
    @RequirePermission("menu:export")
    public Result<Void> delete(@PathVariable Long id) {
        Long adminId = UserContext.get().getAdminId();
        exportTaskService.delete(id, adminId);
        return Result.ok();
    }

    /** 直接下载（浏览器触发保存对话框） */
    @GetMapping("/tasks/{id}/download")
    @RequirePermission("menu:export")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        Long adminId = UserContext.get().getAdminId();
        exportTaskService.writeDownloadStream(id, adminId, response);
    }
}
