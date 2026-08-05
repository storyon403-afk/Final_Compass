# FinalsCompass AI V4：Gemini 视觉与 DeepSeek 解题编排

## 1. 目标

V4 将原有 `math-problem-image-analysis` 升级为后端封装的两阶段 Skill。用户只需上传、拍摄或粘贴题目图片并描述目标，不需要理解模型与 Skill 的内部组合。

```text
图片 + 用户要求
      │
      ▼
输入校验与临时图片内存
      │
      ▼
Gemini Vision：可靠转写 + 意图分类
      │  只输出文本，不直接作答
      ▼
Skill 路由：完整解题 / 分步提示 / 解答检查 / 概念解释
      │
      ▼
DeepSeek V4 Flash：生成最终学习回答
      │
      ▼
统一审计与临时数据销毁
```

## 2. 用户意图

用户要求优先于视觉模型的分类结果：

| 用户表达 | 最终 Skill |
|---|---|
| 解题、完整解答、求答案 | `complete-solution` |
| 提示、引导、不要答案 | `progressive-hint` |
| 批改、检查、哪里错 | `solution-review` |
| 解释、为什么、概念 | `concept-explanation` |
| 没有明确说明 | `complete-solution` |

Gemini 的识别文本会以“不可信题目数据”进入第二阶段。图片里即使出现“忽略系统规则”等文字，也不能改变 Skill、权限或工具范围。

## 3. 凭据和模型配置

管理员需要在 AI 管理菜单分别保存并启用：

- `gemini`：视觉识别模型，当前建议使用生产级多模态模型 `gemini-3.6-flash`；
- `deepseek`：最终分析模型，配置 `deepseek-v4-flash`。

平台资格用户可使用这一组合。DeepSeek BYOK 用户上传图片时，视觉阶段仍会使用平台 Gemini，因此也需要具有当月平台视觉资格；纯文本 BYOK 不受此约束。临时或已保存的 DeepSeek BYOK 默认选择 `deepseek-v4-flash`，Key 本身不决定模型。

## 4. 数据生命周期

- 浏览器先压缩图片，再以请求体传入；
- 后端只在请求期内持有图片字节，不写业务上传目录；
- Gemini 使用 `inlineData` 接收图片，不调用文件持久化接口；
- DeepSeek 只收到 Gemini 转写文本，不收到原图；
- 请求结束时图片字节数组和两套解密后的 Key 都会被覆盖清理；
- 审计表只保存 Provider、模型、Skill、Token 数、状态和 `traceId`，不保存图片、Key 或完整题面。

## 5. 主要代码位置

- `GeminiGenerateContentAdapter`：Gemini HTTP 协议和多模态响应解析；
- `AiVisionProblemPipeline`：两阶段编排、意图映射和数据边界；
- `AiSkillConfiguration`：`complete-solution` 等 Skill 契约；
- `AiAnalysisService`：统一限流、审计和调用入口；
- `AiCredentialResolver`：平台 Gemini 与 DeepSeek 平台/BYOK 凭据解析。

## 6. 失败边界

Gemini 未配置、图片识别失败或 DeepSeek 调用失败时，整次请求返回失败，不用猜测性文本替代真实识别结果。审计记录标记为 `FAILED`。当前版本不自动重试，避免一次用户操作在供应商拥塞时产生不可控费用；后续可按错误码增加最多一次、带退避的重试。
