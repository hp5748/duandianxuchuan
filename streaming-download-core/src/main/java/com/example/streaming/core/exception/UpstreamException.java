package com.example.streaming.core.exception;

/**
 * 上游服务器错误异常
 * HTTP 502
 */
public class UpstreamException extends DownloadException {

    public UpstreamException(String message) {
        super(message, 502);
    }

    public UpstreamException(String message, Throwable cause) {
        super(message, 502, cause);
    }

    public UpstreamException(int upstreamStatusCode, String url) {
        super(String.format("上游服务器返回错误状态码 %d, URL: %s", upstreamStatusCode, url), 502);
    }
}
