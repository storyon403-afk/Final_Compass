# Finals Compass AI V3：真实调用与临时拍题设计

## 1. 版本目标

V3 将 V2 的 Preview Provider 替换为第一批真实外部调用，并形成“题目识别、渐进提示、解答检查”的学习闭环。Provider、凭据和 Skill 仍通过统一抽象组合，业务层不直接依赖供应商请求格式。

当前真实 Provider：

- DeepSeek Chat Completions：文本输入，适合渐进提示、解答检查与概念解释。
- OpenAI Responses：文本与图片输入，适合手机拍题和图片解答检查。

协议依据：[DeepSeek Chat Completion](https://api-docs.deepseek.com/api/create-chat-completion) 与 [OpenAI Responses API](https://platform.openai.com/docs/api-reference/responses)。

Claude 与 Gemini 仍保留 Preview Adapter，界面响应会明确标记 `preview=true`。

## 2. 手机拍题链路

```text
移动端 capture=environment
        ↓
浏览器 Canvas 限边压缩为 JPEG
        ↓
当前 Vue 组件内存中的 data URL
        ↓ 单次 JSON 请求
Spring Boot 校验 MIME、Magic Bytes 与 4MB 上限
        ↓
request-scoped byte[]
        ↓
OpenAI Responses 的 data URL 图片输入
        ↓
finally / AutoCloseable 清零 byte[]
```

该链路不调用课程资料上传接口、不生成数据库附件记录、不写入 `uploads/`，也不调用 Provider 的 Files API。浏览器只在当前组件状态中保存压缩照片；请求成功或用户主动删除时释放。调用失败时照片留在当前页面，便于确认后重试，关闭或刷新页面后自然消失。

输入框同时监听剪贴板 `paste` 事件。剪贴板包含 PNG、JPEG 或 WebP 图片时，阻止图片作为普通文本粘贴，并复用相同的 Canvas 压缩、视觉 Provider 选择和请求级内存链路；普通文字粘贴不受影响。

生产 Nginx 必须对精确路径 `/api/ai/invoke` 设置 `proxy_request_buffering off`，避免较大的请求体落入 Nginx `client_body_temp`。仓库容器配置已经包含该规则；使用宿主机 Nginx 时需要同步配置。

外部 Provider 会接收到图片内容，其数据保留边界由对应 Provider 的账户配置和服务条款决定。因此界面只能承诺“不保存到 Finals Compass 服务器硬盘”，不能表述为“任何第三方均不保留”。

## 3. Provider 抽象

`AiProviderAdapter.AiProviderRequest` 包含：

- 模型名称；
- Provider 无关的 `ExecutionPlan`；
- 可选的 `TransientImage`。

DeepSeek Adapter 明确拒绝原始图片。OpenAI Adapter 使用 Responses API 的 `input_text` 与 `input_image`，图片直接采用请求级 Base64 data URL，不创建远端文件。

## 4. Skill 闭环

- `math-problem-image-analysis`：转写题目、识别条件与不确定区域。
- `progressive-hint`：默认只给当前最小必要提示。
- `solution-review`：保留正确步骤并定位第一处错误。

V3 仍按一次请求执行一个主 Skill。多轮 Context 和自动顺序编排多个 Skill 属于后续版本，不能在响应中伪装为已经执行。

## 5. 配额与审计

每次外部调用前由 `AiUsageGuardService` 执行：

- 每名用户默认每分钟最多 6 次调用；
- 平台 Key 默认每天最多 20 次；
- 平台 Key 默认每用户每月最多累计 100000 Token；
- BYOK 不消耗平台 Token 额度，但仍受分钟级防刷限制。

限制值通过 `AI_CALLS_PER_MINUTE`、`AI_PLATFORM_DAILY_CALLS` 和 `AI_PLATFORM_MONTHLY_TOKENS` 配置。调用结果继续写入 `ai_usage_log`，但不保存问题正文、图片、API Key 或 Provider 原始错误正文。

## 6. 验证范围

- Java 单元测试验证临时图片类型检查、内存解码和关闭后字节清零。
- 原有 Agent 路由、Skill 规划、凭据清零和附件 Worker 测试继续执行。
- Vue 生产构建验证相机入口与请求字段。
- 真实 Provider 验收需要各自有效 API Key，不在自动测试或仓库中保存凭据。
