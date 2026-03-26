package com.example.streaming.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式下载配置属性
 */
@ConfigurationProperties(prefix = "streaming.download")
public class StreamingDownloadProperties {

    /**
     * 是否启用流式下载功能
     */
    private boolean enabled = true;

    /**
     * 缓冲区大小（字节），默认 8KB
     */
    private int bufferSize = 8192;

    /**
     * 连接超时（毫秒），默认 10s
     */
    private int connectTimeout = 10000;

    /**
     * 读取超时（毫秒），默认 60s
     */
    private int readTimeout = 60000;

    /**
     * 最大文件大小（字节），-1 表示不限制
     */
    private long maxFileSize = -1;

    /**
     * 域名白名单
     */
    private List<String> allowedDomains = new ArrayList<>();

    /**
     * 是否允许所有域名（危险，仅用于测试）
     */
    private boolean allowAllDomains = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
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
