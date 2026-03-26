# 流式代理下载服务设计文档

## 1. 概述

### 1.1 项目背景
开发一个支持大文件透传下载的 Web 服务组件，核心目标是严格控制前后端内存占用，无论下载多大的文件，内存消耗必须保持在常数级（O(1)）。

### 1.2 核心特性
- **流式代理下载**：固定 buffer 流式传输，零内存缓存
- **断点续传**：支持 HTTP Range 请求
- **实时反馈**：进度百分比、下载速率
- **Starter 封装**：开箱即用

### 1.3 技术选型
| 层级 | 技术 | 版本 |
|------|------|------|
| JDK | Oracle/OpenJDK | 8+ |
| 后端框架 | Spring Boot | 2.7.6 |
| 前端 | 原生 HTML/JS/CSS | - |
| 浏览器 API | File System Access API | Chrome 86+ |

## 2. 架构设计

### 2.1 模块结构
```
streaming-download/
├── streaming-download-core/              # 核心模块（无 Spring 依赖）
│   ├── src/main/java/com/example/streaming/core/
│   │   ├── RangeRequest.java           # Range 请求解析
│   │   ├── RangeResponse.java          # Range 响应封装
│   │   ├── FileInfo.java               # 文件信息封装
│   │   ├── StreamingDownloader.java    # 核心下载器
│   │   ├── UrlValidator.java           # URL 安全校验
│   │   └── exception/
│   │       ├── DownloadException.java
│   │       ├── InvalidUrlException.java
│   │       ├── DomainNotAllowedException.java
│   │       ├── FileTooLargeException.java
│   │       └── RangeNotSatisfiableException.java
│   └── pom.xml
│
├── streaming-download-spring-boot-starter/  # Starter 模块
│   ├── src/main/java/com/example/streaming/autoconfig/
│   │   ├── StreamingDownloadAutoConfiguration.java
│   │   ├── StreamingDownloadProperties.java
│   │   └── ProxyDownloadController.java
│   ├── src/main/resources/
│   │   ├── META-INF/spring.factories
│   │   └── static/download.html
│   └── pom.xml
│
├── streaming-download-demo/             # 演示应用
│   ├── src/main/java/.../DemoApplication.java
│   └── pom.xml
│
├── README.md
└── pom.xml
```

### 2.2 数据流
```
前端 → Controller → StreamingDownloader → 目标服务器
                ↓
           HttpServletResponse (流式输出)
```

## 3. 核心模块设计

### 3.1 Range 请求处理

#### RangeRequest.java
```java
public class RangeRequest {
    private long start;        // 起始字节
    private long end;          // 结束字节（-1 表示到文件末尾）
    private long totalLength;  // 文件总大小

    /**
     * 解析 Range 请求头
     * @param rangeHeader "bytes=0-1023" 格式
     * @param totalLength 文件总大小
     */
    public static RangeRequest parse(String rangeHeader, long totalLength);

    /** 获取实际读取长度 */
    public long getContentLength();

    /** 生成 Content-Range 响应头 "bytes 0-1023/5000" */
    public String toContentRange();
}
```

#### RangeResponse.java
```java
public class RangeResponse {
    private boolean partial;     // 是否部分内容
    private int statusCode;      // 200 或 206
    private long contentLength;  // 响应体长度
    private String contentRange; // Content-Range 头
}
```

### 3.2 文件信息封装

#### FileInfo.java
```java
public class FileInfo {
    private long contentLength;     // 文件大小
    private String contentType;     // MIME 类型
    private boolean acceptRanges;   // 是否支持断点续传
    private String fileName;        // 文件名（从 URL 或 Content-Disposition 解析）

    // getters and setters
}
```

### 3.3 核心下载器

#### StreamingDownloader.java
```java
public class StreamingDownloader {
    private int bufferSize;
    private int connectTimeout;
    private int readTimeout;

    /**
     * 获取目标文件信息
     * @return FileInfo 包含 Content-Length、Content-Type
     */
    public FileInfo getFileInfo(String url);

    /**
     * 流式下载，支持 Range 请求
     * @param url 目标地址
     * @param range Range 请求（null 表示完整下载）
     * @param outputStream 响应输出流
     */
    public void download(String url, RangeRequest range,
                         OutputStream outputStream) throws IOException;
}
```

