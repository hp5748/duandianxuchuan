# 历史记录与打包优化设计

## 概述

为 streaming-download-spring-boot-starter 新增 4 个功能点：
1. 支持网站关闭后重新打开继续下载，显示上次进度
2. 显示历史记录（完整管理）
3. 提供一键打包 starter 的脚本
4. 在 README 中充分说明可使用的接口和前端模块

## 设计决策

| 需求 | 决策 |
|------|------|
| 进度恢复 | localStorage 纯前端存储 |
| 历史记录 | 完整管理（继续下载/删除/清空/重新下载） |
| 打包脚本 | 本地仓库 + JAR 输出到 dist |
| 接口文档 | 完善 README，不新增后端接口 |

## 实现方案

采用**前端优先**方案：重点改造前端 download.html，后端几乎不动。

## 详细设计

### 1. 前端改造

#### 1.1 新增模块

| 模块 | 职责 |
|------|------|
| `DownloadHistory` | 管理 localStorage 中的历史记录（增删查改） |
| `HistoryPanel` | 历史记录 UI 面板（显示/操作） |

#### 1.2 数据结构（localStorage）

```javascript
// Key: 'download_history'
// Value: Array<DownloadRecord>
{
  id: 'uuid',           // 唯一标识
  url: 'string',        // 下载地址
  fileName: 'string',   // 文件名
  totalBytes: number,   // 总大小
  loadedBytes: number,  // 已下载大小
  status: 'downloading' | 'paused' | 'completed' | 'error',
  startTime: timestamp,
  updateTime: timestamp
}
```

#### 1.3 UI 布局

```
┌─────────────────────────────────────┐
│  流式代理下载 - 断点续传 Demo        │
├─────────────────────────────────────┤
│  [下载地址输入框]                    │
│  [进度条]                            │
│  [统计信息]                          │
│  [按钮: 开始/暂停/继续/取消]          │
├─────────────────────────────────────┤
│  📋 历史记录                    [清空] │
│  ┌─────────────────────────────────┐ │
│  │ file.zip  500MB  2024-03-26     │ │
│  │ ████████░░ 80%  [继续] [删除]   │ │
│  └─────────────────────────────────┘ │
│  ┌─────────────────────────────────┐ │
│  │ test.exe  1.2GB  2024-03-25     │ │
│  │ ██████████ 100%  ✅ 已完成 [删除]│ │
│  └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### 2. 打包脚本

**文件**：`script/build-starter.bat`

```batch
@echo off
echo === 构建 Streaming Download Spring Boot Starter ===

REM 1. 清理并打包
call mvn clean install -pl streaming-download-core,streaming-download-spring-boot-starter -DskipTests

REM 2. 创建 dist 目录
if not exist "dist" mkdir dist

REM 3. 复制 JAR 文件
copy /Y streaming-download-core\target\*.jar dist\
copy /Y streaming-download-spring-boot-starter\target\*.jar dist\

echo === 构建完成 ===
echo JAR 文件已输出到 dist\ 目录
echo 同时已安装到本地 Maven 仓库
dir dist\*.jar
```

### 3. README 文档更新

#### 3.1 后端接口说明

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/proxy/download` | GET | 流式下载（支持断点续传） |

详细说明：请求参数、请求头、响应头、状态码、错误格式（已有，优化排版）

#### 3.2 前端模块说明

**集成方式**：

```html
<!-- 方式1：直接使用自带页面 -->
<link rel="stylesheet" href="/download.html">

<!-- 方式2：复制 JS 模块到自己的项目 -->
<script src="/download.js"></script>
```

**核心模块 API**：

```javascript
// 速率计算器
const speedCalc = new SpeedCalculator(windowSize);

// 下载管理器
const manager = new DownloadManager();
manager.start(url);
manager.pause();
manager.resume();
manager.cancel();

// 历史记录（新增）
const history = new DownloadHistory();
history.add(record);           // 添加记录
history.list();                // 获取所有记录
history.update(id, data);      // 更新进度
history.remove(id);            // 删除记录
history.clear();               // 清空历史
```

## 文件改动清单

| 文件 | 操作 |
|------|------|
| `streaming-download-spring-boot-starter/.../static/download.html` | 修改：新增历史记录模块和 UI |
| `script/build-starter.bat` | 新增：打包脚本 |
| `README.md` | 修改：完善接口文档 + 前端模块说明 |

## 验收标准

1. **进度恢复**：关闭浏览器重新打开，历史记录面板显示上次下载进度，点击「继续」可断点续传
2. **历史管理**：可删除单条记录、清空全部、重新下载已完成的文件
3. **打包脚本**：运行 `build-starter.bat` 后，`dist/` 目录包含 core 和 starter 的 JAR 文件
4. **文档完善**：README 清晰说明后端接口和前端模块的使用方法
