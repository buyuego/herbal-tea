package com.herbaltea.module.export.service;

import com.herbaltea.common.result.PageResult;
import com.herbaltea.module.export.dto.DownloadVO;
import com.herbaltea.module.export.dto.ExportRequest;
import com.herbaltea.module.export.dto.ExportTaskVO;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 导出任务服务（v33）
 *
 * <p>设计：
 * <ul>
 *   <li>create 创建任务即返回（不入文件，异步 worker 跑）</li>
 *   <li>page 操作人本人视角（菜单全角色可见，但只能看自己的）</li>
 *   <li>delete 仅本人可删，物理删文件</li>
 *   <li>download 仅本人可读文件流</li>
 * </ul>
 */
public interface ExportTaskService {

    /** 创建导出任务，立即返回（pending → worker 处理） */
    ExportTaskVO create(ExportRequest request, Long operatorId, String operatorName);

    /** 操作人任务列表 */
    PageResult<ExportTaskVO> pageByOperator(Long operatorId, Integer status,
                                             Integer page, Integer size);

    /** 删除任务（本人，且成功/失败都可删） */
    void delete(Long id, Long operatorId);

    /** 获取待下载资源（本人隔离 + status=20 才能下） */
    DownloadVO getDownloadInfo(Long id, Long operatorId);

    /** 直接写文件流（dev 用）；生产换 OSS */
    void writeDownloadStream(Long id, Long operatorId, HttpServletResponse response);

    /** 取操作人名（admin_users.username） */
    String resolveOperatorName(Long operatorId);
}
