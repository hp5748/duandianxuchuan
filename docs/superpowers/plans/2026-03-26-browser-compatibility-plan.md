# 浏览器兼容性与 .crswap 问题修复实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Chrome 86-104 版本的 .crswap OOM 问题，以及 Windows 7 兼容性问题

**Architecture:** 新增 BrowserCapability 类实现惰性能力检测，改造下载流程支持降级，捕获 .crswap 错误并提示用户

**Tech Stack:** JavaScript ES6+, File System Access API, IndexedDB, localStorage

---

## 文件结构

```
streaming-download-spring-boot-starter/src/main/resources/static/
└── download.html          # 所有代码都在这个文件中（单文件架构）
    ├── CSS 样式            # 新增提示组件样式
    ├── HTML 结构           # 新增提示容器
    └── JavaScript
        ├── BrowserCapability (新增)  # 浏览器能力检测
        ├── SpeedCalculator (不变)
        ├── DownloadHistory (不变)
        ├── DownloadStatus (不变)
        └── DownloadManager (修改)
            ├── checkBrowserSupport() → 废弃，改用 capability
            ├── start()               # 添加能力检测
            ├── resumeFromHistory()   # 添加 .crswap 检测
            ├── handleCrSwapConflict() (新增)
            └── renderHistory()       # 调整按钮逻辑
```

---

## Task 1: 新增 BrowserCapability 类和提示组件样式

**Files:**
- Modify: `streaming-download-spring-boot-starter/src/main/resources/static/download.html`

### Step 1: 添加提示组件 CSS 样式

在 `</style>` 标签前添加：

```css
        /* 通知提示样式 */
        .notification {
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 15px 20px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
            z-index: 1000;
            display: flex;
            align-items: center;
            gap: 10px;
            max-width: 400px;
            animation: slideIn 0.3s ease;
        }

        @keyframes slideIn {
            from {
                transform: translateX(100%);
                opacity: 0;
            }
            to {
                transform: translateX(0);
                opacity: 1;
            }
        }

        .notification.info {
            background: #e3f2fd;
            border: 1px solid #90caf9;
            color: #1565c0;
        }

        .notification.warning {
            background: #fff3e0;
            border: 1px solid #ffcc80;
            color: #e65100;
        }

        .notification.error {
            background: #ffebee;
            border: 1px solid #ef9a9a;
            color: #c62828;
        }

        .notification-close {
            background: transparent;
            border: none;
            font-size: 18px;
            cursor: pointer;
            opacity: 0.6;
            padding: 0 0 0 10px;
        }

        .notification-close:hover {
            opacity: 1;
        }

        /* 历史记录错误状态 */
        .history-item-status.conflict {
            background: #ffebee;
            color: #c62828;
        }
```

### Step 2: 添加通知容器 HTML

在 `<div class="container">` 前添加：

```html
    <!-- 通知容器 -->
    <div id="notificationContainer"></div>
```

### Step 3: 添加 BrowserCapability 类

在 `class SpeedCalculator` 之前添加：

```javascript
        // 浏览器能力检测器
        class BrowserCapability {
            constructor() {
                this._checked = false;
                this._canUseFileSystemAPI = null;
                this._checkPromise = null;
            }

            // 惰性检测，只检测一次
            async checkFileSystemAccess() {
                if (this._checked) return this._canUseFileSystemAPI;
                if (this._checkPromise) return this._checkPromise;

                this._checkPromise = this._doCheck();
                this._canUseFileSystemAPI = await this._checkPromise;
                this._checked = true;
                return this._canUseFileSystemAPI;
            }

            async _doCheck() {
                // 1. 基础检测：API 是否存在
                if (!window.showSaveFilePicker) {
                    return { usable: false, reason: 'API_NOT_SUPPORTED' };
                }

                // 2. 功能检测：尝试实际调用
                try {
                    const handle = await window.showSaveFilePicker({
                        suggestedName: '.capability_test_' + Date.now()
                    });

                    // 3. 验证 getFile 是否正常
                    await handle.getFile();

                    // 4. 清理测试文件
                    try { await handle.remove(); } catch (e) {}

                    return { usable: true };
                } catch (e) {
                    if (e.name === 'AbortError') {
                        // 用户取消 = API 可用
                        return { usable: true };
                    }
                    // 其他错误 = API 不可用（如 Windows 7）
                    return { usable: false, reason: 'API_NOT_FUNCTIONAL', error: e };
                }
            }

            // 重置检测状态（用于重新检测）
            reset() {
                this._checked = false;
                this._canUseFileSystemAPI = null;
                this._checkPromise = null;
            }
        }
```

