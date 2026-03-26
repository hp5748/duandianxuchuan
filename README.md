# Streaming Download Spring Boot Starter

支持大文件流式代理下载和断点续传的 Spring Boot Starter 组件。

## 特性

- **O(1) 内存占用**：使用固定 buffer（默认 8KB）流式传输，无论下载多大的文件，内存消耗保持常数级
- **断点续传**：支持 HTTP Range 请求，下载中断后可从断点继续
- **实时进度反馈**：前端支持实时显示进度百分比、下载速率、已下载/总量
- **多路径类型支持**：支持远程 URL、本地文件路径（Windows/Linux）、UNC 网络路径
- **开箱即用**：Spring Boot Starter 封装，引入依赖即可使用
- **安全防护**：支持域名白名单、SSRF 防护（仅远程 URL）

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>streaming-download-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置（可选）

```yaml
streaming:
  download:
    enabled: true
    buffer-size: 8192        # 缓冲区大小（字节）
    connect-timeout: 10000   # 连接超时（毫秒）
    read-timeout: 60000      # 读取超时（毫秒）
    max-file-size: -1        # 最大文件大小（-1 不限制）

    # 安全配置
    allowed-domains:         # 域名白名单
      - example.com
      - cdn.example.org
    allow-all-domains: false # 是否允许所有域名（危险，仅用于测试）
```

### 3. 启动应用

启动 Spring Boot 应用后，访问测试页面：

```
http://localhost:8080/download.html
```

## API 接口

### 下载接口

```
GET /api/proxy/download?url={targetUrl}
```

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| url | String | 是 | 目标文件路径，支持三种类型：<br>- 远程 URL：`https://example.com/file.zip`<br>- 本地路径：`C:\Users\Admin\Desktop\file.exe` 或 `/home/user/file.zip`<br>- UNC 路径：`\\server\share\file.txt` |

> **注意**：本地路径和 UNC 路径不执行域名白名单和 SSRF 防护校验。

### 请求头

| Header | 说明 | 示例 |
|--------|------|------|
| Range | 断点续传范围 | bytes=1024- |

### 响应头

| Header | 说明 |
|--------|------|
| Content-Length | 响应体大小 |
| Content-Type | 文件 MIME 类型 |
| Accept-Ranges | bytes（支持断点续传） |
| Content-Range | 断点续传范围（206 响应） |
| Content-Disposition | 文件名 |

### 响应状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 完整下载 |
| 206 | 断点续传 |
| 400 | URL/路径格式非法 |
| 403 | 域名不在白名单（仅远程 URL） |
| 404 | 本地文件不存在 |
| 413 | 文件过大 |
| 416 | Range 无效 |
| 502 | 上游服务器错误（仅远程 URL） |

## 前端集成指南

### 核心模块

| 模块 | 说明 |
|------|------|
| `SpeedCalculator` | 速率计算器（滑动窗口算法） |
| `DownloadManager` | 下载管理器（核心下载逻辑） |
| `DownloadHistory` | 历史记录管理器（localStorage 存储） |

### 集成方式

**方式一：直接使用自带页面**

访问 `/download.html` 即可使用完整功能。

**方式二：复制 JS 模块到自己的项目**

1. 从 `download.html` 中提取以下类：
   - `SpeedCalculator`
   - `DownloadManager`
   - `DownloadHistory`

2. 根据需要修改 API 端点：
   ```javascript
   // 在 DownloadManager 构造函数中修改
   this.apiBase = '/your-custom-path/api/proxy';
   ```

### 核心概念

前端通过 Fetch API 的 ReadableStream 实现流式读取，边接收边写入本地文件，同时实时计算进度、速度和状态。

### 1. 获取文件总大小

从响应头 Content-Length 获取（断点续传时需要结合 Content-Range 计算）：

```javascript
const response = await fetch('/api/proxy/download?url=' + encodeURIComponent(url));
const contentLength = parseInt(response.headers.get('content-length'));

// 断点续传时，从 Content-Range 获取完整文件大小
const contentRange = response.headers.get('content-range');
if (contentRange) {
    // Content-Range: bytes 0-1023/5000
    const totalSize = parseInt(contentRange.split('/')[1]);
}
```

