package com.example.streaming.core;

import com.example.streaming.core.exception.FileTooLargeException;
import com.example.streaming.core.exception.RangeNotSatisfiableException;
import com.example.streaming.core.exception.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流式下载器
 * 核心功能：O(1) 内存占用的流式代理下载
 */
public class StreamingDownloader {

    private static final Logger logger = LoggerFactory.getLogger(StreamingDownloader.class);

    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000;
    private static final int DEFAULT_READ_TIMEOUT = 60000;
    private static final Pattern FILENAME_PATTERN = Pattern.compile("filename\\*?=['\"]?(?:UTF-8''|)([^'\"]+)['\"]?", Pattern.CASE_INSENSITIVE);

    private final int bufferSize;
    private final int connectTimeout;
    private final int readTimeout;
    private final long maxFileSize;

    public StreamingDownloader() {
        this(DEFAULT_BUFFER_SIZE, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, -1);
    }

    public StreamingDownloader(int bufferSize, int connectTimeout, int readTimeout, long maxFileSize) {
        this.bufferSize = bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
        this.connectTimeout = connectTimeout > 0 ? connectTimeout : DEFAULT_CONNECT_TIMEOUT;
        this.readTimeout = readTimeout > 0 ? readTimeout : DEFAULT_READ_TIMEOUT;
        this.maxFileSize = maxFileSize;
    }

    /**
     * 获取目标文件信息（HEAD 请求）
     *
     * @param url 目标地址
     * @return 文件信息
     * @throws UpstreamException 上游服务器错误
     */
    public FileInfo getFileInfo(String url) throws UpstreamException {
        HttpURLConnection connection = null;
        try {
            connection = createConnection(url, "HEAD");
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new UpstreamException(responseCode, url);
            }

            FileInfo fileInfo = new FileInfo();

            // Content-Length
            String contentLengthStr = connection.getHeaderField("Content-Length");
            if (contentLengthStr != null) {
                fileInfo.setContentLength(Long.parseLong(contentLengthStr));
            }

            // Content-Type
            String contentType = connection.getHeaderField("Content-Type");
            fileInfo.setContentType(contentType != null ? contentType : "application/octet-stream");

            // Accept-Ranges
            String acceptRanges = connection.getHeaderField("Accept-Ranges");
            fileInfo.setAcceptRanges("bytes".equalsIgnoreCase(acceptRanges));

            // 文件名
            String contentDisposition = connection.getHeaderField("Content-Disposition");
            fileInfo.setFileName(extractFileName(contentDisposition, url));

            return fileInfo;

        } catch (IOException e) {
            throw new UpstreamException("获取文件信息失败: " + url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 流式下载，支持 Range 请求
     *
     * @param url          目标地址
     * @param rangeRequest Range 请求（null 表示完整下载）
     * @param outputStream 响应输出流
     * @return 实际传输的字节数
     * @throws IOException                   IO 异常
     * @throws UpstreamException             上游服务器错误
     * @throws RangeNotSatisfiableException  Range 无效
     * @throws FileTooLargeException         文件过大
     */
    public long download(String url, RangeRequest rangeRequest, OutputStream outputStream)
            throws IOException, UpstreamException, RangeNotSatisfiableException, FileTooLargeException {

        HttpURLConnection connection = null;
        InputStream inputStream = null;

        try {
            connection = createConnection(url, "GET");

            // 设置 Range 请求头
            if (rangeRequest != null) {
                connection.setRequestProperty("Range",
                    String.format("bytes=%d-%d", rangeRequest.getStart(), rangeRequest.getEnd()));
            }

            connection.connect();

            int responseCode = connection.getResponseCode();

            // 处理响应状态
            if (rangeRequest != null) {
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 服务器不支持 Range，返回完整内容
                    logger.warn("服务器不支持 Range 请求，返回完整内容: {}", url);
                } else if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    // 206 Partial Content - 正常
                } else if (responseCode == 416) {
                    // Range Not Satisfiable
                    throw new RangeNotSatisfiableException(rangeRequest.getStart(),
                        getContentLength(connection));
                } else {
                    throw new UpstreamException(responseCode, url);
                }
            } else {
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new UpstreamException(responseCode, url);
                }
            }

            // 获取文件大小
            long contentLength = getContentLength(connection);

            // 检查文件大小限制
            if (maxFileSize > 0 && contentLength > maxFileSize) {
                throw new FileTooLargeException(contentLength, maxFileSize);
            }

            // 流式拷贝 - O(1) 内存占用
            inputStream = connection.getInputStream();
            return streamCopy(inputStream, outputStream);

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.debug("关闭输入流失败", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 创建 HTTP 连接
     */
    private HttpURLConnection createConnection(String url, String method) throws IOException {
        URL targetUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setInstanceFollowRedirects(true);

        // 设置通用请求头
        connection.setRequestProperty("User-Agent", "StreamingDownloadProxy/1.0");

        return connection;
    }

    /**
     * 获取 Content-Length
     */
    private long getContentLength(HttpURLConnection connection) {
        String contentLengthStr = connection.getHeaderField("Content-Length");
        if (contentLengthStr != null) {
            return Long.parseLong(contentLengthStr);
        }

        // 对于 Range 请求，尝试解析 Content-Range
        String contentRange = connection.getHeaderField("Content-Range");
        if (contentRange != null) {
            // Content-Range: bytes 0-1023/5000
            String[] parts = contentRange.split("/");
            if (parts.length == 2) {
                return Long.parseLong(parts[1].trim());
            }
        }

        return -1;
    }

    /**
     * 流式拷贝 - 核心方法，确保 O(1) 内存占用
     */
    private long streamCopy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[bufferSize];
        long totalBytes = 0;
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
        }

        output.flush();
        return totalBytes;
    }

    /**
     * 从 Content-Disposition 或 URL 中提取文件名
     */
    private String extractFileName(String contentDisposition, String url) {
        // 尝试从 Content-Disposition 解析
        if (contentDisposition != null) {
            Matcher matcher = FILENAME_PATTERN.matcher(contentDisposition);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        // 从 URL 中提取
        try {
            URL parsedUrl = new URL(url);
            String path = parsedUrl.getPath();
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                return path.substring(lastSlash + 1);
            }
        } catch (Exception e) {
            // 忽略解析错误
        }

        return "download";
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }
}
