package com.example.streaming.core;

import com.example.streaming.core.exception.DomainNotAllowedException;
import com.example.streaming.core.exception.InvalidUrlException;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * URL 安全校验器
 * 1. 校验协议必须为 http 或 https
 * 2. 校验域名必须在白名单内
 * 3. 禁止访问内网 IP（SSRF 防护）
 */
public class UrlValidator {

    private List<String> allowedDomains;
    private boolean allowAllDomains;

    public UrlValidator() {
        this.allowedDomains = new ArrayList<>();
        this.allowAllDomains = false;
    }

    public UrlValidator(List<String> allowedDomains, boolean allowAllDomains) {
        this.allowedDomains = allowedDomains != null ? new ArrayList<>(allowedDomains) : new ArrayList<>();
        this.allowAllDomains = allowAllDomains;
    }

    /**
     * 校验 URL 安全性
     *
     * @param url 要校验的 URL
     * @throws InvalidUrlException        URL 格式非法
     * @throws DomainNotAllowedException  域名不在白名单
     */
    public void validate(String url) throws InvalidUrlException, DomainNotAllowedException {
        if (url == null || url.trim().isEmpty()) {
            throw new InvalidUrlException("URL 不能为空");
        }

        URL parsedUrl;
        try {
            parsedUrl = new URL(url);
        } catch (MalformedURLException e) {
            throw new InvalidUrlException("URL 格式无效，请输入完整的网址（如：https://example.com/file.zip）", e);
        }

        // 1. 校验协议
        String protocol = parsedUrl.getProtocol().toLowerCase();
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new InvalidUrlException("仅支持 HTTP 和 HTTPS 协议，不支持本地文件路径或其他协议");
        }

        String host = parsedUrl.getHost();
        if (host == null || host.isEmpty()) {
            throw new InvalidUrlException("URL 缺少主机名");
        }

        // 2. 校验域名白名单
        if (!allowAllDomains) {
            if (allowedDomains == null || allowedDomains.isEmpty()) {
                throw new DomainNotAllowedException("未配置域名白名单，请配置 streaming.download.allowed-domains 或设置 streaming.download.allow-all-domains=true");
            }

            if (!isDomainAllowed(host)) {
                throw new DomainNotAllowedException(host);
            }
        }

        // 3. SSRF 防护：禁止访问内网 IP
        if (isPrivateIp(host)) {
            throw new InvalidUrlException("禁止访问内网地址: " + host);
        }
    }

    /**
     * 检查域名是否在白名单中
     */
    private boolean isDomainAllowed(String host) {
        String lowerHost = host.toLowerCase();
        for (String allowed : allowedDomains) {
            String lowerAllowed = allowed.toLowerCase();
            // 支持通配符，如 *.example.com
            if (lowerAllowed.startsWith("*.")) {
                String domainSuffix = lowerAllowed.substring(2);
                if (lowerHost.endsWith(domainSuffix) || lowerHost.equals(domainSuffix.substring(1))) {
                    return true;
                }
            } else if (lowerHost.equals(lowerAllowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否为内网 IP
     * - 10.0.0.0/8
     * - 172.16.0.0/12
     * - 192.168.0.0/16
     * - 127.0.0.0/8
     * - 169.254.0.0/16 (链路本地)
     * - ::1 (IPv6 本地)
     */
    private boolean isPrivateIp(String host) {
        try {
            // 先尝试解析为 IP 地址
            InetAddress address = InetAddress.getByName(host);
            byte[] bytes = address.getAddress();

            // IPv4
            if (bytes.length == 4) {
                int b0 = bytes[0] & 0xFF;
                int b1 = bytes[1] & 0xFF;

                // 10.0.0.0/8
                if (b0 == 10) return true;

                // 172.16.0.0/12
                if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;

                // 192.168.0.0/16
                if (b0 == 192 && b1 == 168) return true;

                // 127.0.0.0/8
                if (b0 == 127) return true;

                // 169.254.0.0/16 (链路本地)
                if (b0 == 169 && b1 == 254) return true;

                // 0.0.0.0/8
                if (b0 == 0) return true;

                // 224.0.0.0/4 (组播)
                if (b0 >= 224 && b0 <= 239) return true;

                // 240.0.0.0/4 (保留)
                if (b0 >= 240) return true;
            }

            // IPv6
            if (bytes.length == 16) {
                // ::1 本地回环
                if (address.isLoopbackAddress()) return true;

                // 链路本地 fe80::/10
                if (bytes[0] == (byte) 0xfe && (bytes[1] & 0xC0) == 0x80) return true;

                // 唯一本地地址 fc00::/7
                if ((bytes[0] & 0xFE) == 0xFC) return true;
            }

        } catch (Exception e) {
            // 解析失败，可能是域名而非 IP，继续处理
        }

        return false;
    }

    public List<String> getAllowedDomains() {
        return allowedDomains;
    }

    public void setAllowedDomains(List<String> allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    public boolean isAllowAllDomains() {
        return allowAllDomains;
    }

    public void setAllowAllDomains(boolean allowAllDomains) {
        this.allowAllDomains = allowAllDomains;
    }
}
