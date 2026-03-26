# 历史记录与打包优化 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 streaming-download-spring-boot-starter 新增历史记录管理、进度恢复、打包脚本和完善的接口文档。

**Architecture:** 纯前端改造（localStorage 存储），后端无改动。新增 DownloadHistory 类管理历史数据，扩展 UI 添加历史记录面板。

**Tech Stack:** JavaScript (ES6+), localStorage, HTML/CSS, Windows Batch Script

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `streaming-download-spring-boot-starter/src/main/resources/static/download.html` | 修改 | 新增 DownloadHistory 类、历史记录 UI 面板、集成到 DownloadManager |
| `script/build-starter.bat` | 新增 | 打包 core 和 starter 到本地仓库及 dist 目录 |
| `README.md` | 修改 | 完善后端接口文档 + 前端模块使用说明 |

---

## Task 1: 新增 DownloadHistory 类

**Files:**
- Modify: `streaming-download-spring-boot-starter/src/main/resources/static/download.html`

- [ ] **Step 1: 在 `<script>` 标签内添加 DownloadHistory 类**

在 `SpeedCalculator` 类之后、`DownloadStatus` 枚举之前添加：

```javascript
        // 历史记录管理器
        class DownloadHistory {
            constructor() {
                this.storageKey = 'download_history';
            }

            // 获取所有记录
            list() {
                const data = localStorage.getItem(this.storageKey);
                return data ? JSON.parse(data) : [];
            }

            // 添加记录
            add(record) {
                const records = this.list();
                const newRecord = {
                    id: this.generateId(),
                    url: record.url,
                    fileName: record.fileName,
                    totalBytes: record.totalBytes || 0,
                    loadedBytes: record.loadedBytes || 0,
                    status: record.status || 'downloading',
                    startTime: Date.now(),
                    updateTime: Date.now()
                };
                records.unshift(newRecord);
                this.save(records);
                return newRecord;
            }

            // 更新记录
            update(id, data) {
                const records = this.list();
                const index = records.findIndex(r => r.id === id);
                if (index >= 0) {
                    records[index] = { ...records[index], ...data, updateTime: Date.now() };
                    this.save(records);
                    return records[index];
                }
                return null;
            }

            // 删除记录
            remove(id) {
                const records = this.list();
                const filtered = records.filter(r => r.id !== id);
                this.save(filtered);
            }

            // 清空所有记录
            clear() {
                localStorage.removeItem(this.storageKey);
            }

            // 获取单条记录
            get(id) {
                const records = this.list();
                return records.find(r => r.id === id);
            }

            // 保存到 localStorage
            save(records) {
                localStorage.setItem(this.storageKey, JSON.stringify(records));
            }

            // 生成唯一 ID
            generateId() {
                return Date.now().toString(36) + Math.random().toString(36).substr(2);
            }
        }
```

- [ ] **Step 2: 验证语法正确**

在浏览器控制台测试：
```javascript
const h = new DownloadHistory();
h.add({url: 'test', fileName: 'test.zip'});
console.log(h.list());
```

预期：控制台输出包含新增记录的数组

---

## Task 2: 添加历史记录 UI 样式

**Files:**
- Modify: `streaming-download-spring-boot-starter/src/main/resources/static/download.html`

- [ ] **Step 1: 在 `<style>` 标签内添加历史记录面板样式**

在 `.browser-warning.show` 样式之后添加：

