package com.example.streaming.core;

import com.example.streaming.core.exception.FileTooLargeException;
import com.example.streaming.core.exception.LocalFileNotFoundException;
import com.example.streaming.core.exception.RangeNotSatisfiableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 本地文件下载器
 * 支持本地文件路径和 UNC 网络路径的流式下载
 * 支持 Range 请求（断点续传）
 */
public class LocalFileDownloader {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileDownloader.class);

    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final int bufferSize;
    private final long maxFileSize;

    public LocalFileDownloader() {
        this(DEFAULT_BUFFER_SIZE, -1);
    }

    public LocalFileDownloader(int bufferSize, long maxFileSize) {
        this.bufferSize = bufferSize > 0 ? bufferSize : DEFAULT_BUFFER_SIZE;
        this.maxFileSize = maxFileSize;
    }

    /**
     * 获取本地文件信息
     *
     * @param path     文件路径
     * @param pathType 路径类型
     * @return 文件信息
     * @throws LocalNotFoundException 文件不存在
     */
    public FileInfo getFileInfo(String path, PathTypeDetector.PathType pathType) throws LocalFileNotFoundException {
        Path filePath = resolvePath(path, pathType);

        if (!Files.exists(filePath)) {
            throw new LocalFileNotFoundException(path);
        }

        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

            if (attrs.isDirectory()) {
                throw new LocalFileNotFoundException(path + " (是目录，不是文件)");
            }

            FileInfo fileInfo = new FileInfo();
            fileInfo.setContentLength(attrs.size());
            fileInfo.setContentType(detectContentType(filePath));
            fileInfo.setAcceptRanges(true); // 本地文件始终支持 Range
            fileInfo.setFileName(extractFileName(filePath));

            return fileInfo;

        } catch (IOException e) {
            throw new LocalFileNotFoundException(path, e);
        }
    }

    /**
     * 流式下载本地文件，支持 Range 请求
     *
     * @param path          文件路径
     * @param pathType      路径类型
     * @param rangeRequest  Range 请求（null 表示完整下载）
     * @param outputStream  输出流
     * @return 实际传输的字节数
     * @throws IOException                   IO 异常
     * @throws LocalFileNotFoundException    文件不存在
     * @throws RangeNotSatisfiableException  Range 无效
     * @throws FileTooLargeException         文件过大
     */
    public long download(String path, PathTypeDetector.PathType pathType,
                         RangeRequest rangeRequest, OutputStream outputStream)
            throws IOException, LocalFileNotFoundException, RangeNotSatisfiableException, FileTooLargeException {

        Path filePath = resolvePath(path, pathType);

        if (!Files.exists(filePath)) {
            throw new LocalFileNotFoundException(path);
        }

        long fileSize = Files.size(filePath);

        // 检查文件大小限制
        if (maxFileSize > 0 && fileSize > maxFileSize) {
            throw new FileTooLargeException(fileSize, maxFileSize);
        }

        // 校验 Range 请求
        if (rangeRequest != null) {
            if (rangeRequest.getStart() >= fileSize) {
                throw new RangeNotSatisfiableException(rangeRequest.getStart(), fileSize);
            }
        }

        // 使用 SeekableByteChannel 实现流式读取
        try (SeekableByteChannel channel = Files.newByteChannel(filePath, StandardOpenOption.READ)) {

            long start = 0;
            long end = fileSize - 1;

            if (rangeRequest != null) {
                start = rangeRequest.getStart();
                end = Math.min(rangeRequest.getEnd(), fileSize - 1);
                channel.position(start);
                logger.debug("本地文件断点续传: start={}, end={}, total={}", start, end, fileSize);
            }

            // 流式拷贝 - O(1) 内存占用
            return streamCopy(channel, outputStream, end - start + 1);
        }
    }

    /**
     * 解析路径
     * 处理 UNC 路径和本地路径
     */
    private Path resolvePath(String path, PathTypeDetector.PathType pathType) {
        if (pathType == PathTypeDetector.PathType.UNC_PATH) {
            // UNC 路径：将 \\server\share 转换为 //server/share
            // Java NIO 可以自动处理 UNC 路径
            String normalizedPath = path.replace('\\', '/');
            if (normalizedPath.startsWith("//")) {
                // 保持 //server/share 格式
                return Paths.get(normalizedPath);
            }
        }

        // Windows 本地路径和 Unix 路径直接使用
        return Paths.get(path);
    }

    /**
     * 流式拷贝 - 核心方法，确保 O(1) 内存占用
     */
    private long streamCopy(SeekableByteChannel input, OutputStream output, long bytesToRead)
            throws IOException {

        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(bufferSize);
        long totalBytes = 0;
        long remaining = bytesToRead;

        while (remaining > 0 && input.read(buffer) != -1) {
            buffer.flip();

            int bytesToWrite = (int) Math.min(buffer.remaining(), remaining);
            if (bytesToWrite < buffer.remaining()) {
                // 只写入需要的字节数
                byte[] temp = new byte[bytesToWrite];
                buffer.get(temp);
                output.write(temp);
                totalBytes += bytesToWrite;
                remaining -= bytesToWrite;
            } else {
                // 写入整个 buffer
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                output.write(data);
                totalBytes += data.length;
                remaining -= data.length;
            }

            buffer.clear();
        }

        output.flush();
        return totalBytes;
    }

    /**
     * 检测文件 MIME 类型
     */
    private String detectContentType(Path filePath) {
        try {
            String contentType = Files.probeContentType(filePath);
            return contentType != null ? contentType : DEFAULT_CONTENT_TYPE;
        } catch (IOException e) {
            logger.debug("无法检测文件类型: {}", e.getMessage());
            return DEFAULT_CONTENT_TYPE;
        }
    }

    /**
     * 从路径提取文件名
     */
    private String extractFileName(Path filePath) {
        Path fileName = filePath.getFileName();
        return fileName != null ? fileName.toString() : "download";
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }
}
