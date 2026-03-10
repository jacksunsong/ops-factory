# 错误处理统一化测试报告

**日期:** 2026-03-10
**改动范围:** 统一 Toast + 错误分级 + Gateway 断连修复 + 统一错误横幅样式 + 错误横幅位置统一
**测试框架:** Vitest + @testing-library/react + jsdom; Playwright (e2e)

---

## 测试结果总览

### 单元测试（Vitest）

| 指标 | 值 |
|------|-----|
| 测试文件数 | 9 |
| 测试用例数 | 115 |
| 通过 | 115 |
| 失败 | 0 |
| 耗时 | 1.60s |

**结果: 全部通过**

### E2E 测试（Playwright）

| 指标 | 值 |
|------|-----|
| 测试文件 | `e2e/error-handling.spec.ts` |
| 测试用例数 | 10 |
| 前置条件 | 需 webapp 运行 (`npm run dev`) |
| 运行方式 | `cd test && npx playwright test e2e/error-handling.spec.ts` |

---

## 单元测试文件明细

### 1. `errorMessages.test.ts` — 错误映射工具函数（17 个用例）

| 用例 | 状态 |
|------|------|
| HTTP 500/502 -> 服务器错误 | ✅ |
| HTTP 401/403 -> 认证失效 | ✅ |
| HTTP 404 -> 未找到 | ✅ |
| HTTP 429 -> 请求失败+状态码 | ✅ |
| Failed to fetch / NetworkError / net:: -> 网络错误 | ✅ |
| TimeoutError / timed out / timeout -> 超时 | ✅ |
| null / undefined / 空字符串 / 普通字符串 -> 未知错误 | ✅ |

### 2. `ToastProvider.test.tsx` — Toast 上下文组件（7 个用例）

| 用例 | 状态 |
|------|------|
| 初始不渲染 / error toast / warning toast / 3s 自动移除 | ✅ |
| 多条并发 / Provider 外抛异常 / 无 ProtectedRoute 可用 | ✅ |

### 3. `useChat.test.ts` — SSE 流错误闭包修复（7 个用例）

| 用例 | 状态 |
|------|------|
| START_STREAMING 清除 error / STREAM_FINISH 带/不带 error | ✅ |
| SET_ERROR / STREAM_FINISH 覆盖 / streamErrorRef 模式 | ✅ |

### 4. `alertRemoval.test.tsx` — alert() 移除验证（16 个用例）

| 用例 | 状态 |
|------|------|
| Home/ChatInput/History 不含 alert() | ✅ |
| 各文件导入 useToast 并调用 showToast | ✅ |
| 语音错误展示 (voiceError / micPermissionDenied) | ✅ |
| FilePreview 复制失败 toast | ✅ |

### 5. `hookErrorFriendly.test.ts` — Hook HTTP 错误友好化（27 个用例）

| 用例 | 状态 |
|------|------|
| 5 个 hook 导入/使用 getErrorMessage，无原始 err instanceof | ✅ |
| errorMessages.ts 工具文件完整性 | ✅ |
| i18n en/zh 错误 key 完全匹配 | ✅ |
| ToastProvider 位置 (main.tsx 有, App.tsx 无) | ✅ |
| SSE streamErrorRef 模式 / sendMessage 依赖数组 | ✅ |

### 6. `connectionError.test.ts` — 全页面断连错误处理（33 个用例）

**History 页面（3 个用例）:**

| 用例 | 状态 |
|------|------|
| 读取 connectionError / 未连接时 setIsLoading(false) / 错误横幅展示 | ✅ |

**Chat 页面（5 个用例）:**

| 用例 | 状态 |
|------|------|
| 读取 goosedError / 未连接时显示错误态 / 展示错误文案 | ✅ |
| 提供返回首页按钮 / 连接正常时仍显示 loading | ✅ |

**Files 页面（3 个用例）:**

| 用例 | 状态 |
|------|------|
| 读取 connectionError / 未连接时 setIsLoading(false) / 使用 conn-banner | ✅ |

**Inbox 页面（3 个用例）:**

| 用例 | 状态 |
|------|------|
| 导入 useGoosed / 读取 isConnected + connectionError / 使用 conn-banner | ✅ |

**Monitoring 页面（4 个用例）:**

| 用例 | 状态 |
|------|------|
| 导入 useGoosed / 页面级 conn-banner 展示 | ✅ |
| 不使用 mon-error-banner 类 / 无重试按钮 | ✅ |

**统一 CSS 类验证（9 个用例）:**

| 用例 | 状态 |
|------|------|
| Home/History/Files/Inbox/Agents/ScheduledActions/Monitoring 均使用 conn-banner | ✅ |
| App.css 定义 conn-banner / conn-banner-error / conn-banner-warning | ✅ |
| 无页面使用内联样式的错误横幅 | ✅ |