- [ ] **Step 4: 验证修改**

确认文件结构正确，CSS、HTML、JavaScript 三部分都已添加。

---

## Task 2: 改造 DownloadManager 构造函数和通知方法

**Files:**
- Modify: `streaming-download-spring-boot-starter/src/main/resources/static/download.html`

### Step 1: 修改 DownloadManager 构造函数

在 `constructor()` 中添加 `capability` 实例：

```javascript
            constructor() {
                this.apiBase = '/api/proxy';
                this.fileHandle = null;
                this.loadedBytes = 0;
                this.totalBytes = 0;
                this.status = DownloadStatus.IDLE;
                this.speedCalc = new SpeedCalculator();
                this.abortController = null;
                this.reader = null;
                this.writer = null;
                this.capability = new BrowserCapability();  // 新增

                // UI 元素
                // ... 其余代码不变 ...
            }
```

### Step 2: 添加通知显示方法

在 `formatBytes()` 方法后添加：

```javascript
            // 显示通知提示
            showNotification(message, type = 'info', duration = 5000) {
                const container = document.getElementById('notificationContainer');

                const notification = document.createElement('div');
                notification.className = `notification ${type}`;

                const icons = {
                    info: 'ℹ️',
                    warning: '⚠️',
                    error: '❌'
                };

                notification.innerHTML = `
                    <span>${icons[type] || 'ℹ️'} ${message}</span>
                    <button class="notification-close" onclick="this.parentElement.remove()">×</button>
                `;

                container.appendChild(notification);

                // 自动消失
                if (duration > 0) {
                    setTimeout(() => {
                        notification.style.animation = 'slideIn 0.3s ease reverse';
                        setTimeout(() => notification.remove(), 300);
                    }, duration);
                }
            }
```

- [ ] **Step 3: 验证修改**

确认构造函数和通知方法已正确添加。

---

## Task 3: 改造 start() 方法

**Files:**
- Modify: `streaming-download-spring-boot-starter/src/main/resources/static/download.html`

### Step 1: 替换 start() 方法的浏览器检测逻辑

找到 `async start()` 方法，替换开头的检测逻辑：

**原代码：**
```javascript
            async start() {
                const url = this.urlInput.value.trim();
                if (!url) {
                    alert('请输入下载地址');
                    return;
                }

                // 检查浏览器兼容性
                if (!this.checkBrowserSupport()) {
                    // 降级为传统下载
                    this.fallbackDownload(url);
                    return;
                }
```

**替换为：**
```javascript
            async start() {
                const url = this.urlInput.value.trim();
                if (!url) {
                    alert('请输入下载地址');
                    return;
                }

                // 检查浏览器能力（惰性检测）
                const capability = await this.capability.checkFileSystemAccess();

                if (!capability.usable) {
                    // API 不可用，使用传统下载
                    if (!localStorage.getItem('fs_api_unsupported_shown')) {
                        localStorage.setItem('fs_api_unsupported_shown', 'true');
                        this.showNotification(
                            '当前浏览器不支持断点续传功能，将使用传统下载方式。建议使用 Chrome 130+ 版本。',
                            'warning',
                            8000
                        );
                    }
                    this.fallbackDownload(url);
                    return;
                }
```

- [ ] **Step 2: 验证修改**

确认 start() 方法的检测逻辑已正确替换。

---

## Task 4: 改造 resumeFromHistory() 方法

