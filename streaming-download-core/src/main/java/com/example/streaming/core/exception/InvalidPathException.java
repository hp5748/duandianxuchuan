package com.example.streaming.core.exception;

/**
 * 无效路径异常
 * 当路径格式非法时抛出（HTTP 状态码 400）
 */
public class InvalidPathException extends DownloadException {

    public InvalidPathException(String message) {
        super(message, 400);
    }

    public InvalidPathException(String message, Throwable cause) {
        super(message, 400, cause);
    }
}
