# 浏览器兼容性与 .crswap 问题修复设计

## 问题背景

### 问题 1：Chrome 86-104 版本 OOM 问题
- **现象**：大文件下载中断后，重新打开浏览器点击继续下载时报 `out of memory`
- **根因**：`.crswap` 临时文件残留，`createWritable({ keepExistingData: true })` 全量加载到内存
- **触发条件**：
  - Chrome 版本 < 130
  - 下载大文件（>1GB）
  - 浏览器非正常关闭导致 `.crswap` 残留

### 问题 2：Windows 7 + Chrome 86-104 兼容性问题
- **现象**：点击开始下载后直接使用传统下载，页面按钮无法控制
- **根因**：`showSaveFilePicker` API 存在但不可用
- **触发条件**：
  - Windows 7 操作系统
  - Chrome 86-104 版本

## 技术分析

### Chrome 版本与 File System Access API 对应关系

| Chrome 版本 | API 支持 | `.crswap` 处理 | 状态 |
|-------------|----------|----------------|------|
| < 86 | 不支持 | N/A | 降级传统下载 |
| 86-104 | 支持 | 全量加载 → OOM | 需要处理 |
| 105-129 | 支持 | 可能有问题 | 需要处理 |
| 130+ | 支持 | 流式处理 | 正常 |

### `.crswap` 文件机制
- Chrome File System Access API 的原子写入机制
- `createWritable()` 时创建，`writer.close()` 成功后合并并删除
- 浏览器崩溃/强制关闭时残留
- **关键发现**：当 `.crswap` 存在时，`fileHandle.getFile()` 会抛出错误

## 解决方案设计

### 1. 浏览器能力检测模块

新增 `BrowserCapability` 类，实现惰性检测：

```javascript
class BrowserCapability {
    constructor() {
        this._checked = false;
        this._canUseFileSystemAPI = null;
        this._checkPromise = null;
    }

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
                return { usable: true };
            }
            return { usable: false, reason: 'API_NOT_FUNCTIONAL', error: e };
        }
    }
}
```

### 2. `.crswap` 错误处理

在 `resumeFromHistory()` 中添加检测：

```javascript
async resumeFromHistory(recordId) {
    // ... 现有代码 ...

    try {
        // 尝试获取文件，检测 .crswap 问题
        await fileHandle.getFile();
    } catch (e) {
        if (e.name === 'NotReadableError' ||
            e.message?.toLowerCase().includes('swap') ||
            e.message?.toLowerCase().includes('readable')) {
            // .crswap 冲突
            this.handleCrSwapConflict(recordId, record.fileName);
            return;
        }
        throw e;
    }

    // 正常继续下载...
}

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

### 3. 下载流程改造

```
开始下载
    │
    ▼
┌─────────────────────────┐
│ 检测浏览器能力（惰性）   │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ API 可用？              │
└───────────┬─────────────┘
            │
    ┌───────┴───────┐
    │ 是            │ 否
    ▼               ▼
┌─────────────┐ ┌─────────────┐
│ File System │ │ 传统下载    │
│ Access API  │ │ (无断点续传)│
│ 断点续传    │ │ 显示提示    │
└─────────────┘ └─────────────┘
```

### 4. 用户提示设计

#### API 不可用提示（非阻塞）

```
┌────────────────────────────────────────────────────┐
│ ℹ️  当前浏览器不支持断点续传功能                     │
│    下载过程中无法暂停，建议使用 Chrome 130+ 版本     │
└────────────────────────────────────────────────────┘
```

#### `.crswap` 冲突提示（弹窗）

```
检测到上次下载未正常结束，存在临时文件冲突。

请按以下步骤操作：
1. 打开文件保存目录
2. 删除以 .crswap 结尾的临时文件
3. 点击"重新下载"开始新的下载

文件名：xxx.zip
```

### 5. 历史记录按钮逻辑

| 状态 | 条件 | 显示按钮 |
|------|------|----------|
| `paused` | API 可用 + 无冲突 | 继续 |
| `paused` | 有 `.crswap` 冲突 | 重新下载 |
| `error` | `CR_SWAP_CONFLICT` | 重新下载 |
| `error` | 其他错误 + 有进度 | 重试 |
| `completed` | - | 重新下载 |
| 任何 | API 不可用 | 隐藏继续按钮 |

### 6. 数据结构调整

历史记录新增字段：

```javascript
{
    id: 'xxx',
    url: '...',
    fileName: '...',
    totalBytes: 1000000,
    loadedBytes: 500000,
    status: 'error',           // 状态
    error: 'CR_SWAP_CONFLICT', // 新增：错误类型
    startTime: 1234567890,
    updateTime: 1234567890
}
```

## 实现计划

### 任务 1：新增 BrowserCapability 类
- 实现惰性检测逻辑
- 集成到 DownloadManager

### 任务 2：改造 start() 方法
- 添加能力检测
- 实现降级逻辑
- 添加用户提示

### 任务 3：改造 resumeFromHistory() 方法
- 添加 `.crswap` 检测
- 实现错误处理
- 更新历史记录状态

### 任务 4：UI 优化
- 添加非阻塞提示组件
- 调整历史记录按钮逻辑
- 添加 `.crswap` 冲突提示

### 任务 5：测试验证
- Chrome 104 环境测试
- Windows 7 环境测试
- Chrome 130+ 环境回归测试

## 风险与注意事项

1. **兼容性**：确保不影响 Chrome 130+ 的正常使用
2. **用户体验**：降级时给用户明确提示，避免困惑
3. **数据安全**：`.crswap` 冲突时不自动删除文件，让用户手动处理
4. **性能**：能力检测只执行一次，避免重复开销
