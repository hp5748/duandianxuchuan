package com.example.streaming.core.exception;

/**
 * URL 格式非法异常
 * HTTP 400
 */
public class InvalidUrlException extends DownloadException {

    public InvalidUrlException(String message) {
        super(message, 400);
    }

    public InvalidUrlException(String message, Throwable cause) {
        super(message, 400, cause);
    }
}
