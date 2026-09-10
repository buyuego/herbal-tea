package com.herbaltea.module.export.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseCreatedOnlyEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * B 端导出任务（v33）
 *
 * <p>异步导出：Worker 每 5s 轮询待处理任务（status=0），CAS 抢锁后写 10；
 * 写文件成功后置 20，失败置 30 + error_msg。
 *
 * <p>继承 {@link BaseCreatedOnlyEntity}（V17 schema 无 updated_at 列）——遵循项目约定
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("export_tasks")
public class ExportTask extends BaseCreatedOnlyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_no")
    private String taskNo;

    @TableField("biz_type")
    private String bizType;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("operator_name")
    private String operatorName;

    /** filter 条件 JSON：storeId / status / dateFrom / dateTo / keyword 等 */
    @TableField("filter_json")
    private String filterJson;

    @TableField("row_count")
    private Integer rowCount;

    @TableField("file_size")
    private Long fileSize;

    /** 相对 backend/ 路径（导出目录下文件）；prod 应换 OSS URL */
    @TableField("file_path")
    private String filePath;

    /** 0 待处理 10 处理中 20 成功 30 失败 */
    @TableField("status")
    private Integer status;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
