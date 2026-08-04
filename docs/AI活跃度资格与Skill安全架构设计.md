# AI 活跃度资格、BYOK 与 Skill 安全架构设计

> 分支：`feature/ai-analysis`  
> 对应议题：[#12 是否支持用户自带 API Key（BYOK）模式，以及 AI Skill 调用安全方案设计](https://github.com/storyon403-afk/Final_Compass/issues/12)  
> 状态：第一版可运行预实现；真实模型 Provider 与业务 Skill 尚未接入。

## 1. 目标与非目标

这一版解决四件事：

1. 以不可重复的事件账本记录用户贡献。
2. 按上一个自然月积分选出前 20 名，授予下一个自然月的平台 AI 资格。
3. 统一平台 API Key、用户保存型 BYOK 和单次临时 BYOK 的选择与审计。
4. 把 AI Provider 与 AI Skill 分开抽象，后续接入自定义 Skill 时不改认证、密钥和审计主链。

这一版不做：

- 不调用真实 DeepSeek、OpenAI 或 Claude。
- 不承诺 Token 计费、并发限流和模型输出质量。
- 不允许 Skill 直接执行数据库写入、任意 HTTP、Shell 或文件操作。
- 不把平台或用户 API Key 写入源码、日志、错误信息和前端持久存储。

当前 `PreviewAiProviderAdapter` 只返回“安全预检完成”的预览结果。真实模型接入时为各厂商新增 Provider Adapter，不应把 HTTP 调用散落到 Controller 或 Skill 中。

## 2. 总体调用链

```text
Vue AI 学习分析页
  │
  ├── GET /api/ai/dashboard
  │     ├── 当前月积分与 Top 20
  │     ├── 本月平台 AI 资格
  │     ├── 已启用 Provider（不含密钥）
  │     └── 已保存 BYOK 指纹（不含密钥）
  │
  └── POST /api/ai/invoke
        ├── AuthService：确认登录用户
        ├── AiSkillRegistry：只允许已注册 Skill
        ├── AiCredentialResolver：检查资格并选择短生命周期凭据
        ├── AiSecretCipher：必要时短暂解密
        ├── AiProviderGateway：统一模型调用边界
        └── ai_usage_log：记录元数据，不记录 Key 和完整输入
```

`AiSkill` 不接触 API Key；`AiProviderGateway` 不决定用户是否有权限；Controller 不实现加密或供应商逻辑。各层职责单一，方便以后替换。

## 3. 积分规则

| 行为 | 分数 | 去重依据 | 触发位置 |
|---|---:|---|---|
| 当日首次成功登录 | +1 | 用户 + `DAILY_LOGIN` + 日期 | `AuthService.login` |
| 成功提交一份资料 | +2 | 用户 + 资料存储 UUID | `CircleController.upload` |
| 资料审核通过 | +5 | 用户 + `RESOURCE` + 资料 ID | `SystemController.moderate` |
| 老师评价/论坛讨论审核通过 | +2 | 用户 + `DISCUSSION` + 帖子 ID | `SystemController.moderate` |
| 指南参考内容审核通过 | +2 | 用户 + `GUIDE_SUBMISSION` + 提交 ID | `SystemController.moderate` |

积分使用 `activity_event` 追加事件，不在 `app_user` 上维护一个可以随意加减的总数字。

这里需要区分两种限制：

- **只有登录按天限分**：同一个用户一天登录一次或多次，都只获得 1 分。
- **内容贡献不按天限分**：一天提交多份不同资料，每份都获得 2 分；每份资料审核通过后分别再获得 5 分；多条不同的论坛内容或指南内容审核通过后，每条分别获得 2 分。
- “去重”只用于阻止同一份资料或同一条内容因为重复请求、重复审核而再次加分，不会阻止不同内容继续累计。

### 3.1 为什么使用事件账本

- 唯一键从数据库层阻止同一条业务记录重复计分，不限制不同内容的累计次数。
- 能解释“某人的积分从哪里来”。
- 规则变化时可以按事件重新统计。
- 审核拒绝不加分；对同一记录重复点击批准也不能重复加分。
- 登录一天无论建立多少次会话都只有一个 `DAY + 日期` 事件。

### 3.2 当前还需要补充的反作弊措施

唯一事件只能阻止“同一事件重复计分”，不能阻止批量提交低质量内容。真实上线前还应增加：

- 单日资料提交积分上限。
- 同一内容哈希或近似重复检测。
- 被管理员撤销、删除或判定作弊后的冲正事件，不能直接删除历史。
- 管理员积分审计页和异常速率告警。
- 排行榜只显示匿名昵称或用户自选公开名，当前预览使用 `display_name`，上线前需要隐私确认。

## 4. 月度 Top 20 与免费资格

### 4.1 时间窗口

- 排行榜：当前自然月 1 日 00:00 到下月 1 日 00:00。
- 资格来源：上一个完整自然月。
- 资格有效期：当前自然月。
- 人数：最多 20 人。

`ai_monthly_entitlement` 保存月度快照，而不是每次调用都重新算上月排名。这样上月数据后来发生变化时，本月已经发放的资格不会无意漂移。

当前预实现会在第一次读取 AI Dashboard 时懒生成本月快照。正式上线建议在每月 1 日使用受监控的定时任务生成，并增加 `entitlement_batch` 批次表，明确记录“即使上月无人上榜也已经完成快照”。

### 4.2 同分排序

当前按以下顺序确定位置：

1. 总分降序。
2. 达成积分的最早事件时间。
3. 用户 ID。

这样最多严格授予 20 人，不会因为第 20 名同分突然授予超过 20 人。若产品希望同分全部获得资格，需要单独评估预算。

## 5. 数据库设计

V16 新增五张表。

### 5.1 `activity_event`

贡献积分事实表：

```text
user_id + event_type + source_type + source_ref  唯一
points                                             本次积分
event_date / occurred_at                           统计日期与审计时间
```

不保存可修改总分，总分使用 `SUM(points)` 聚合。

### 5.2 `ai_monthly_entitlement`

保存“哪个月、哪个用户、依据哪个月、当时多少分、第几名”。主键是 `entitlement_month + user_id`。

### 5.3 `user_ai_secret`

每个用户、每个 Provider 最多保存一个 Key：

- `encrypted_key`：AES-GCM 密文。
- `encryption_iv`：每次保存随机生成的 12 字节 IV。
- `key_fingerprint`：SHA-256 截断指纹，只用于辨认 Key 是否变化。
- `consent_version` / `consented_at`：记录用户明确同意的版本和时间。
- 不保存明文，不提供管理员读取明文的 API。

### 5.4 `platform_ai_config`

管理员设置的平台 Provider、模型、加密 Key 和启用状态。API 响应只返回 Provider、模型、指纹和时间，不返回密文、IV 或明文。

### 5.5 `ai_usage_log`

记录用户、Provider、模型、Skill、凭据来源、状态、输入/输出计量和 trace ID。

明确不记录：

- API Key。
- Authorization Header。
- 完整提示词与模型完整响应。
- 用户上传的课程资料正文。

以后若业务确实需要保留对话，必须另建有保留期限、删除权和隐私同意的数据模型，不能偷偷塞进使用日志。

## 6. BYOK 两种模式

### 6.1 不保存：`EPHEMERAL_BYOK`

```text
浏览器内存中的 Key
→ 随单次 HTTPS 请求进入后端
→ char[] 在 Provider 调用期间短暂存在
→ finally 覆写 char[]
→ 不进入数据库
```

前端收到请求结果后清空输入框。Java、浏览器和网络栈可能仍产生不可控的短期内存副本，因此“立即释放”应理解为应用层不持久化和主动清理，而不是物理上证明每个字节瞬间消失。

### 6.2 用户明确同意保存：`STORED_BYOK`

用户必须勾选同意，后端才接受保存请求。保存时使用 AES-256-GCM；调用时短暂解密，完成后覆盖字符数组。

产品提示建议使用：

> Key 将采用 AES-256-GCM 加密保存；管理员界面无法查看原文。服务器仅在代表你发起模型请求时短暂解密，你可以随时删除。

不建议使用“除了您自己无人知道您的 Key”这种绝对表述。原因是持有数据库和服务器加密主密钥的最高权限运维者在技术上具备解密能力。AES 加密主要保护数据库备份泄露、普通管理员误看和非授权应用查询，不等于对服务器所有者实现零知识。

## 7. 加密主密钥

环境变量：

```env
AI_SECRET_ENCRYPTION_KEY=Base64编码的32个随机字节
```

生成方式：

```bash
openssl rand -base64 32
```

要求：

- 只保存在服务器 root-only 私有配置或专用 Secret Manager。
- 不提交 Git，不写 Wiki，不复制进数据库。
- 备份密文时必须以独立安全渠道备份主密钥。
- 主密钥丢失后，已保存 Key 无法恢复，只能让管理员和用户重新录入。
- 不能直接替换主密钥；轮换需要“旧密钥解密 → 新密钥重新加密”的受控任务和审计。

未配置主密钥时，系统允许临时 BYOK，但拒绝保存平台 Key和用户 Key。

## 8. Provider 抽象

统一接口：

```java
public interface AiProviderGateway {
    AiProviderResult invoke(AiProviderRequest request, char[] apiKey);
}
```

后续建议为每家供应商建立 adapter，例如：

```text
DeepSeekGateway
OpenAiCompatibleGateway
ClaudeGateway
```

真实实现必须统一处理：

- HTTPS、连接/读取超时和最大响应大小。
- Provider 域名白名单，禁止用户提交任意 Base URL 造成 SSRF。
- 429、5xx、超时和熔断。
- API Key Header 脱敏。
- Token 计量和月度平台预算。
- 流式响应取消与用户退出。

第一版不开放“用户自定义任意 API 地址”。BYOK 只允许系统支持的 Provider，否则用户提供的 URL 可能访问内网或云元数据地址。

## 9. Skill 抽象与安全

Skill 接口：

```java
public interface AiSkill {
    String id();
    String displayName();
    String description();
    int maxInputLength();
    void validate(String input);
}
```

你后续设计 Skill 时，实现 `AiSkill` 并注册为 Spring Bean，`AiSkillRegistry` 会自动暴露元数据并只允许调用已注册 ID。

### 9.1 Skill 不应拥有的能力

- 不接收或读取 API Key。
- 不拼接任意 Provider URL。
- 不直接执行 Shell。
- 不读取任意服务器文件路径。
- 不直接运行模型生成的 SQL。
- 不绕过当前用户权限读取管理员或其他用户资料。

### 9.2 未来工具调用建议

如果 Skill 需要“查课程、查资料、生成计划”等工具，应使用显式工具注册表：

```text
skill_id
→ allowed_tool_ids
→ 每个工具独立参数 Schema
→ 服务端再次鉴权
→ 只读/写入能力分类
→ 超时、结果条数、调用次数上限
→ tool_call_audit
```

模型只能提出结构化工具调用建议，服务端必须验证 Skill 白名单、用户权限、参数和资源所有权后才能执行。高风险写操作应要求二次人工确认。

### 9.3 Prompt Injection 边界

课程资料和论坛内容都属于不可信输入。Skill 系统提示应明确：资料中的“忽略规则、输出密钥、调用工具”等文字只是待分析内容。工具参数不能直接采用模型生成值，必须服务端验证。

## 10. API 设计

| 方法 | 路径 | 权限 | 作用 |
|---|---|---|---|
| GET | `/api/ai/dashboard` | 登录用户 | 排行榜、积分、资格、Skill 与 Key 摘要 |
| PUT | `/api/ai/byok` | 当前用户 | 明确同意后加密保存自己的 Key |
| DELETE | `/api/ai/byok/{provider}` | 当前用户 | 删除自己的已保存 Key |
| PUT | `/api/ai/admin/platform-key` | 管理员 | 配置平台 Key、模型和启用状态 |
| POST | `/api/ai/invoke` | 登录用户 | 统一选择 Skill 和凭据来源进行调用 |

所有接口经过现有 `AuthenticationInterceptor`。数据库查询始终包含当前 `user_id`，用户不能读取或删除别人的 Key。

## 11. UI 设计

主导航在“课程导航”和“英语等级考试收录”旁增加“AI 学习分析”。页面采用现有浅色、细边框和低饱和风格，标题与标签使用楷体/手写体。

页面包含：

- 当前积分和本月 Top 20。
- 平台 AI、已保存 BYOK、临时 BYOK 三种通道。
- 保存同意复选框和准确的加密说明。
- Skill 扩展坞。
- 管理员平台 Key 配置区。
- 当前 Provider 只做调用预览，不伪造模型结果。

## 12. 与其他功能分支合并

本模块尽量通过新增文件隔离：

- 新 Controller、Service、`ai/` 包和 Vue View。
- 对既有文件只做小型接入：登录积分、上传积分、审核积分、路由、导航、API 导出和样式追加。
- 不修改 V1～V15。

并行分支也可能创建 `V16`。合并前必须先更新 `main`，检查最新 Flyway 版本，并把本迁移重命名为下一个未使用版本；已经在共享或生产数据库执行后不能再重命名或改内容。

`index.html` 在进入本任务前已有未提交改动，本分支没有修改或覆盖它。

## 13. 本地验证记录

2026-08-02 已完成：

- `npm run build` 成功。
- `mvn test` 成功，原有 4 个测试通过。
- 新增 AES-GCM 单元测试。
- 本地数据库迁移前备份：`/tmp/finals_compass_before_ai_v16_20260802.sql`。
- 本地 Flyway 成功迁移到 V16，并成功 validate 16 个迁移。
- 临时管理员完成登录、Dashboard、平台 Key 保存、保存型 BYOK、临时 BYOK 和两个预览调用。
- 数据库确认平台测试 Key 不是明文，使用日志 2 条，每日登录事件 1 条。
- 临时管理员、测试 Key、日志和积分事件已清理；保留 V16 表结构。

本机 MySQL 为 9.7，高于当前 Flyway 明确测试的 MySQL 8.1，因此出现兼容性提醒；迁移实际成功。生产仍应使用项目声明的 MySQL 8.0。

## 14. 下一步

1. 你确定第一批真实 Skill 的输入、输出和允许工具。
2. 选择首个 Provider，并实现对应 `AiProviderGateway` adapter。
3. 增加平台月度 Token 预算、单用户限额和并发限制。
4. 增加每月资格批次任务、冲正事件和管理员积分审计。
5. 增加 Provider Mock 集成测试、权限测试和日志泄密测试。
6. 完成隐私说明与用户删除 Key 的验收后，再考虑部署。
# AI 界面与附件解析边界（V2）

AI 主页面采用对话式布局，不向普通用户展示 Skill Registry、Provider Adapter 等内部扩展结构。用户只需要直接提出问题；模块规则、排行榜、凭据来源和管理员平台配置收纳在右上角三横线菜单中。Skill ID、输入上限和能力声明属于开发文档与后端契约，不作为主页面信息架构。

界面提供图片、文档和音频入口。附件会真实上传到 Spring Boot，再交给隔离的 MarkItDown Worker 转换为 Markdown；普通文本默认使用 `auto` 进入 V2 Intent Router，图片与资料附件分别进入视觉和摘要 Skill。当前 Provider Adapter 仍是 Preview，因此页面不得把预览响应描述为真实外部模型分析。

## MarkItDown 接入评估

Microsoft MarkItDown 是 MIT 许可的 Python 3.10+ 文件转 Markdown 工具，适合把 PDF、Word、PowerPoint、Excel、HTML、文本、图片 OCR 和音频转写结果转换为更适合 LLM 使用的 Markdown。它可以用于 Final Compass 后续的附件标准化，但不应直接嵌入 Vue，也不建议让 Spring Boot 通过不受约束的命令行处理用户路径。

推荐架构：

```text
Vue 上传附件
  -> Spring Boot 校验身份、大小、扩展名和实际 MIME
  -> 保存到每次请求独立的临时目录
  -> 隔离的 MarkItDown Worker 使用 convert_stream/convert_local
  -> 返回 Markdown、页码和解析元数据
  -> Spring Boot 再按 Skill 与权限调用外部 AI API
  -> 请求结束按保留策略删除原文件和中间结果
```

安全要求：

- Worker 使用独立低权限系统用户或容器，不能读取应用配置、数据库凭据和上传目录以外的文件。
- 禁止把用户提供的任意 URL 或文件路径直接交给 MarkItDown；官方明确说明它会以当前进程权限执行 I/O。
- 默认禁用第三方插件，只安装确实需要的格式依赖。
- 设置文件大小、页数、解压文件数、解析时间和输出字符数上限，防止 ZIP 炸弹和资源耗尽。
- 文件名只作展示，存储使用服务器生成的随机名称。
- OCR 或音频转写若需要云 API，必须继续经过 Credential Resolver，不能让转换 Worker读取平台或用户 Key。
- 解析后的 Markdown属于不可信数据，不能作为系统提示词；后续 MCP/Agent 接入时仍需防 Prompt Injection。
- 记录 traceId、文件类型、大小、耗时和状态，不记录 API Key，也不默认长期保存原文件内容。

MarkItDown 已作为项目内的独立 Python Worker 接入，由 Docker Compose 与 Spring Boot 一起部署。Worker 不接触数据库或 AI Key，具体格式、资源限制和运行方式见《MarkItDown 内置附件解析与运行指南》。V2 Skill 编排见《FinalsCompass AI V2 Skill 编排设计与扩展指南》。
