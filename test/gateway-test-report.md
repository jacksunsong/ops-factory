# 方案 A：统一 Toast + 错误分级

## Context

Web App 当前存在 5 种不一致的错误展示模式（内联 div、Toast、alert()、静默吞掉、全屏 ErrorBoundary），导致用户体验碎片化。本次改造目标：统一走 Toast 系统，按严重程度分级展示，消除 `alert()` 和原始错误直露。

## 错误分级定义

| 级别       | Toast 类型 | 展示方式                       | 场景                                   |
| :--------- | :--------- | :----------------------------- | :------------------------------------- |
| FATAL      | -          | ErrorBoundary 全屏（保持现有） | 组件渲染崩溃                           |
| ERROR      | `error`    | Toast + 友好文案               | API 失败、网络错误、SSE 断开           |
| WARNING    | `warning`  | Toast                          | 操作失败（删除、创建等）、校验拦截     |
| SILENT     | -          | console.error only             | 非关键失败（best-effort 文件 diff 等） |
| VALIDATION | -          | 内联红色提示（保留现有）       | 表单字段校验                           |

## 实施步骤

### 1. ToastProvider 提升到根级

**文件:** `web-app/src/main.tsx`

将 `ToastProvider` 从 App.tsx 的 ProtectedRoute 内部移到 main.tsx 根级，使登录页等未认证页面也能使用 Toast。

```
ErrorBoundary > BrowserRouter > ToastProvider > UserProvider > GoosedProvider > Routes
```

同步从 `App.tsx` 中移除 ToastProvider。

### 2. 新增错误文案映射工具

**新建文件:** `web-app/src/utils/errorMessages.ts`

```typescript
// HTTP 状态码 → 用户友好文案 (i18n key)
// 提供 getErrorMessage(error: unknown): string 函数
// 将 HTTP 状态码、网络错误、超时等映射为 i18n key
```

同步在 `en.json` / `zh.json` 中添加对应的翻译 key：

- `errors.networkError` — 网络连接失败
- `errors.serverError` — 服务暂时不可用
- `errors.unauthorized` — 认证失效
- `errors.timeout` — 请求超时
- `errors.unknown` — 未知错误
- `errors.deleteFailed` — 删除失败
- `errors.createFailed` — 创建失败
- `errors.operationFailed` — 操作失败

### 3. 替换所有 alert() 为 showToast()

| 文件                | 行                                            | 原代码                                                 | 改为 |
| :------------------ | :-------------------------------------------- | :----------------------------------------------------- | :--- |
| `Home.tsx:64`       | alert(t('home.failedToCreateSession'...))     | showToast('error', t('home.failedToCreateSession'...)) |      |
| `ChatInput.tsx:186` | alert(t('chat.imageUploadNotEnabled'))        | showToast('warning', t('chat.imageUploadNotEnabled'))  |      |
| `ChatInput.tsx:196` | alert(t('chat.maxImagesAllowed'...))          | showToast('warning', t('chat.maxImagesAllowed'...))    |      |
| `ChatInput.tsx:199` | alert(t('chat.maxFilesAllowed'...))           | showToast('warning', t('chat.maxFilesAllowed'...))     |      |
| `ChatInput.tsx:285` | alert(t('chat.imageUploadNotEnabled'))        | showToast('warning', t('chat.imageUploadNotEnabled'))  |      |
| `ChatInput.tsx:291` | alert(t('chat.maxImagesAllowed'...))          | showToast('warning', t('chat.maxImagesAllowed'...))    |      |
| `History.tsx:162`   | alert('Failed to delete session: ' + message) | showToast('error', t('errors.deleteFailed'))           |      |

ChatInput 需要新增 `useToast` 导入。Home.tsx 和 History.tsx 同理。

### 4. 修复 SSE 流错误闭包 bug

**文件:** `web-app/src/hooks/useChat.ts`

`sendMessage` 函数中 STREAM_FINISH dispatch (约 line 281) 读取的 `state.error` 来自函数开始时的闭包快照，不会反映流中设置的错误。需要改用 `useRef` 追踪流期间的错误，或在 finally 块中通过 reducer 的当前 state 判断。

### 5. 语音输入错误展示

**文件:** `web-app/src/components/ChatInput.tsx`

`useVoiceInput` 返回的 `error` 当前未被使用。添加 `useEffect` 监听 `voiceError` 变化，有值时调用 `showToast('error', voiceError)`。

### 6. 静默失败改为 Toast（选择性）

| 文件                      | 场景                    | 处理                                       |
| :------------------------ | :---------------------- | :----------------------------------------- |
| `FilePreview.tsx:128`     | 复制到剪贴板失败        | showToast('error', t('errors.copyFailed')) |
| `Chat.tsx:80`             | model info 获取失败     | 保持 SILENT（非关键）                      |
| `PreviewContext.tsx:56`   | Office Preview 配置获取 | 保持 SILENT（非关键）                      |
| `MessageList.tsx:104,150` | 文件 diff baseline      | 保持 SILENT（best-effort）                 |

### 7. 各 hook 的 HTTP 错误友好化

以下 hooks 目前直接返回 `HTTP ${status}: ${text}` 形式的错误文本给组件展示：

