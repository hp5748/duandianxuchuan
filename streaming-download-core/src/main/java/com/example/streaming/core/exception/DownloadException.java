package com.example.streaming.core.exception;

/**
 * 下载异常基类
 */
public class DownloadException extends Exception {

    private final int statusCode;

    public DownloadException(String message) {
        super(message);
        this.statusCode = 500;
    }

    public DownloadException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public DownloadException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
    }

    public DownloadException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
