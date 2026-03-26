package com.example.streaming.core.exception;

/**
 * 域名不在白名单异常
 * HTTP 403
 */
public class DomainNotAllowedException extends DownloadException {

    public DomainNotAllowedException(String domain) {
        super("域名不在白名单中: " + domain, 403);
    }
}