```css
        .history-section {
            margin-top: 30px;
            border-top: 1px solid #e0e0e0;
            padding-top: 20px;
        }

        .history-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 15px;
        }

        .history-header h2 {
            font-size: 16px;
            color: #333;
            margin: 0;
        }

        .history-list {
            max-height: 300px;
            overflow-y: auto;
        }

        .history-item {
            background: #f8f9fa;
            border-radius: 8px;
            padding: 15px;
            margin-bottom: 10px;
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        .history-item-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .history-item-name {
            font-weight: 600;
            color: #333;
            font-size: 14px;
            word-break: break-all;
        }

        .history-item-status {
            font-size: 12px;
            padding: 4px 8px;
            border-radius: 4px;
        }

        .history-item-status.completed {
            background: #d4edda;
            color: #155724;
        }

        .history-item-status.paused {
            background: #fff3cd;
            color: #856404;
        }

        .history-item-status.error {
            background: #f8d7da;
            color: #721c24;
        }

        .history-item-status.downloading {
            background: #cce5ff;
            color: #004085;
        }

        .history-item-meta {
            display: flex;
            gap: 15px;
            font-size: 12px;
            color: #666;
        }

        .history-item-progress {
            height: 6px;
            background: #e0e0e0;
            border-radius: 3px;
            overflow: hidden;
        }

        .history-item-progress-bar {
            height: 100%;
            background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
            transition: width 0.3s;
        }

        .history-item-actions {
            display: flex;
            gap: 8px;
        }

        .history-item-actions .btn {
            padding: 6px 12px;
            font-size: 12px;
        }

        .btn-clear {
            background: transparent;
            border: 1px solid #dc3545;
            color: #dc3545;
            padding: 6px 12px;
            font-size: 12px;
        }

        .btn-clear:hover {
            background: #dc3545;
            color: white;
        }

        .history-empty {
            text-align: center;
            color: #888;
            padding: 30px;
            font-size: 14px;
        }
```

---

## Task 3: 添加历史记录 UI HTML

**Files:**
- Modify: `streaming-download-spring-boot-starter/src/main/resources/static/download.html`

- [ ] **Step 1: 在 `.status` div 之后添加历史记录面板 HTML**

在 `<div class="status" id="status">状态: 空闲</div>` 之后添加：

```html
        <div class="history-section">
            <div class="history-header">
                <h2>📋 历史记录</h2>
                <button class="btn btn-clear" id="btnClearHistory">清空</button>
            </div>
            <div class="history-list" id="historyList">
                <div class="history-empty">暂无下载记录</div>
            </div>
        </div>
```

---

## Task 4: 集成 DownloadHistory 到 DownloadManager

**Files:**
- Modify: `streaming-download-spring-boot-starter/src/main/resources/static/download.html`

- [ ] **Step 1: 在 DownloadManager 构造函数中初始化 history**

在 `this.btnCancel = ...` 之后添加：

```javascript
                this.history = new DownloadHistory();
                this.currentRecordId = null;
```

- [ ] **Step 2: 修改 start() 方法，创建历史记录**

在 `start()` 方法中，找到 `this.updateUI();`（第 445 行），在该行和 `await this.download(url, false);`（第 448 行）之间插入：

```javascript
                    // 创建历史记录
                    const record = this.history.add({
                        url: url,
                        fileName: fileName,
                        totalBytes: 0,
                        loadedBytes: 0,
                        status: 'downloading'
                    });
                    this.currentRecordId = record.id;
                    this.renderHistory();
```

- [ ] **Step 3: 修改 download() 方法，更新历史进度**

在 `this.loadedBytes += value.length;` 之后添加：

```javascript
                        // 更新历史记录
                        if (this.currentRecordId) {
                            this.history.update(this.currentRecordId, {
                                loadedBytes: this.loadedBytes,
                                totalBytes: this.totalBytes,
                                status: 'downloading'
                            });
                        }
```

在 `this.status = DownloadStatus.COMPLETED;` 之后添加：

```javascript
                    // 更新历史记录为完成
                    if (this.currentRecordId) {
                        this.history.update(this.currentRecordId, {
                            loadedBytes: this.loadedBytes,
                            totalBytes: this.totalBytes,
                            status: 'completed'
                        });
                        this.renderHistory();
                    }
```

- [ ] **Step 4: 修改 pause() 方法，更新历史状态**

在 `this.updateUI();` 之后添加：

```javascript
                    // 更新历史记录为暂停
                    if (this.currentRecordId) {
                        this.history.update(this.currentRecordId, {
                            loadedBytes: this.loadedBytes,
                            totalBytes: this.totalBytes,
                            status: 'paused'
                        });
                        this.renderHistory();
                    }
```

- [ ] **Step 5: 修改 cancel() 方法，清除当前记录引用**

在 `this.status = DownloadStatus.IDLE;` 之前添加：

```javascript
                this.currentRecordId = null;
```

- [ ] **Step 6: 添加 renderHistory() 方法**

在 `cancel()` 方法之后添加：