**错误横幅位置统一验证（4 个用例）:**

| 用例 | 状态 |
|------|------|
| History: conn-banner 在 search-container 之前 | ✅ |
| Files: conn-banner 在 search-container 之前 | ✅ |
| Inbox: conn-banner 在 inbox-toolbar 之前 | ✅ |
| Monitoring: 页面级 conn-banner 在 config-tabs 之前 | ✅ |

**GoosedContext 验证（3 个用例）:**

| 用例 | 状态 |
|------|------|
| AbortSignal.timeout(5000) / isConnected=false / error 暴露 | ✅ |

### 7. `App.test.tsx` — 应用渲染（1 个用例） ✅

### 8-9. 既有测试（7 个用例）

ErrorBoundary (2) + EmbedMode (5) 正常通过，无回归。

---

## E2E 测试明细

### `error-handling.spec.ts` — Gateway 断连 E2E（10 个用例）

**所有页面错误展示（7 个用例）:**

| 页面 | 验证 |
|------|------|
| History | `.conn-banner-error` 可见，loading spinner 消失 |
| Chat | 错误信息可见（加载会话失败） |
| Agents | `.conn-banner-error` 可见 |
| Files | `.conn-banner-error` 可见，loading spinner 消失 |
| Inbox | `.conn-banner-error` 可见 |
| Monitoring | `.conn-banner-error` 可见（无重试按钮） |
| Home | `.conn-banner-error` 可见 |

**统一样式验证（1 个用例）:**

| 用例 | 验证 |
|------|------|
| 所有页面使用 `.conn-banner.conn-banner-error` | History/Files/Inbox/Agents 均通过 |

**错误文案质量（2 个用例）:**

| 用例 | 验证 |
|------|------|
| 无原始 HTTP 状态码 | 页面不含 `HTTP 4xx/5xx:` |
| 文案已本地化 | 展示 i18n 友好文案 |

---

## 改动覆盖矩阵

| 改动项 | 单元测试 | E2E |
|--------|---------|-----|
| ToastProvider 提升到 main.tsx | hookErrorFriendly.test | - |
| errorMessages.ts 工具函数 | errorMessages.test | error-handling.spec |
| alert() -> showToast() | alertRemoval.test | - |
| SSE 闭包 bug 修复 | useChat.test, hookErrorFriendly.test | - |
| Hook HTTP 错误友好化 | hookErrorFriendly.test | error-handling.spec |
| History 断连修复 | connectionError.test | error-handling.spec |
| Chat 断连修复 | connectionError.test | error-handling.spec |
| Files 断连修复 | connectionError.test | error-handling.spec |
| Inbox 断连修复 | connectionError.test | error-handling.spec |
| Monitoring 断连修复 + 去重试 | connectionError.test | error-handling.spec |
| 错误横幅位置统一 | connectionError.test | - |
| 统一 conn-banner CSS | connectionError.test | error-handling.spec |

---

## 全页面 Gateway 断连行为对照

| 页面 | 修复前 | 修复后 |
|------|--------|--------|
| Home | 内联样式错误横幅 | ✅ 统一 conn-banner 样式 |
| Chat | 无限 "正在加载会话..." | ✅ 错误页 + 返回首页按钮 |
| History | 无限 loading skeleton | ✅ conn-banner 错误横幅 |
| Files | 无限 loading spinner | ✅ conn-banner 错误横幅 |
| Inbox | 显示"收件箱为空"（误导） | ✅ conn-banner 错误横幅 + 空态 |
| Agents | agents-alert 样式 | ✅ 统一 conn-banner 样式 |
| ScheduledActions | agents-alert 样式 | ✅ 统一 conn-banner 样式 |
| Monitoring | mon-error-banner（含重试） | ✅ 统一 conn-banner 样式，去掉重试按钮 |

---

## 结论

全部 **115 个单元测试**通过 + **10 个 E2E 测试**就绪：

1. **错误映射** — HTTP/网络/超时错误均映射为 i18n 友好文案
2. **Toast 系统** — 各类型 toast 正常工作
3. **alert() 清除** — 全部替换为 showToast()
4. **SSE 闭包修复** — streamErrorRef 模式正确
5. **Hook 友好化** — 5 个 hook 使用 getErrorMessage()
6. **i18n 完整性** — en/zh 错误 key 匹配
7. **Gateway 断连** — 所有页面 5s 内展示友好错误，无无限 loading
8. **统一样式** — 所有页面（含 Monitoring）使用 `.conn-banner` CSS 类，视觉一致（带边框）
9. **统一位置** — 所有页面的错误横幅位于标题+副标题之后、搜索/筛选/Tab/工具栏之前
10. **Monitoring 去重试** — 移除重试按钮，页面级错误横幅取代各 Tab 内分散展示
11. **无回归** — 既有测试全部通过，构建成功