- `useAgentConfig.ts:29` — 改用 `getErrorMessage()`
- `useMcp.ts:41,86,125,149` — 改用 `getErrorMessage()`
- `useSkills.ts:27` — 改用 `getErrorMessage()`
- `GoosedContext.tsx:61` — 改用 `getErrorMessage()`
- `useMonitoring.ts:76-78` — 改用 `getErrorMessage()`

各 hook 的 error 设值改为调用 `getErrorMessage(err)` 而非原始 HTTP 错误字符串。

## 改动文件清单（约 15 个）

| 文件                              | 改动                             |
| :-------------------------------- | :------------------------------- |
| `main.tsx`                        | 添加 ToastProvider 包裹          |
| `App.tsx`                         | 移除 ToastProvider               |
| **新建** `utils/errorMessages.ts` | 错误映射工具函数                 |
| `i18n/en.json`                    | 添加 errors.* 翻译               |
| `i18n/zh.json`                    | 添加 errors.* 翻译               |
| `Home.tsx`                        | alert → showToast                |
| `ChatInput.tsx`                   | alert → showToast + 语音错误展示 |
| `History.tsx`                     | alert → showToast                |
| `useChat.ts`                      | 修复 SSE 闭包 bug                |
| `FilePreview.tsx`                 | 复制失败 → showToast             |
| `useAgentConfig.ts`               | 错误友好化                       |
| `useMcp.ts`                       | 错误友好化                       |
| `useSkills.ts`                    | 错误友好化                       |
| `GoosedContext.tsx`               | 错误友好化                       |
| `useMonitoring.ts`                | 错误友好化                       |

## 验证

1. `cd web-app && npm run build` — 确保无 TypeScript 编译错误
2. 手动测试：
   - 断网状态下操作 → 应看到友好 Toast 而非原始 HTTP 错误
   - 删除 session 失败 → Toast 而非 alert()
   - 文件上传超限 → warning Toast 而非 alert()
   - SSE 流中断 → 正确展示错误
   - 复制文件内容失败 → Toast 提示
   - 登录页异常 → Toast 可用（不再因 provider 缺失而崩溃）











## 改动总结

### 基础设施（3 个文件）

| 文件                                                         | 改动                                                      |
| ------------------------------------------------------------ | --------------------------------------------------------- |
| [main.tsx](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/main.tsx) | ToastProvider 提升到根级，登录页也可用 Toast              |
| [App.tsx](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/App.tsx) | 移除原来嵌套在 ProtectedRoute 内的 ToastProvider          |
| **新建** [errorMessages.ts](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/utils/errorMessages.ts) | `getErrorMessage()` 工具函数，HTTP 状态码 → i18n 友好文案 |

### i18n 翻译（2 个文件）

| 文件                                                         | 改动                                  |
| ------------------------------------------------------------ | ------------------------------------- |
| [en.json](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/i18n/en.json) | 新增 `errors.*` 命名空间（13 个 key） |
| [zh.json](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/i18n/zh.json) | 对应中文翻译                          |

### alert() → Toast（3 个文件，7 处）

| 文件                                                         | 改动                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| [Home.tsx](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/pages/Home.tsx) | 创建 session 失败：`alert` → `showToast('error', ...)`       |
| [History.tsx](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/pages/History.tsx) | 删除 session 失败：`alert` → `showToast('error', t('errors.deleteFailed'))` |
| [ChatInput.tsx](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/components/ChatInput.tsx) | 6 处 `alert` → `showToast('warning', ...)`                   |

### Bug 修复（1 个文件）

| 文件                                                         | 改动                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| [useChat.ts](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/hooks/useChat.ts) | SSE 流错误闭包 bug：用 `streamErrorRef` 替代 `state.error` 闭包快照，移除 `state.error` 依赖 |

### 静默失败改为 Toast（2 个文件）

| 文件                                                         | 改动                                                  |
| ------------------------------------------------------------ | ----------------------------------------------------- |
| [ChatInput.tsx](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/components/ChatInput.tsx) | 语音输入错误 → `useEffect` 监听 `voiceError` 并 Toast |
| [FilePreview.tsx](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/components/FilePreview.tsx) | 剪贴板复制失败 → `showToast('error', ...)`            |

### HTTP 错误友好化（5 个文件）

| 文件                                                         | 改动                                |
| ------------------------------------------------------------ | ----------------------------------- |
| [useAgentConfig.ts](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/hooks/useAgentConfig.ts) | `setError(getErrorMessage(err))`    |
| [useMcp.ts](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/hooks/useMcp.ts) | 4 处 catch → `getErrorMessage(err)` |
| [useSkills.ts](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/hooks/useSkills.ts) | `setError(getErrorMessage(err))`    |
| [GoosedContext.tsx](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/contexts/GoosedContext.tsx) | `setError(getErrorMessage(err))`    |
| [useMonitoring.ts](vscode-webview://1cnkevekpg0mo7p6bltb3kb4kgtvvn73vu16qs4ulmj8st022i44/web-app/src/hooks/useMonitoring.ts) | 2 处 catch → `getErrorMessage(err)` |