**Files:**
- Modify: `streaming-download-spring-boot-starter/src/main/resources/static/download.html`

### Step 1: 添加 .crswap 冲突处理方法

在 `escapeHtml()` 方法后添加：

```javascript
            // 处理 .crswap 冲突
            handleCrSwapConflict(recordId, fileName) {
                alert(
                    '检测到上次下载未正常结束，存在临时文件冲突。\n\n' +
                    '请按以下步骤操作：\n' +
                    '1. 打开文件保存目录\n' +
                    '2. 删除以 .crswap 结尾的临时文件\n' +
                    '3. 点击"重新下载"开始新的下载\n\n' +
                    `文件名：${fileName}`
                );

                // 清理不可用的 fileHandle
                this.history.removeFileHandle(recordId).catch(() => {});

                // 更新状态
                this.history.update(recordId, {
                    status: 'error',
                    error: 'CR_SWAP_CONFLICT'
                });
                this.renderHistory();
            }
```

### Step 2: 修改 resumeFromHistory() 方法

在获取 fileHandle 后添加 .crswap 检测：

找到 `async resumeFromHistory(recordId)` 方法中获取 fileHandle 后的位置：

**在以下代码之后：**
```javascript
                try {
                    // 尝试从 IndexedDB 获取已保存的 fileHandle
                    let fileHandle = await this.history.getFileHandle(recordId);
```

**添加检测代码：**
```javascript
                    // 检测 .crswap 冲突
                    if (fileHandle) {
                        try {
                            await fileHandle.getFile();
                        } catch (e) {
                            // .crswap 残留会导致 getFile() 失败
                            if (e.name === 'NotReadableError' ||
                                (e.message && (
                                    e.message.toLowerCase().includes('swap') ||
                                    e.message.toLowerCase().includes('readable') ||
                                    e.message.toLowerCase().includes('lock')
                                ))) {
                                this.handleCrSwapConflict(recordId, record.fileName);
                                return;
                            }
                            // 其他错误继续尝试
                            console.warn('getFile() 检测异常:', e);
                        }
                    }
```

- [ ] **Step 3: 验证修改**

确认 resumeFromHistory() 方法已正确添加 .crswap 检测逻辑。

---

## Task 5: 改造 renderHistory() 方法按钮逻辑

**Files:**
- Modify: `streaming-download-spring-boot-starter/src/main/resources/static/download.html`

### Step 1: 修改历史记录按钮渲染逻辑

找到 `renderHistory()` 方法中的按钮渲染部分：

**原代码：**
```javascript
                            <div class="history-item-actions">
                                ${['paused', 'cancelled', 'error'].includes(record.status) && record.loadedBytes > 0 ? `<button class="btn btn-primary" onclick="downloadManager.resumeFromHistory('${record.id}')">继续</button>` : ''}
                                ${record.status === 'completed' || (record.status === 'error' && record.loadedBytes === 0) ? `<button class="btn btn-primary" onclick="downloadManager.redownload('${record.id}')">重新下载</button>` : ''}
                                ${record.status === 'downloading' ? `<button class="btn btn-warning" onclick="downloadManager.resumeFromHistory('${record.id}')">恢复</button>` : ''}
                                <button class="btn btn-secondary" onclick="downloadManager.removeFromHistory('${record.id}')">删除</button>
                            </div>
```

**替换为：**
```javascript
                            <div class="history-item-actions">
                                ${this._shouldShowResumeButton(record) ? `<button class="btn btn-primary" onclick="downloadManager.resumeFromHistory('${record.id}')">继续</button>` : ''}
                                ${this._shouldShowRedownloadButton(record) ? `<button class="btn btn-primary" onclick="downloadManager.redownload('${record.id}')">重新下载</button>` : ''}
                                <button class="btn btn-secondary" onclick="downloadManager.removeFromHistory('${record.id}')">删除</button>
                            </div>
```

### Step 2: 添加按钮显示判断方法

在 `renderHistory()` 方法后添加：