```javascript
            // 渲染历史记录列表
            renderHistory() {
                const records = this.history.list();
                const listEl = document.getElementById('historyList');

                if (records.length === 0) {
                    listEl.innerHTML = '<div class="history-empty">暂无下载记录</div>';
                    return;
                }

                listEl.innerHTML = records.map(record => {
                    const progress = record.totalBytes > 0
                        ? (record.loadedBytes / record.totalBytes * 100).toFixed(1)
                        : 0;
                    const statusText = {
                        'completed': '已完成',
                        'paused': '已暂停',
                        'error': '出错',
                        'downloading': '下载中'
                    }[record.status] || record.status;

                    const timeStr = new Date(record.startTime).toLocaleString('zh-CN');

                    let actionsHtml = '';
                    if (record.status === 'paused') {
                        actionsHtml = `<button class="btn btn-primary" onclick="window.downloadManager.resumeFromHistory('${record.id}')">继续</button>`;
                    } else if (record.status === 'completed') {
                        actionsHtml = `<button class="btn btn-secondary" onclick="window.downloadManager.redownload('${record.id}')">重新下载</button>`;
                    }
                    actionsHtml += `<button class="btn btn-secondary" onclick="window.downloadManager.removeFromHistory('${record.id}')">删除</button>`;

                    return `
                        <div class="history-item">
                            <div class="history-item-header">
                                <span class="history-item-name">${this.escapeHtml(record.fileName)}</span>
                                <span class="history-item-status ${record.status}">${statusText}</span>
                            </div>
                            <div class="history-item-meta">
                                <span>${this.formatBytes(record.loadedBytes)} / ${this.formatBytes(record.totalBytes)}</span>
                                <span>${timeStr}</span>
                            </div>
                            <div class="history-item-progress">
                                <div class="history-item-progress-bar" style="width: ${progress}%"></div>
                            </div>
                            <div class="history-item-actions">
                                ${actionsHtml}
                            </div>
                        </div>
                    `;
                }).join('');
            }

            // HTML 转义
            escapeHtml(str) {
                const div = document.createElement('div');
                div.textContent = str;
                return div.innerHTML;
            }

            // 从历史记录继续下载
            async resumeFromHistory(recordId) {
                const record = this.history.get(recordId);
                if (!record) return;

                this.urlInput.value = record.url;
                this.loadedBytes = record.loadedBytes;
                this.totalBytes = record.totalBytes;
                this.currentRecordId = recordId;

                try {
                    this.status = DownloadStatus.CONNECTING;
                    this.updateUI();

                    // 选择保存位置
                    this.fileHandle = await window.showSaveFilePicker({
                        suggestedName: record.fileName
                    });

                    await this.download(record.url, true);

                    // 更新历史记录为完成
                    this.history.update(recordId, {
                        loadedBytes: this.loadedBytes,
                        totalBytes: this.totalBytes,
                        status: 'completed'
                    });
                    this.renderHistory();
                } catch (e) {
                    if (e.name !== 'AbortError') {
                        console.error('继续下载失败:', e);
                        this.status = DownloadStatus.ERROR;
                        this.history.update(recordId, { status: 'error' });
                        this.renderHistory();
                        this.updateUI();
                    }
                }
            }

            // 重新下载
            async redownload(recordId) {
                const record = this.history.get(recordId);
                if (!record) return;

                this.urlInput.value = record.url;
                this.loadedBytes = 0;
                this.totalBytes = 0;
                this.currentRecordId = null;

                await this.start();
            }

            // 从历史记录删除
            removeFromHistory(recordId) {
                this.history.remove(recordId);
                this.renderHistory();
            }

            // 清空历史记录
            clearHistory() {
                if (confirm('确定要清空所有下载记录吗？')) {
                    this.history.clear();
                    this.renderHistory();
                }
            }
```

- [ ] **Step 7: 暴露 downloadManager 到全局**

在 DOMContentLoaded 事件处理中，`manager.updateUI();` 之后添加：

```javascript
            // 暴露到全局供历史记录按钮调用
            window.downloadManager = manager;
```

- [ ] **Step 8: 绑定清空历史按钮事件**

在 `document.getElementById('btnCancel').addEventListener(...)` 之后添加：

