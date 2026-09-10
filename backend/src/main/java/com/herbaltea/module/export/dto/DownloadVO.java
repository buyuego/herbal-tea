package com.herbaltea.module.export.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下载响应 VO（v33）：前端用 fileName + base64/content 调用浏览器下载，或 window.open 直链
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "下载响应")
public class DownloadVO {

    @Schema(description = "文件字节")
    private Long fileSize;

    @Schema(description = "文件名（不含路径）")
    private String fileName;

    @Schema(description = "相对路径（dev 用，prod 应换 OSS URL）")
    private String filePath;
}