### 2. 计算下载进度

维护已下载字节数，实时计算百分比：

```javascript
let loadedBytes = 0;
const totalBytes = contentLength;

// 在读取循环中累加
const reader = response.body.getReader();
while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    loadedBytes += value.length;
    const progress = (loadedBytes / totalBytes * 100).toFixed(2);
    console.log(`进度: ${progress}%`);
}
```

### 3. 计算下载速度

使用时间窗口计算实时速度：

```javascript
class SpeedCalculator {
    constructor(windowSize = 5) {
        this.samples = [];  // [{time, loaded}]
        this.windowSize = windowSize;
    }

    addSample(loaded) {
        const now = Date.now();
        this.samples.push({ time: now, loaded });
        // 移除窗口外的样本
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
        return bytesDiff / timeDiff;
    }

    format(speed) {
        if (speed > 1024 * 1024) return (speed / 1024 / 1024).toFixed(2) + ' MB/s';
        if (speed > 1024) return (speed / 1024).toFixed(2) + ' KB/s';
        return speed.toFixed(0) + ' B/s';
    }
}

// 使用
const speedCalc = new SpeedCalculator(5);
// 在读取循环中
speedCalc.addSample(loadedBytes);
console.log(`速度: ${speedCalc.format(speedCalc.getSpeed())}`);
```

### 4. 管理下载状态

```javascript
const DownloadStatus = {
    IDLE: 'idle',           // 空闲
    CONNECTING: 'connecting', // 连接中
    DOWNLOADING: 'downloading', // 下载中
    PAUSED: 'paused',       // 已暂停
    COMPLETED: 'completed', // 已完成
    ERROR: 'error'          // 出错
};

let status = DownloadStatus.IDLE;
let loadedBytes = 0;  // 用于断点续传

// 断点续传：暂停时保留 loadedBytes
function pause() {
    status = DownloadStatus.PAUSED;
    // reader.cancel() 中断读取
}

// 继续下载
function resume(url) {
    const headers = {};
    if (loadedBytes > 0) {
        headers['Range'] = `bytes=${loadedBytes}-`;
    }
    // 重新发起请求...
}
```

### 5. 完整示例

```javascript
const downloader = {
    status: 'idle',
    loadedBytes: 0,
    totalBytes: 0,
    fileHandle: null,
    speedCalc: new SpeedCalculator(5),

    async download(url, resume = false) {
        this.status = 'connecting';

        const headers = resume && this.loadedBytes > 0
            ? { 'Range': `bytes=${this.loadedBytes}-` }
            : {};

        const response = await fetch('/api/proxy/download?url=' + encodeURIComponent(url), {
            headers
        });

        this.totalBytes = parseInt(response.headers.get('content-length'));
        if (resume) {
            const range = response.headers.get('content-range');
            if (range) this.totalBytes = parseInt(range.split('/')[1]);
        }

        this.status = 'downloading';
        const reader = response.body.getReader();
        const writer = await this.fileHandle.createWritable({
            keepExistingData: resume
        });

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            await writer.write(value);
            this.loadedBytes += value.length;
            this.speedCalc.addSample(this.loadedBytes);

            // 更新 UI
            this.onProgress({
                progress: (this.loadedBytes / this.totalBytes * 100).toFixed(2),
                speed: this.speedCalc.format(this.speedCalc.getSpeed()),
                loaded: this.formatBytes(this.loadedBytes),
                total: this.formatBytes(this.totalBytes)
            });
        }

        await writer.close();
        this.status = 'completed';
    }
};
```

### 6. 浏览器兼容性

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

### 7. 历史记录模块

#### 数据结构

```javascript
// localStorage Key: 'download_history'
{
  id: 'string',           // 唯一标识
  url: 'string',          // 下载地址
  fileName: 'string',     // 文件名
  totalBytes: number,     // 总大小（字节）
  loadedBytes: number,    // 已下载大小（字节）
  status: 'downloading' | 'paused' | 'completed' | 'error' | 'cancelled',
  startTime: number,      // 开始时间（Unix 时间戳）
  updateTime: number      // 更新时间（Unix 时间戳）
}
```

#### API

