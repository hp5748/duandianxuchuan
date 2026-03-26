package com.example.streaming.autoconfig;

import com.example.streaming.core.LocalFileDownloader;
import com.example.streaming.core.StreamingDownloader;
import com.example.streaming.core.UnifiedDownloader;
import com.example.streaming.core.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 流式下载自动配置
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass(StreamingDownloader.class)
@ConditionalOnProperty(prefix = "streaming.download", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StreamingDownloadProperties.class)
public class StreamingDownloadAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(StreamingDownloadAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public UrlValidator urlValidator(StreamingDownloadProperties properties) {
        UrlValidator validator = new UrlValidator(
            properties.getAllowedDomains(),
            properties.isAllowAllDomains()
        );

        if (properties.isAllowAllDomains()) {
            logger.warn("已启用 allowAllDomains 模式，所有域名都将被允许访问。此模式仅建议用于测试环境！");
        } else if (properties.getAllowedDomains().isEmpty()) {
            logger.warn("未配置域名白名单 (allowed-domains)，所有请求都将被拒绝。" +
                "请配置 streaming.download.allowed-domains 或设置 streaming.download.allow-all-domains=true");
        }

        return validator;
    }

    @Bean
    @ConditionalOnMissingBean
    public StreamingDownloader streamingDownloader(StreamingDownloadProperties properties) {
        logger.info("初始化 StreamingDownloader: bufferSize={}, connectTimeout={}ms, readTimeout={}ms, maxFileSize={}",
            properties.getBufferSize(),
            properties.getConnectTimeout(),
            properties.getReadTimeout(),
            properties.getMaxFileSize() > 0 ? properties.getMaxFileSize() + " bytes" : "unlimited");

        return new StreamingDownloader(
            properties.getBufferSize(),
            properties.getConnectTimeout(),
            properties.getReadTimeout(),
            properties.getMaxFileSize()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalFileDownloader localFileDownloader(StreamingDownloadProperties properties) {
        logger.info("初始化 LocalFileDownloader: bufferSize={}, maxFileSize={}",
            properties.getBufferSize(),
            properties.getMaxFileSize() > 0 ? properties.getMaxFileSize() + " bytes" : "unlimited");

        return new LocalFileDownloader(
            properties.getBufferSize(),
            properties.getMaxFileSize()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public UnifiedDownloader unifiedDownloader(StreamingDownloader remoteDownloader, LocalFileDownloader localDownloader) {
        logger.info("初始化 UnifiedDownloader");
        return new UnifiedDownloader(remoteDownloader, localDownloader);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProxyDownloadController proxyDownloadController(UnifiedDownloader downloader, UrlValidator validator) {
        return new ProxyDownloadController(downloader, validator);
    }
}
