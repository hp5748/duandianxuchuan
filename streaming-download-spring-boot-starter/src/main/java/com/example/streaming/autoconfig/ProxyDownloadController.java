package com.example.streaming.autoconfig;

import com.example.streaming.core.FileInfo;
import com.example.streaming.core.PathTypeDetector;
import com.example.streaming.core.RangeRequest;
import com.example.streaming.core.RangeResponse;
import com.example.streaming.core.UnifiedDownloader;
import com.example.streaming.core.UrlValidator;
import com.example.streaming.core.exception.DomainNotAllowedException;
import com.example.streaming.core.exception.DownloadException;
import com.example.streaming.core.exception.FileTooLargeException;
import com.example.streaming.core.exception.InvalidUrlException;
import com.example.streaming.core.exception.LocalFileNotFoundException;
import com.example.streaming.core.exception.RangeNotSatisfiableException;
import com.example.streaming.core.exception.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 流式代理下载控制器
 * 支持断点续传（Range 请求）
 * 支持远程 URL 和本地文件路径下载
 */
@RestController
@RequestMapping("/api/proxy")
public class ProxyDownloadController {

    private static final Logger logger = LoggerFactory.getLogger(ProxyDownloadController.class);

    private final UnifiedDownloader downloader;
    private final UrlValidator urlValidator;

    public ProxyDownloadController(UnifiedDownloader downloader, UrlValidator urlValidator) {
        this.downloader = downloader;
        this.urlValidator = urlValidator;
    }

    /**
     * 流式代理下载（支持断点续传）
     * <p>
     * GET /api/proxy/download?url=xxx
     * <p>
     * 支持的路径类型：
     * - HTTP/HTTPS URL: https://example.com/file.zip
     * - 本地文件路径: C:\Users\Admin\Desktop\file.exe 或 /home/user/file.zip
     * - UNC 网络路径: \\server\share\file.txt
     * <p>
     * Request Headers:
     * - Range: bytes=0-1023 (可选，断点续传)
     * <p>
     * Response Headers:
     * - Content-Length: 文件大小
     * - Content-Type: MIME 类型
     * - Content-Range: bytes 0-1023/5000 (部分内容时)
     * - Accept-Ranges: bytes
     */
    @GetMapping("/download")
    public void download(
            @RequestParam String url,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            HttpServletResponse response) throws IOException, DownloadException {

        logger.info("收到下载请求: url={}, range={}", url, rangeHeader);

        // 1. 检测路径类型
        PathTypeDetector.PathType pathType = downloader.getPathType(url);
        boolean isRemote = downloader.isRemote(url);

        logger.debug("路径类型: {}, 是否远程: {}", pathType, isRemote);

        // 2. 仅对远程 URL 执行安全校验
        if (isRemote) {
            try {
                urlValidator.validate(url);
            } catch (InvalidUrlException e) {
                logger.warn("URL 校验失败: {}", e.getMessage());
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
                return;
            } catch (DomainNotAllowedException e) {
                logger.warn("域名不允许: {}", e.getMessage());
                sendError(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
                return;
            }
        }

        // 3. 获取文件信息
        FileInfo fileInfo;
        try {
            fileInfo = downloader.getFileInfo(url);
        } catch (UpstreamException e) {
            logger.error("获取文件信息失败: {}", e.getMessage());
            sendError(response, HttpServletResponse.SC_BAD_GATEWAY, e.getMessage());
            return;
        } catch (LocalFileNotFoundException e) {
            logger.warn("文件不存在: {}", e.getMessage());
            sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return;
        }

        long totalLength = fileInfo.getContentLength();
        String fileName = fileInfo.getFileName();

        // 4. 解析 Range 请求
        RangeRequest rangeRequest = RangeRequest.parse(rangeHeader, totalLength);

        // 5. 构建 Range 响应
        RangeResponse rangeResponse;
        if (rangeRequest != null) {
            rangeResponse = RangeResponse.partialResponse(rangeRequest);
            logger.debug("断点续传: start={}, end={}, total={}",
                rangeRequest.getStart(), rangeRequest.getEnd(), totalLength);
        } else {
            rangeResponse = RangeResponse.fullResponse(totalLength);
        }

        // 6. 设置响应头
        response.setStatus(rangeResponse.getStatusCode());
        response.setContentType(fileInfo.getContentType());
        response.setHeader("Content-Length", String.valueOf(rangeResponse.getContentLength()));
        response.setHeader("Accept-Ranges", "bytes");

        // Content-Disposition
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
            .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition",
            "attachment; filename*=UTF-8''" + encodedFileName);

        // Content-Range (仅部分内容)
        if (rangeResponse.isPartial()) {
            response.setHeader("Content-Range", rangeResponse.getContentRange());
        }

        // 7. 执行流式下载
        try {
            long transferred = downloader.download(url, rangeRequest, response.getOutputStream());
            logger.info("下载完成: fileName={}, transferred={} bytes", fileName, transferred);
        } catch (RangeNotSatisfiableException e) {
            logger.warn("Range 无效: {}", e.getMessage());
            response.reset();
            response.setHeader("Content-Range", "bytes */" + e.getTotalLength());
            sendError(response, 416, e.getMessage());
        } catch (FileTooLargeException e) {
            logger.warn("文件过大: {}", e.getMessage());
            sendError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, e.getMessage());
        } catch (UpstreamException e) {
            logger.error("上游服务器错误: {}", e.getMessage());
            sendError(response, HttpServletResponse.SC_BAD_GATEWAY, e.getMessage());
        } catch (LocalFileNotFoundException e) {
            logger.warn("文件不存在: {}", e.getMessage());
            sendError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        }
    }

    /**
     * 发送错误响应
     */
    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"timestamp\":\"%s\"}",
            status, escapeJson(message), Instant.now().toString());
        response.getWriter().write(json);
    }

    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * 全局异常处理
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        logger.error("下载异常", e);

        Map<String, Object> body = new HashMap<>();
        body.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("message", "服务器内部错误: " + e.getMessage());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