### 3.4 关键实现约束
- 使用固定 8KB buffer 进行流拷贝
- 透传 Content-Length、Content-Type
- 支持 Range 请求（断点续传核心）
- 不缓存任何数据到内存
- 使用 try-with-resources 确保流正确关闭

### 3.5 安全校验

#### UrlValidator.java
```java
public class UrlValidator {
    private List<String> allowedDomains;   // 域名白名单
    private boolean allowAllDomains;       // 是否允许所有域名（默认 false）

    /**
     * 校验 URL 安全性
     * 1. 协议必须为 http 或 https
     * 2. 域名必须在白名单内
     * 3. 禁止访问内网 IP（SSRF 防护）
     */
    public void validate(String url) throws InvalidUrlException, DomainNotAllowedException;

    /**
     * 检查是否为内网 IP
     * - 10.0.0.0/8
     * - 172.16.0.0/12
     * - 192.168.0.0/16
     * - 127.0.0.0/8
     */
    private boolean isPrivateIp(String host);
}
```

### 3.6 异常处理

| 异常类型 | HTTP 状态码 | 说明 |
|----------|-------------|------|
| InvalidUrlException | 400 | URL 格式非法 |
| DomainNotAllowedException | 403 | 域名不在白名单 |
| FileTooLargeException | 413 | 超过最大文件限制 |
| RangeNotSatisfiableException | 416 | Range 超出范围 |
| UpstreamException | 502 | 上游服务器错误 |

#### 异常响应格式
```json
{
  "code": 400,
  "message": "URL 格式非法",
  "timestamp": "2026-03-25T10:30:00Z"
}
```

## 4. Starter 模块设计

### 4.1 自动配置

#### StreamingDownloadProperties.java
```java
@ConfigurationProperties(prefix = "streaming.download")
public class StreamingDownloadProperties {
    private int bufferSize = 8192;        // 缓冲区大小，默认 8KB
    private int connectTimeout = 10000;   // 连接超时，默认 10s
    private int readTimeout = 60000;      // 读取超时，默认 60s
    private long maxFileSize = -1;        // 最大文件大小，-1 不限制
    private boolean enabled = true;       // 是否启用

    // 安全配置
    private List<String> allowedDomains = new ArrayList<>();  // 域名白名单
    private boolean allowAllDomains = false;  // 是否允许所有域名（危险，仅用于测试）
}
```

#### StreamingDownloadAutoConfiguration.java
```java
@Configuration
@ConditionalOnClass(StreamingDownloader.class)
@EnableConfigurationProperties(StreamingDownloadProperties.class)
public class StreamingDownloadAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StreamingDownloader streamingDownloader(StreamingDownloadProperties props);

    @Bean
    @ConditionalOnMissingBean
    public ProxyDownloadController proxyDownloadController();
}
```

### 4.2 控制器设计

#### ProxyDownloadController.java
```java
@RestController
@RequestMapping("/api/proxy")
public class ProxyDownloadController {

    /**
     * 流式代理下载（支持断点续传）
     * GET /api/proxy/download?url=xxx
     *
     * Request Headers:
     *   Range: bytes=0-1023 (可选，断点续传)
     *
     * Response Headers:
     *   Content-Length: 文件大小
     *   Content-Type: MIME 类型
     *   Content-Range: bytes 0-1023/5000 (部分内容时)
     *   Accept-Ranges: bytes
     */
    @GetMapping("/download")
    public void download(
        @RequestParam String url,
        @RequestHeader(value = "Range", required = false) String rangeHeader,
        HttpServletResponse response
    );
}
```

### 4.3 接口行为
| 场景 | 请求 | 响应码 | 说明 |
|------|------|--------|------|
| 完整下载 | 无 Range 头 | 200 | 返回完整文件 |
| 断点续传 | Range: bytes=1000- | 206 | 从第 1000 字节开始 |
| 无效 Range | Range: bytes=9999- | 416 | Range Not Satisfiable |

## 5. 前端设计

### 5.1 页面布局
```
┌─────────────────────────────────────────────────────────┐
│  流式代理下载 - 断点续传 Demo                            │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  下载地址: [____________________________] [选择文件]    │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │████████████████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░│   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  进度: 45.5%          速率: 2.4 MB/s                    │
│  已下载: 120 MB / 500 MB                                │
│                                                         │
│  [开始下载]  [暂停]  [继续下载]  [取消]                 │
│                                                         │
│  状态: 下载中...                                        │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 5.2 核心类设计

#### StreamingDownloader 前端类
```javascript
class StreamingDownloader {
    constructor(apiBase) {
        this.apiBase = apiBase;
        this.fileHandle = null;
        this.loadedBytes = 0;
        this.totalBytes = 0;
        this.isPaused = false;
    }

