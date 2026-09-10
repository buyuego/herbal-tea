package com.herbaltea.module.export.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.infrastructure.scheduler.ScheduledTask;
import com.herbaltea.infrastructure.scheduler.TaskRunner;
import com.herbaltea.module.export.dto.ExportRequest;
import com.herbaltea.module.export.entity.ExportTask;
import com.herbaltea.module.export.exporter.Exporter;
import com.herbaltea.module.export.mapper.ExportTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 导出 Worker（v33）
 *
 * <p>每 5s 扫一次 pending 任务（status=0）：
 * <ul>
 *   <li>Redis 分布式锁防多实例并发</li>
 *   <li>SELECT pending + CAS 抢锁（status 0→10）保证多实例仅一个 Worker 抢到</li>
 *   <li>执行 Exporter，落地文件后 markFinished status=20</li>
 *   <li>失败 → markFinished status=30 + error_msg；运营手动重试或重新创建</li>
 *   <li>文件路径：{@code exports/{yyyyMMdd}/{taskNo}.xlsx}</li>
 * </ul>
 *
 * <p>为什么不放 Service：抽取为独立 task 类保持 Service 单测不依赖调度；与 PromotionCloseTask 一脉相承。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ScheduledTask(task = "export_worker")
public class ExportWorker {

    private final TaskRunner taskRunner;
    private final ExportTaskMapper exportTaskMapper;
    private final Map<String, Exporter> exporterMap;
    private final ObjectMapper objectMapper;

    @Value("${app.export.dir:exports}")
    private String exportDir;

    /** 每 5 秒轮询 */
    @Scheduled(fixedDelay = 5000L)
    public void run() {
        if (!taskRunner.acquireLock("export_worker")) {
            return;
        }
        AtomicBoolean hasError = new AtomicBoolean(false);
        try {
            List<ExportTask> pending = exportTaskMapper.selectPending(20);
            for (ExportTask t : pending) {
                // CAS 抢锁（status=0 → 10），其他实例抢不到
                int rows = exportTaskMapper.tryAcquire(t.getId());
                if (rows != 1) {
                    continue;
                }
                try {
                    processOne(t);
                } catch (Exception ex) {
                    hasError.set(true);
                    log.error("[导出任务失败] taskNo={} 业务={} 错误={}", t.getTaskNo(), t.getBizType(), ex.getMessage(), ex);
                    exportTaskMapper.markFinished(
                            t.getId(), 30, 0, 0L, null,
                            truncate(ex.getMessage(), 1000),
                            LocalDateTime.now());
                }
            }
        } catch (Exception e) {
            hasError.set(true);
            log.error("[导出 Worker] 扫描异常（下轮重试）", e);
        } finally {
            taskRunner.recordSuccess("export_worker");
        }
    }

    private void processOne(ExportTask t) throws Exception {
        Exporter exporter = exporterMap.get(t.getBizType());
        if (exporter == null) {
            throw new BizException(com.herbaltea.common.result.ResultCode.PARAM_ERROR, "未注册导出器: " + t.getBizType());
        }

        // 解析 filter JSON
        ExportRequest.Filter filter = null;
        if (t.getFilterJson() != null && !t.getFilterJson().isEmpty()) {
            try {
                filter = objectMapper.readValue(t.getFilterJson(), new TypeReference<ExportRequest.Filter>() {});
            } catch (Exception e) {
                log.warn("[导出] 任务 {} filter 解析失败，按无过滤处理: {}", t.getTaskNo(), e.getMessage());
                filter = new ExportRequest.Filter();
            }
        } else {
            filter = new ExportRequest.Filter();
        }

        // 落地文件路径
        LocalDate today = LocalDate.now();
        Path target = Paths.get(System.getProperty("user.dir"),
                exportDir,
                today.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                t.getTaskNo() + ".xlsx");
        Files.createDirectories(target.getParent());

        // 同步执行导出
        Exporter.Result r = exporter.export(filter, target.toString());

        // 用 fileSize 兜底（Exporter 可不返回精确字节）
        long size = r.getFileSize() > 0 ? r.getFileSize() : Files.size(target);

        String relative = exportDir + "/" + today.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "/" + t.getTaskNo() + ".xlsx";
        exportTaskMapper.markFinished(
                t.getId(), 20, r.getRowCount(), size, relative, null, LocalDateTime.now());
        log.info("[导出任务完成] taskNo={} rows={} bytes={}", t.getTaskNo(), r.getRowCount(), size);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
