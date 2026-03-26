package com.example.streaming.core.exception;

/**
 * 本地文件未找到异常
 * 当本地文件不存在时抛出（HTTP 状态码 404）
 */
public class LocalFileNotFoundException extends DownloadException {

    private final String path;

    public LocalFileNotFoundException(String path) {
        super("文件不存在: " + path, 404);
        this.path = path;
    }

    public LocalFileNotFoundException(String path, Throwable cause) {
        super("文件不存在: " + path, 404, cause);
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
