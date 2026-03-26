package com.example.streaming.core.exception;

/**
 * 文件过大异常
 * HTTP 413
 */
public class FileTooLargeException extends DownloadException {

    public FileTooLargeException(String message) {
        super(message, 413);
    }

    public FileTooLargeException(long fileSize, long maxSize) {
        super(String.format("文件大小 %d 字节超过最大限制 %d 字节", fileSize, maxSize), 413);
    }
}