```javascript
const history = new DownloadHistory();

// 添加记录
const record = history.add({
  url: 'https://example.com/file.zip',
  fileName: 'file.zip',
  totalBytes: 1024000,
  loadedBytes: 0,
  status: 'downloading'
});

// 获取所有记录
const records = history.list();

// 获取单条记录
const record = history.get(recordId);

// 更新记录
history.update(recordId, {
  loadedBytes: 512000,
  status: 'paused'
});

// 删除记录
history.remove(recordId);

// 清空所有记录
history.clear();
```

## 环境要求

- **Java 8**：`JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202`
- **Maven 3.9.9**：已配置全局环境变量 `MAVEN_HOME=C:\tools\apache-maven-3.9.9`
  - 本地仓库：`C:\tools\maven-repo`
  - 镜像源：阿里云镜像（加速下载）

## 构建和运行

### 方式一：一键启动（推荐）

```bash
# 启动服务
script\start.bat

# 或使用 quick-start.bat（首次会检查环境）
script\quick-start.bat
```

### 方式二：手动构建

```bash
# 构建
mvn clean install -DskipTests

# 运行 Demo
cd streaming-download-demo
mvn spring-boot:run
```

### 验证

启动后访问 http://localhost:8080/download.html 进行测试。

## 构建和发布

### 构建 Starter

```bash
# 一键构建（输出到 dist 目录 + 安装到本地仓库）
script\build-starter.bat
```

构建完成后：
- `dist/` 目录包含 JAR 文件
- 已安装到本地 Maven 仓库

### 在其他项目中使用

**方式一：本地仓库引用**

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>streaming-download-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**方式二：手动引入 JAR**

1. 将 `dist/` 中的 JAR 复制到项目的 `libs/` 目录
2. 在 pom.xml 中添加：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>streaming-download-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/streaming-download-spring-boot-starter-1.0.0-SNAPSHOT.jar</systemPath>
</dependency>
<dependency>
    <groupId>com.example</groupId>
    <artifactId>streaming-download-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/libs/streaming-download-core-1.0.0-SNAPSHOT.jar</systemPath>
</dependency>
```

## 安全注意事项

1. **域名白名单**：生产环境务必配置 `allowed-domains`，不要使用 `allow-all-domains: true`
2. **SSRF 防护**：组件已内置内网 IP 检测，禁止访问 `10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16`、`127.0.0.0/8` 等私有网络
3. **文件大小限制**：建议配置 `max-file-size` 限制最大下载文件大小

## 项目结构

```
streaming-download/
├── streaming-download-core/              # 核心模块（无 Spring 依赖）
│   └── src/main/java/.../core/
│       ├── RangeRequest.java            # Range 请求解析
│       ├── RangeResponse.java           # Range 响应封装
│       ├── FileInfo.java                # 文件信息
│       ├── StreamingDownloader.java     # 远程文件下载器
│       ├── LocalFileDownloader.java     # 本地文件下载器
│       ├── UnifiedDownloader.java       # 统一下载器（自动路由）
│       ├── PathTypeDetector.java        # 路径类型检测
│       ├── UrlValidator.java            # URL 安全校验
│       └── exception/                   # 异常定义
│
├── streaming-download-spring-boot-starter/  # Starter 模块
│   └── src/main/
│       ├── java/.../autoconfig/
│       │   ├── StreamingDownloadAutoConfiguration.java
│       │   ├── StreamingDownloadProperties.java
│       │   └── ProxyDownloadController.java
│       └── resources/
│           ├── META-INF/spring.factories
│           └── static/download.html
│
├── streaming-download-demo/             # 演示应用
│   └── src/main/
│       ├── java/.../demo/DemoApplication.java
│       └── resources/application.yml
│
├── script/                              # 启动脚本
│   ├── start.bat                        # 一键启动脚本
│   └── quick-start.bat                  # 快速启动脚本
│
├── spec/                                # 规范文档
│   ├── Me2AI/                          # 需求和技术约束
│   └── AI2AI/                          # 代码状态总结（由 AI 维护）
│
├── docs/                                # 设计文档
│   └── superpowers/specs/
│
├── README.md
└── pom.xml
```

## License

MIT
