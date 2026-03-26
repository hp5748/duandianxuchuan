package com.example.streaming.core;

import com.example.streaming.core.exception.DownloadException;
import com.example.streaming.core.exception.FileTooLargeException;
import com.example.streaming.core.exception.LocalFileNotFoundException;
import com.example.streaming.core.exception.RangeNotSatisfiableException;
import com.example.streaming.core.exception.UpstreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 统一下载器
 * 根据路径类型自动路由到远程下载器或本地文件下载器
 */
public class UnifiedDownloader {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedDownloader.class);

    private final StreamingDownloader remoteDownloader;
    private final LocalFileDownloader localDownloader;

    public UnifiedDownloader(StreamingDownloader remoteDownloader, LocalFileDownloader localDownloader) {
        this.remoteDownloader = remoteDownloader;
        this.localDownloader = localDownloader;
    }

    /**
     * 获取文件信息
     * 自动检测路径类型并路由到对应的下载器
     *
     * @param path 文件路径（URL 或本地路径）
     * @return 文件信息
     * @throws UpstreamException         远程服务器错误
     * @throws LocalNotFoundException    本地文件不存在
     */
    public FileInfo getFileInfo(String path) throws UpstreamException, LocalFileNotFoundException {
        PathTypeDetector.PathType pathType = PathTypeDetector.detect(path);

        logger.debug("获取文件信息: path={}, type={}", path, pathType);

        switch (pathType) {
            case HTTP_URL:
                return remoteDownloader.getFileInfo(path);
            case LOCAL_FILE:
            case UNC_PATH:
                return localDownloader.getFileInfo(path, pathType);
            default:
                throw new IllegalArgumentException("不支持的路径类型: " + pathType);
        }
    }

    /**
     * 流式下载，支持 Range 请求
     * 自动检测路径类型并路由到对应的下载器
     *
     * @param path          文件路径（URL 或本地路径）
     * @param rangeRequest  Range 请求（null 表示完整下载）
     * @param outputStream  响应输出流
     * @return 实际传输的字节数
     * @throws IOException                   IO 异常
     * @throws UpstreamException             远程服务器错误
     * @throws LocalNotFoundException        本地文件不存在
     * @throws RangeNotSatisfiableException  Range 无效
     * @throws FileTooLargeException         文件过大
     */
    public long download(String path, RangeRequest rangeRequest, OutputStream outputStream)
            throws IOException, DownloadException {

        PathTypeDetector.PathType pathType = PathTypeDetector.detect(path);

        logger.debug("开始下载: path={}, type={}, range={}", path, pathType, rangeRequest);

        switch (pathType) {
            case HTTP_URL:
                return remoteDownloader.download(path, rangeRequest, outputStream);
            case LOCAL_FILE:
            case UNC_PATH:
                return localDownloader.download(path, pathType, rangeRequest, outputStream);
            default:
                throw new IllegalArgumentException("不支持的路径类型: " + pathType);
        }
    }

    /**
     * 获取路径类型
     *
     * @param path 文件路径
     * @return 路径类型
     */
    public PathTypeDetector.PathType getPathType(String path) {
        return PathTypeDetector.detect(path);
    }

    /**
     * 判断是否为远程路径
     *
     * @param path 文件路径
     * @return 是否为远程路径
     */
    public boolean isRemote(String path) {
        return PathTypeDetector.isRemote(path);
    }

    /**
     * 判断是否为本地路径
     *
     * @param path 文件路径
     * @return 是否为本地路径
     */
    public boolean isLocal(String path) {
        return PathTypeDetector.isLocal(path);
    }
}