```javascript
            document.getElementById('btnClearHistory').addEventListener('click', () => manager.clearHistory());

            // 初始渲染历史记录
            manager.renderHistory();
```

---

## Task 5: 创建打包脚本

**Files:**
- Create: `script/build-starter.bat`

- [ ] **Step 1: 创建 build-starter.bat**

```batch
@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo  Streaming Download Starter 构建脚本
echo ========================================
echo.

REM 获取脚本所在目录的父目录
set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."
cd /d "%PROJECT_ROOT%"

echo [1/4] 清理旧构建...
if exist "dist" rd /s /q "dist"

echo [2/4] 构建 core 和 starter 模块...
call mvn clean install -pl streaming-download-core,streaming-download-spring-boot-starter -DskipTests -q
if errorlevel 1 (
    echo.
    echo [错误] Maven 构建失败！
    pause
    exit /b 1
)

echo [3/4] 创建 dist 目录并复制 JAR...
mkdir "dist"

REM 复制 core JAR
for %%f in (streaming-download-core\target\*.jar) do (
    if not "%%~nxf"=="*-sources.jar" (
        if not "%%~nxf"=="*-javadoc.jar" (
            copy /y "%%f" "dist\" >nul
            echo   - %%~nxf
        )
    )
)

REM 复制 starter JAR
for %%f in (streaming-download-spring-boot-starter\target\*.jar) do (
    if not "%%~nxf"=="*-sources.jar" (
        if not "%%~nxf"=="*-javadoc.jar" (
            copy /y "%%f" "dist\" >nul
            echo   - %%~nxf
        )
    )
)

echo.
echo [4/4] 构建完成！
echo.
echo ========================================
echo  输出目录: %CD%\dist
echo  本地仓库: 已安装
echo ========================================
echo.
echo JAR 文件列表:
dir /b "dist\*.jar"
echo.
echo 使用方法:
echo  1. 将 dist 目录中的 JAR 文件复制到目标项目
echo  2. 或直接在 pom.xml 中引用（已安装到本地仓库）
echo.
pause
```

- [ ] **Step 2: 测试脚本执行**

```bash
cd C:\projects\duandianxuchuan
script\build-starter.bat
```

预期：dist 目录包含 core 和 starter 的 JAR 文件

---

## Task 6: 更新 README 文档

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 在「浏览器兼容性」章节后添加「历史记录模块」章节**

找到 `### 6. 浏览器兼容性` 章节（约第 269 行），在其 `#### 降级方案` 代码块结束（` ``` `）之后添加：

```markdown
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
  status: 'downloading' | 'paused' | 'completed' | 'error',
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
```

- [ ] **Step 2: 添加「构建和发布」章节**

在 `## 构建和运行` 章节后添加：

```markdown
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
```

- [ ] **Step 3: 在「核心概念」之前插入模块概览表格**

找到 `## 前端集成指南` 章节（约第 97 行），在 `### 核心概念` 之前插入：

```markdown
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
```

---

## Task 7: 验收测试

- [ ] **Step 1: 启动服务**

```bash
cd C:\projects\duandianxuchuan
script\start.bat
```

- [ ] **Step 2: 测试历史记录功能**

1. 访问 http://localhost:8080/download.html
2. 输入下载地址，点击「开始下载」
3. 下载过程中点击「暂停」
4. 刷新页面，验证历史记录显示
5. 点击「继续」验证断点续传
6. 点击「删除」验证删除功能
7. 点击「清空」验证清空功能

- [ ] **Step 3: 测试打包脚本**

```bash
script\build-starter.bat
```

验证 dist 目录包含：
- streaming-download-core-1.0.0-SNAPSHOT.jar
- streaming-download-spring-boot-starter-1.0.0-SNAPSHOT.jar

---

## 验收标准

| 功能 | 验收标准 |
|------|----------|
| 进度恢复 | 关闭浏览器重新打开，历史记录面板显示上次下载进度，点击「继续」可断点续传 |
| 历史管理 | 可删除单条记录、清空全部、重新下载已完成的文件 |
| 打包脚本 | 运行 build-starter.bat 后，dist/ 目录包含 core 和 starter 的 JAR 文件 |
| 文档完善 | README 清晰说明后端接口和前端模块的使用方法 |