```javascript
            // 判断是否显示继续按钮
            _shouldShowResumeButton(record) {
                // CR_SWAP_CONFLICT 错误不显示继续按钮
                if (record.error === 'CR_SWAP_CONFLICT') return false;

                // paused/cancelled/error 状态且有进度
                if (['paused', 'cancelled', 'error'].includes(record.status) && record.loadedBytes > 0) {
                    return true;
                }

                // downloading 状态（页面刷新后恢复）
                if (record.status === 'downloading') {
                    return true;
                }

                return false;
            }

            // 判断是否显示重新下载按钮
            _shouldShowRedownloadButton(record) {
                // 已完成
                if (record.status === 'completed') return true;

                // CR_SWAP_CONFLICT 错误
                if (record.error === 'CR_SWAP_CONFLICT') return true;

                // 其他错误且无进度
                if (record.status === 'error' && record.loadedBytes === 0) return true;

                return false;
            }
```

### Step 3: 更新状态标签显示

在 `renderHistory()` 方法中找到状态标签部分，添加 conflict 状态的 CSS 类：

**原代码：**
```javascript
                                <span class="history-item-status ${record.status}">${statusLabels[record.status] || record.status}</span>
```

**替换为：**
```javascript
                                <span class="history-item-status ${record.error === 'CR_SWAP_CONFLICT' ? 'conflict' : record.status}">${record.error === 'CR_SWAP_CONFLICT' ? '临时文件冲突' : (statusLabels[record.status] || record.status)}</span>
```

- [ ] **Step 4: 验证修改**

确认 renderHistory() 方法和辅助方法已正确修改。

---

## Task 6: 更新页面初始化逻辑

**Files:**
- Modify: `streaming-download-spring-boot-starter/src/main/resources/static/download.html`

### Step 1: 修改 DOMContentLoaded 事件处理

找到 `document.addEventListener('DOMContentLoaded', ...)` 部分：

**原代码：**
```javascript
            // 检查浏览器兼容性
            if (!manager.checkBrowserSupport()) {
                document.getElementById('browserWarning').classList.add('show');
            }
```

**替换为：**
```javascript
            // 异步检查浏览器兼容性（惰性检测）
            manager.capability.checkFileSystemAccess().then(capability => {
                if (!capability.usable) {
                    document.getElementById('browserWarning').classList.add('show');
                    // 修改提示文本
                    document.getElementById('browserWarning').innerHTML = `
                        <strong>浏览器兼容性提示：</strong>当前浏览器不支持断点续传功能（${capability.reason === 'API_NOT_FUNCTIONAL' ? 'API 不可用' : 'API 不支持'}）。
                        建议使用 Chrome 130+ 浏览器以获得完整功能。
                    `;
                }
            });
```

- [ ] **Step 2: 验证修改**

确认初始化逻辑已正确修改为异步检测。

---

## Task 7: 提交和测试

### Step 1: 提交代码

```bash
git add streaming-download-spring-boot-starter/src/main/resources/static/download.html
git commit -m "fix: 修复 Chrome 86-104 版本 .crswap OOM 问题和 Windows 7 兼容性

- 新增 BrowserCapability 类实现惰性能力检测
- 捕获 .crswap 冲突错误并提示用户清理
- API 不可用时降级到传统下载并显示提示
- 优化历史记录按钮显示逻辑

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

### Step 2: 手动测试清单

- [ ] Chrome 130+ 正常下载、暂停、继续
- [ ] Chrome 130+ 关闭浏览器后恢复下载
- [ ] Chrome 104 模拟 .crswap 冲突（手动创建 .crswap 文件）
- [ ] Windows 7 + Chrome 86-104 降级提示显示
- [ ] 历史记录按钮显示正确

---

## 风险与回滚

如果出现问题，可以通过以下方式回滚：

```bash
git revert HEAD
```

主要风险点：
1. 能力检测可能误判 - 已通过惰性检测和缓存减少影响
2. .crswap 检测可能漏掉某些错误类型 - 已添加多种错误关键词匹配
