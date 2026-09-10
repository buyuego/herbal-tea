package com.herbaltea.module.export.exporter;

import com.herbaltea.module.export.dto.ExportRequest;

/**
 * 导出器统一接口（v33）
 *
 * <p>Worker 调用 {@link #export} 写入 Excel 到目标目录，返回 {@link Result}：
 * <ul>
 *   <li>导出文件路径（相对 backend/）</li>
 *   <li>导出行数</li>
 *   <li>文件字节数</li>
 * </ul>
 *
 * <p>支持业务类型字符串：{@code product / order / member / settlement}。
 * 由 {@link com.herbaltea.module.export.service.ExportTaskService} 装配。
 */
public interface Exporter {

    /** 业务类型编码 */
    String bizType();

    /**
     * 同步执行导出，抛异常表示失败（Worker 捕获后置 30）。
     *
     * @param filter 过滤条件
     * @param targetPath 输出绝对路径（含 .xlsx 后缀）
     */
    Result export(ExportRequest.Filter filter, String targetPath);

    /**
     * 导出结果
     */
    @lombok.Data
    @lombok.Builder
    class Result {
        /** 写入行数（不含表头） */
        private int rowCount;
        /** 文件字节 */
        private long fileSize;
        /** 任务文件路径（相对 backend/） */
        private String relativePath;
    }
}