    /** 选择保存位置 */
    async selectFile(filename) {
        this.fileHandle = await window.showSaveFilePicker({
            suggestedName: filename
        });
    }

    /** 开始/继续下载 */
    async download(url, resume = false) {
        const headers = {};
        if (resume && this.loadedBytes > 0) {
            headers['Range'] = `bytes=${this.loadedBytes}-`;
        }

        const response = await fetch(`${this.apiBase}/download?url=${url}`, { headers });

        // 获取文件总大小
        this.totalBytes = parseInt(response.headers.get('content-length'));
        if (resume) {
            this.totalBytes += this.loadedBytes;
        }

        // 流式写入
        const writer = await this.fileHandle.createWritable({ keepExistingData: resume });
        if (resume) await writer.seek(this.loadedBytes);

        const reader = response.body.getReader();
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            await writer.write(value);
            this.loadedBytes += value.length;
            this.updateProgress();
        }
        await writer.close();
    }

    pause() { this.isPaused = true; }
    resume(url) { return this.download(url, true); }
}
```

#### SpeedCalculator 速率计算
```javascript
class SpeedCalculator {
    constructor(windowSize = 5) {
        this.samples = [];  // [{time, loaded}] 存储累计下载量
        this.windowSize = windowSize;
    }

    /**
     * 添加采样点
     * @param loaded 当前累计已下载字节数
     */
    addSample(loaded) {
        this.samples.push({ time: Date.now(), loaded });
        if (this.samples.length > this.windowSize) {
            this.samples.shift();
        }
    }

    getSpeed() {
        if (this.samples.length < 2) return 0;
        const first = this.samples[0];
        const last = this.samples[this.samples.length - 1];
        const timeDiff = (last.time - first.time) / 1000;
        const bytesDiff = last.loaded - first.loaded;
        return bytesDiff / timeDiff; // bytes/s
    }

    formatSpeed(speed) {
        if (speed > 1024 * 1024) return (speed / 1024 / 1024).toFixed(2) + ' MB/s';
        if (speed > 1024) return (speed / 1024).toFixed(2) + ' KB/s';
        return speed.toFixed(0) + ' B/s';
    }
}
```

### 5.3 关键特性
- 使用 File System Access API 实现真正的断点续传
- 暂停时保留 `loadedBytes`，继续时发送 Range 请求
- 滑动窗口计算实时速率，避免抖动

### 5.4 浏览器兼容性

#### 支持情况
| 浏览器 | 版本 | 支持程度 |
|--------|------|----------|
| Chrome | 86+ | 完整支持（断点续传） |
| Edge | 86+ | 完整支持（断点续传） |
| Firefox | - | 仅基础下载（无断点续传） |
| Safari | - | 仅基础下载（无断点续传） |

#### 降级方案
```javascript
async function download(url) {
    // 检测浏览器是否支持 File System Access API
    if (!window.showSaveFilePicker) {
        // 降级为传统下载方式
        console.warn('浏览器不支持 File System Access API，使用传统下载');
        const link = document.createElement('a');
        link.href = `/api/proxy/download?url=${encodeURIComponent(url)}`;
        link.click();
        return;
    }

    // 使用完整的流式下载（支持断点续传）
    await streamingDownload(url);
}
```

## 6. 配置说明

```yaml
streaming:
  download:
    # 基础配置
    buffer-size: 8192        # 缓冲区大小（字节）
    connect-timeout: 10000   # 连接超时（毫秒）
    read-timeout: 60000      # 读取超时（毫秒）
    max-file-size: -1        # 最大文件大小（-1 不限制）
    enabled: true            # 是否启用

    # 安全配置
    allowed-domains:         # 域名白名单
      - example.com
      - cdn.example.org
    allow-all-domains: false # 是否允许所有域名（危险，仅用于测试）
```

## 7. 测试策略

### 7.1 单元测试
- RangeRequest 解析测试
- RangeResponse 生成测试
- StreamingDownloader 核心逻辑测试

### 7.2 集成测试
- 完整下载流程测试
- 断点续传流程测试
- 异常场景测试（超时、无效 Range 等）

### 7.3 性能测试
- 大文件下载内存占用测试
- 并发下载测试
