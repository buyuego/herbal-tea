package com.herbaltea.module.export.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.herbaltea.module.export.entity.ExportTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导出任务列表 VO（v33）
 */
@Data
@Schema(description = "导出任务")
public class ExportTaskVO {

    @Schema(description = "任务 id")
    private Long id;

    @Schema(description = "任务单号")
    private String taskNo;

    @Schema(description = "业务类型 product/order/member/settlement")
    private String bizType;

    @Schema(description = "操作人")
    private String operatorName;

    @Schema(description = "0 待处理 10 处理中 20 成功 30 失败")
    private Integer status;

    @Schema(description = "导出文件行数")
    private Integer rowCount;

    @Schema(description = "文件字节")
    private Long fileSize;

    @Schema(description = "可下载标记")
    private Boolean downloadable;

    @Schema(description = "错误信息")
    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public static ExportTaskVO from(ExportTask e) {
        ExportTaskVO v = new ExportTaskVO();
        v.id = e.getId();
        v.taskNo = e.getTaskNo();
        v.bizType = e.getBizType();
        v.operatorName = e.getOperatorName();
        v.status = e.getStatus();
        v.rowCount = e.getRowCount();
        v.fileSize = e.getFileSize();
        v.downloadable = Boolean.TRUE.equals(e.getStatus() == 20);
        v.errorMsg = e.getErrorMsg();
        v.startedAt = e.getStartedAt();
        v.finishedAt = e.getFinishedAt();
        v.createdAt = e.getCreatedAt();
        return v;
    }
}
