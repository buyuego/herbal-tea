package com.herbaltea.module.export.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbaltea.common.exception.BizException;
import com.herbaltea.common.result.PageResult;
import com.herbaltea.common.result.ResultCode;
import com.herbaltea.module.auth.entity.AdminUser;
import com.herbaltea.module.auth.mapper.AdminUserMapper;
import com.herbaltea.module.export.dto.DownloadVO;
import com.herbaltea.module.export.dto.ExportRequest;
import com.herbaltea.module.export.dto.ExportTaskVO;
import com.herbaltea.module.export.entity.ExportTask;
import com.herbaltea.module.export.exporter.Exporter;
import com.herbaltea.module.export.mapper.ExportTaskMapper;
import com.herbaltea.module.export.service.ExportTaskService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 导出任务服务实现（v33）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportTaskServiceImpl implements ExportTaskService {

    private final ExportTaskMapper exportTaskMapper;
    private final AdminUserMapper adminUserMapper;
    /** Spring 自动按 type=Exporter 注入所有实现，bean 名作为 key（productExporter/orderExporter 等） */
    private final Map<String, Exporter> exporterMap;
    private final ObjectMapper objectMapper;

    /** 导出目录：dev 写到 backend/exports/ */
    @Value("${app.export.dir:exports}")
    private String exportDir;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    @Transactional
    public ExportTaskVO create(ExportRequest request, Long operatorId, String operatorName) {
        if (request == null || request.getBizType() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "bizType 不能为空");
        }
        String bizType = request.getBizType().trim();
        if (!exporterMap.containsKey(bizType)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的导出类型: " + bizType);
        }
        ExportTask t = new ExportTask();
        t.setTaskNo("EX" + LocalDate.now().format(DATE_FMT) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        t.setBizType(bizType);
        t.setOperatorId(operatorId);
        t.setOperatorName(operatorName);
        try {
            t.setFilterJson(objectMapper.writeValueAsString(request.getFilter()));
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.PARAM_ERROR, "filter 序列化失败");
        }
        t.setStatus(0);
        t.setRowCount(0);
        t.setFileSize(0L);
        exportTaskMapper.insert(t);
        log.info("[导出任务创建] taskNo={} bizType={} operator={}", t.getTaskNo(), bizType, operatorName);
        return ExportTaskVO.from(t);
    }

    @Override
    public PageResult<ExportTaskVO> pageByOperator(Long operatorId, Integer status,
                                                    Integer page, Integer size) {
        long offset = (long) Math.max(0, (page - 1)) * size;
        List<ExportTask> rows = exportTaskMapper.selectByOperator(operatorId, status, offset, size);
        long total = exportTaskMapper.countByOperator(operatorId, status);
        List<ExportTaskVO> vos = rows.stream().map(ExportTaskVO::from).collect(Collectors.toList());
        return PageResult.of(total, page, size, vos);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        ExportTask t = mustOwned(id, operatorId);
        // 删文件（仅本地路径；OSS 走对象删除）
        if (t.getFilePath() != null && !t.getFilePath().isEmpty()) {
            try {
                Path p = resolveExportPath(t.getFilePath());
                Files.deleteIfExists(p);
            } catch (IOException e) {
                log.warn("[导出] 任务 {} 文件删除失败 {}", t.getTaskNo(), e.getMessage());
            }
        }
        exportTaskMapper.deleteById(id);
    }

    @Override
    public DownloadVO getDownloadInfo(Long id, Long operatorId) {
        ExportTask t = mustOwned(id, operatorId);
        if (t.getStatus() != 20) {
            throw new BizException(ResultCode.PARAM_ERROR, "任务未完成，无法下载");
        }
        return DownloadVO.builder()
                .fileSize(t.getFileSize())
                .fileName(t.getTaskNo() + ".xlsx")
                .filePath(t.getFilePath())
                .build();
    }

    @Override
    public void writeDownloadStream(Long id, Long operatorId, HttpServletResponse response) {
        ExportTask t = mustOwned(id, operatorId);
        if (t.getStatus() != 20) {
            throw new BizException(ResultCode.PARAM_ERROR, "任务未完成，无法下载");
        }
        Path p = resolveExportPath(t.getFilePath());
        if (!Files.exists(p)) {
            throw new BizException(ResultCode.NOT_FOUND, "文件已丢失");
        }
        try {
            String fileName = t.getTaskNo() + ".xlsx";
            String encodeName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodeName);
            response.setContentLengthLong(t.getFileSize());
            try (InputStream in = Files.newInputStream(p);
                 OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
                out.flush();
            }
        } catch (IOException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件流写入失败: " + e.getMessage());
        }
    }

    /**
     * 取操作人名（admin_users 表），Controller 用来传
     */
    @Override
    public String resolveOperatorName(Long operatorId) {
        AdminUser u = adminUserMapper.selectById(operatorId);
        return u == null ? ("uid:" + operatorId) : u.getUsername();
    }

    // -------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------

    private ExportTask mustOwned(Long id, Long operatorId) {
        ExportTask t = exportTaskMapper.selectById(id);
        if (t == null) {
            throw new BizException(ResultCode.NOT_FOUND, "任务不存在");
        }
        if (!t.getOperatorId().equals(operatorId)) {
            // 越权访问他人任务，统一对 40400 防枚举
            throw new BizException(ResultCode.NOT_FOUND, "任务不存在");
        }
        return t;
    }

    /** 把 "exports/xxx.xlsx" 解析为 backend/ 下的绝对路径 */
    private Path resolveExportPath(String relative) {
        // backend/ 是 user.dir（spring-boot:run 启动时 cwd）
        return Paths.get(System.getProperty("user.dir"), relative);
    }
}
