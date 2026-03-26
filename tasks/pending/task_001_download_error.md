# 任务卡

## 基本信息
- **任务ID**: T001
- **标题**: 修复本地文件路径下载错误提示不清晰问题
- **负责角色**: developer
- **优先级**: P1
- **状态**: pending
- **创建时间**: 2026-03-26T08:42:30
- **依赖**: 无

## 问题描述

### 复现步骤
1. 启动服务：http://localhost:8080
2. 访问测试页面：http://localhost:8080/download.html
3. 输入本地文件路径：`C:\Users\Admin\Desktop\jdk-8u202-windows-x64.exe`
4. 点击"开始下载"

### 实际行为
- 前端：浏览器直接报错（Tomcat 无法解析包含 `\` 的 URL）
- 后端：无法收到请求，因为请求在 Tomcat 层就被拒绝

### 错误日志
```
java.lang.IllegalArgumentException: Invalid character found in the request target
[/api/proxy/download?url=C:\Users\Admin\Desktop\Desktop\jdk-8u202-windows-x64.exe].
The valid characters are defined in RFC 7230 and RFC 3986
```

### 如果 URL 编码后发送
- 返回：`{"code":400,"message":"URL 格式非法: C:\\Users\\Admin\\Desktop\\jdk-8u202-windows-x64.exe"}`
- 这个错误信息不够友好，没有明确告诉用户"只支持 HTTP/HTTPS 协议"

### 根因分析
1. **前端问题**：`encodeURIComponent()` 会编码 URL，但如果用户直接输入本地路径，反斜杠 `\` 在 URL 中是非法字符
2. **后端问题**：错误信息"URL 格式非法"不够明确，应该告诉用户"仅支持 HTTP/HTTPS 协议"

### 期望行为
1. 前端应该验证输入是否为有效的 HTTP/HTTPS URL，如果不是，应该给出友好提示
2. 后端错误信息应该更明确："仅支持 HTTP 和 HTTPS 协议，不支持本地文件路径"

## 验收标准
- [ ] 前端验证：输入非 HTTP/HTTPS URL 时，显示友好错误提示
- [ ] 后端错误信息优化：明确说明"仅支持 HTTP/HTTPS 协议"
- [ ] 测试用例：验证本地路径输入的处理

## 输出产物
- [ ] 前端代码修改: download.html
- [ ] 后端代码修改: UrlValidator.java 或 InvalidUrlException.java
