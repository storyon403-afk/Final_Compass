# Finals Compass 安全审查报告（2026-08-14）

> **报告性质**：本报告为**只读静态安全审查**产物，未修改任何源代码或源文件；未进行动态渗透测试，也未执行依赖项 CVE/SCA 比对。
> **审查基线**：main @ ff9ea17（2026-08-13）+ 工作区未提交改动（V58、AiAnalysis/管理后台），Flyway 最新迁移 V58。
> **与历史报告的关系**：docs/安全审计报告.md 是 2026-08-09 的审计快照（V54 之前），**不能作为当前安全结论**。本报告是当前代码的独立复审：第 5 节为低危与提示，第 6 节为相对旧报告的已修复/仍存在/新增对照，第 7 节为已确认的正面设计基线。
> **配套**：架构背景见 docs/现阶段完整架构图与说明.md。
> **分级**：高 / 中 / 低 / 提示；分类依据 CWE 与 OWASP Top 10 (2021)。

---

## 0. 审查范围与方法

- **后端**：config 包 6 类（拦截器/过滤器/WebSocket）；22 个 Controller；service 层认证/验证码/账号发放/邮件/加密（AiSecretCipher、MailSecretCipher）；ai/ 目录全部 78 个运行时类；application.yml、pom.xml。
- **前端**：src 全部（api.js、App.vue、views、components，含 SafeHtml/SafeMarkdown/PdfViewer）。
- **外围**：browser-extension（manifest/background/bridge/popup）；scripts（hermes-agent.mjs、mock-agent.mjs、public.sh）；services（markitdown-worker、pdf-renderer）；deploy（docker-compose、nginx、Dockerfile、.env.example、server-fetch.sh）；CI（ci.yml）。
- **方法**：逐文件通读 + 全仓 grep 验证（鉴权豁免、SQL 拼接、Redis 限流接线、敏感字段落库、出站 URL 校验、文件路径校验）。所有结论附 文件:行号 证据。

---

## 1. 结论摘要与统计

| 统计项 | 数量 |
| --- | --- |
| 高危 | 5 |
| 中危 | 14 |
| 低危 | 14 |
| 提示级观察 | 3 |
| **合计** | **36** |

最重要的三件事：

1. **AI 用量与成本守卫整体失效（H01）**：限流服务是死代码，平台 Key 没有任何频率/日/月限制，可被任意登录用户刷光额度。
2. **认证链缺限速（H02）**：登录与管理员的“密码二次鉴权”均无防爆破，弱口令即可接管管理员 → SMTP 凭据、批量发号、平台 Key 全部沦陷。
3. **浏览器控制面令牌暴露面过大（H03/H04）**：WebSocket 令牌走 URL query 且 Origin 通配；扩展持有全域 https 权限。任一令牌泄露即可驱动用户浏览器（导航/点击/输入/截图）。

安全基线的正面部分（第 5 节）整体扎实：AES-256-GCM 凭据加密、BCrypt、参数化 SQL、HMAC 验证码、Worker 多重守卫、前端 DOMPurify 全覆盖、回调 per-run 令牌、路径穿越防护均经源码确认。

---

## 2. 发现汇总表

| 编号 | 严重度 | 标题 | 位置 | 类型 |
| --- | --- | --- | --- | --- |
| H01 | 高 | AI 用量/成本限制整体失效（守卫死代码） | service/AiUsageGuardService.java（全仓无调用） | 配额绕过/资源滥用 |
| H02 | 高 | 登录与管理员二次鉴权无防爆破 | AuthService.java:35-56；MailAdminService.java:267-270 | 暴力破解 |
| H03 | 高 | 浏览器桥 WS：令牌入 URL query + Origin 通配 | BrowserBridgeWebSocketConfig.java:61-75 | 令牌泄露 |
| H04 | 高 | 扩展全域 https://*/* 权限 + 任意 selector 命令 | browser-extension/manifest.json:16；background.js:306-431 | 权限过宽 |
| H05 | 高 | 后端容器以 root 运行、jar 无 chown | deploy/backend/Dockerfile:8-14 | 容器加固缺失 |
| M01 | 中 | 会话 Cookie 无 Secure | AuthController.java:78-88 | 明文传输 |
| M02 | 中 | 盲信 X-Forwarded-For 绕过限流 | AuthController.java:90-95 | IP 伪造 |
| M03 | 中 | Agent Gateway URL 取自 DB 且无主机白名单 | AiRuntimeDispatchService.java:101-107 | SSRF |
| M04 | 中 | 出站 HTTPS 任意 host、无内网黑名单 | JdkRuntimeHttpTransport.java:78-87 | SSRF/DNS 重绑定 |
| M05 | 中 | 回调 token 无过期/轮换、状态机不严格 | AiRuntimeDispatchService.java:193-223 | 凭证生命周期 |
| M06 | 中 | AGENT 路径临时 BYOK 明文传给网关 | AiRuntimeDispatchService.java:113 | 密钥出域 |
| M07 | 中 | RAG 内容拼接 system prompt（提示注入面） | AiChatService.java:337-351 | 提示注入 |
| M08 | 中 | 修改密码后旧会话不失效 | AuthService.java:93-106 | 会话吊销缺失 |
| M09 | 中 | OAuth 回调可被 CSRF 诱导换绑发件邮箱 | MicrosoftMailController.java:44-55 | CSRF |
| M10 | 中 | 上传仅扩展名白名单、客户端 MIME 原样输出 | CircleController.java:181-210,104-109 | 内容校验不足 |
| M11 | 中 | MySQL useSSL=false + allowPublicKeyRetrieval=true | deploy/docker-compose.yml:28 | 明文内网链路 |
| M12 | 中 | 加解密密钥默认空值仍可启动 | application.yml:31,36,42 | 弱默认配置 |
| M13 | 中 | Nginx 纯 HTTP、无安全响应头、server_tokens | deploy/nginx/finals-compass.conf | 传输/信息泄露 |
| M14 | 中 | 本机 Agent Gateway 无鉴权 + callbackBase 任意 | scripts/hermes-agent.mjs:43-59,149-156,259 | 本地攻击面 |
| L01 | 低 | 会话令牌存 localStorage | src/api.js:4-9,44-47 | 敏感信息存储 |
| L02 | 低 | 会话 7 天、无轮换/吊销列表 | AuthService.java:47-52 | 会话管理 |
| L03 | 低 | AiCenter HTML 原文入库、依赖前端净化 | AiCenterContentService.java:66-77 | 存储型 XSS 残留 |
| L04 | 低 | 扩展研究文本入 chrome.storage 无 TTL | browser-extension/background.js（lastStatus 持久化） | 本地明文 |
| L05 | 低 | pdf-renderer 任意 HTML 渲染（预留未接线） | services/pdf-renderer/server.mjs:15-43 | SSRF 面 |
| L06 | 低 | 收件人邮箱 SHA-256 无盐 | DynamicMailService.java:179-188 | 字典还原 |
| L07 | 低 | 匿名昵称无唯一约束 | AnonymousIdentityService.java:13-27 | 身份混淆 |
| L08 | 低 | 异常消息原样回显 | ApiExceptionHandler.java:18-24 | 信息泄露 |
| L09 | 低 | health 匿名且触发 DB 查询 | SystemController.java:36-40 | 探活信息 |
| L10 | 低 | CET 真题/答案仅登录即可下载 | CetController.java:88-121 | 业务授权 |
| L11 | 低 | STATIC_LOCATIONS 可被环境变量改为 file 路径 | application.yml:17 | 配置面 |
| L12 | 低 | 产物 contentType 未白名单、文件名未转义 | AiRuntimeDispatchController.java:66-72 | 响应头注入 |
| L13 | 低 | CI 无依赖漏洞扫描、前端依赖浮动版本 | .github/workflows/ci.yml；package.json:14-20 | 供应链 |
| L14 | 低 | Trace/反馈存用户明文，无脱敏/保留期 | AiChatService.java:159；AiFeedbackService.java:180 | 敏感数据留存 |

---

## 3. 高危发现

### H01 · AI 用量/成本限制整体失效（守卫死代码）

- **证据**：service/AiUsageGuardService.java:16 定义了 per-minute/日/月三层限流，但全仓 grep（backend/src/main/java）**没有任何类注入或调用它**；AiChatService.answer（chat/AiChatService.java:110）、AiVisionService.analyze（service/AiVisionService.java:21）、AiRuntimeDispatchService.start（agent/AiRuntimeDispatchService.java:47）三个入口均不执行用量检查；ai_usage_log（V17）**没有任何 INSERT**，月度 token 统计（AiUsageGuardService.java:49-63）永远为 0。
- **影响**：application.yml:44-46 配置的 AI_CALLS_PER_MINUTE=6、平台日 20 次、月 100000 token 全部形同虚设；V58 的 internal_test_open 打开后，任意登录用户可无限调用平台付费 Key（CHAT/AGENT/视觉/审核/embedding），造成直接经济损失与资源耗尽。
- **放大因素**：AiChatService.java:132 每次 chat answer 都触发平台 embedding（RuntimeEmbeddingGateway → AiCredentialResolver.resolvePlatformService，AiCredentialResolver.java:161 注释“调用者须为可信组件”但实际由任意用户请求触发）——即使平台聊天模型不可用，embedding 额度也可被刷空。
- **建议**：把 guard 接入 CHAT/AGENT/VISION 入口（含 embedding 计费），或删除死代码并另建统一计量；ai_usage_log 恢复写入。

### H02 · 登录与管理员二次鉴权无防爆破

- **证据**：/api/auth/login 匿名开放（WebConfig.java:29），AuthService.login（:35-56）只做 BCrypt 比对，**无失败计数、无锁定、无验证码、无 IP/账号限流**；管理员敏感操作（SMTP 保存/测试、批量发号、Microsoft 解绑）用“管理员密码”明文请求体二次鉴权（MailAdminService.java:267-270、MailAdminController.java:32-61），同一无防护面。
- **影响**：攻击者可对登录接口无限尝试（枚举账号 + 爆破密码）；管理员弱口令一旦命中即可读取加密 SMTP 凭据与平台 Key 配置、批量发放账号、控制全站内容。管理员密码还经 HTTP 明文传输（M13 叠加）。
- **建议**：登录与二次鉴权共用一个 Redis 失败计数 + 指数退避锁定（与 RedisVerificationService 的 Lua 限流同模式）；管理员敏感操作改会话内 MFA/短期 PIN；生产强制 HTTPS。

### H03 · 浏览器桥 WebSocket：令牌入 URL query + Origin 通配

- **证据**：BrowserBridgeWebSocketConfig.java:61-73 从 `?token=` 取登录令牌，:75 `setAllowedOriginPatterns("*")`；扩展侧 background.js:235,264 将 token 拼进 ws:// URL。
- **影响**：令牌会进入浏览器历史、代理/网关访问日志、Referer 与转发日志；任意 Origin 的页面都能发起握手。令牌 = 完整登录会话（7 天），泄露即等于账户接管；再叠加 H04，接管者可驱动用户浏览器执行 navigate/click/type/screenshot（background.js:306-431），在用户已登录的任意网站（含 Kimi/DeepSeek/通义）上操作。
- **建议**：改为 `Sec-WebSocket-Protocol` 或首帧消息携带令牌；Origin 白名单收敛到平台域名与扩展 id；浏览器桥使用与登录会话隔离的、短时、scope 受限的专用 token（复用登录 token 的现状见 scripts/test-e2e.md:47）。

### H04 · 扩展全域 https://*/* 权限 + 任意 selector 命令

- **证据**：manifest.json:16 host_permissions 含 `https://*/*`（与逐站条目重复）；background.js:403-424 执行 click/type 时 selector/文本来自后端消息、无页面白名单校验；bridge.js:3 window.postMessage 目标为 `*`。navigate 命令限定 `^https?:` 协议（background.js:330，正面），但 click/type/screenshot 无站点约束。
- **影响**：扩展对用户访问的任意 HTTPS 站点具备注入与读取能力；一旦扩展供应链或消息链被污染，可窃取任意站点内容与凭据。content_scripts 仅匹配 localhost:5173（正面收敛），但 chrome.scripting 注入面由 host_permissions 决定。
- **建议**：删除 `https://*/*`，仅保留编排所需的逐站条目；bridge 指定 targetOrigin；对 click/type 命令做站点+selector 白名单；通用抓取迁至受控后端代理。

### H05 · 后端容器以 root 运行、jar 无 chown

- **证据**：deploy/backend/Dockerfile:8-14 最终镜像未设 USER，COPY 未带 `--chown`；compose 中 backend 挂载 uploads 卷（docker-compose.yml:55-56），且未加 no-new-privileges（redis/worker 有，backend 无）。
- **影响**：文档解析链（POI 5.4.1 等）、SSE/虚拟线程等任一环节出现 RCE 类漏洞时，进程以容器 root 运行，可写挂载卷、进一步横向。历史报告 H01 未修复。
- **建议**：创建非 root 用户并 `USER` 切换；`COPY --chown`；backend 服务补充 `security_opt: no-new-privileges:true` 与 read_only（uploads 卷单独 rw）。

---

## 4. 中危发现

### M01 · 会话 Cookie 无 Secure
AuthController.java:78-88 仅 httpOnly + SameSite=Lax。叠加 M13（Nginx 纯 HTTP），令牌可被中间人窃取。建议：生产强制 `.secure(true)` + HSTS。

### M02 · 盲信 X-Forwarded-For
AuthController.java:90-95 取 XFF 首项作为限流键（RedisVerificationService.java:56），可直接伪造绕过 IP 频控、嫁祸审计。建议：仅受信代理链内采信。

### M03 · Agent Gateway URL 无主机白名单（SSRF）
AiRuntimeDispatchService.java:101-107、:128、:317 从 ai_agent_definition 读 gateway_url 直接 URI.create 出站，无协议/主机校验。该表当前无写入 API（仅迁移种子），风险降为“DB 写权限者”场景；但 V44 种子表结构允许任意 URL。建议：出站前校验 scheme=http(s) 且 host ∈ 回环/内网白名单。

### M04 · 出站 HTTPS 任意 host、无内网黑名单
JdkRuntimeHttpTransport.java:78-87 放行所有 https（http 仅回环，正面）；无内网 IP/云元数据地址黑名单、无 DNS 重绑定防护（HttpClient 不跟随重定向，正面）。Provider endpoint 仅迁移可写，但 MCP STREAMABLE_HTTP 的 allowedHosts 由管理员自填（StreamableHttpRuntimeMcpTransport.java:283-288）。建议：出站统一走 egress 代理 + 解析后 IP 校验。

### M05 · 回调 token 无过期/轮换、状态机不严格
AiRuntimeDispatchService.java:60,171-182：callback_token 恒定到任务终态，无过期/轮换；updateStatus（:193-223）未校验状态合法序列（FAILED 可被改回 COMPLETED，CANCELLED 仅被单向忽略）。建议：终态后作废 token；按 RuntimeTraceStateMachine 同款状态机校验。

### M06 · AGENT 路径临时 BYOK 明文传给网关
AiRuntimeDispatchService.java:113 把 ephemeralApiKey 直接放入发往网关的 JSON body。默认网关为回环（风险低），但若 gateway_url/env 指向非本机则 Key 出域。建议：网关本机内改走进程间安全通道，或回传式凭据引用（网关拿一次性指针回后端取 Key）。

### M07 · RAG 内容拼接 system prompt（提示注入面）
AiChatService.java:337-351 把知识库检索内容直接拼入 system instruction；KnowledgeAdminController 允许管理员入库任意文档（AiDocumentConversionService 白名单含 html/htm）。库内恶意/被投毒文档可注入指令，影响所有使用 PUBLIC 范围检索的用户。建议：检索内容加“资料边界”标记、长度与格式强制、与系统指令分离为独立上下文块并做注入检测；MarkItDown Worker 的 X-Worker-Token 为静态共享密钥（AiDocumentConversionService.java:42-43），建议增加轮换机制。

### M08 · 修改密码后旧会话不失效
AuthService.changePassword（:93-106）只改密码，不删除该用户其它 login_session。改密后旧 token 仍可用至 7 天过期；must_change_password 状态之外无会话吊销手段。建议：改密时清空该用户全部会话。

### M09 · OAuth 回调可被 CSRF 诱导换绑发件邮箱
MicrosoftMailController.java:44-55 回调仅凭 session cookie 判定管理员；攻击者可自行发起 /authorize 取得 state+verifier，再诱使已登录管理员打开 /callback?code=&state=，从而把平台发件账号绑定到攻击者邮箱（后续以平台名义发钓鱼邮件）。建议：state 绑定发起 IP/会话指纹，回调要求一次性确认码。

### M10 · 上传仅扩展名白名单、客户端 MIME 原样输出
CircleController.java:181-210 扩展名白名单（pdf/doc/docx/ppt/pptx/zip/png/jpg/jpeg）+ 20MB，但内容不校验、MIME 直接入库（:210）；下载时 parseMediaType 后按客户端声明输出且支持 inline（:104-109,112-125）。docx/pptx 可携带宏、zip 可携带任意内容。建议：服务端按魔数嗅探内容；公开下载强制 attachment 或对 inline 做 MIME 白名单。

### M11 · MySQL useSSL=false + allowPublicKeyRetrieval=true
docker-compose.yml:28。同一网桥内容器可嗅探认证流量。建议：内网启用 TLS 或严格网络隔离。

### M12 · 加解密密钥默认空值仍可启动
application.yml:31,36,42（MAIL_SECRET_ENCRYPTION_KEY、EMAIL_CODE_PEPPER、AI_SECRET_ENCRYPTION_KEY 默认空）。AiSecretCipher 在空密钥时保存报错（AiSecretCipher.java:80-81，正面），但验证码 pepper<16 时才拒服（RedisVerificationService.java:112-113），DB_PASSWORD 默认 change-me（application.yml:7）。建议：启动期强制断言生产环境密钥非空。

### M13 · Nginx 纯 HTTP、无安全响应头
finals-compass.conf:1-51：仅 :80、无 CSP/X-Frame-Options/X-Content-Type-Options/Referrer-Policy/HSTS、server_tokens 未关。叠加 M01/L10。建议：TLS 终止 + 安全头 + server_tokens off。

### M14 · 本机 Agent Gateway 无鉴权 + callbackBase 任意
scripts/hermes-agent.mjs:43-59：POST /agent-runs 无任何鉴权（mock-agent.mjs:97-99 同）；:149-156 浏览器命令发往请求自带的 callbackBase。本机任意进程（或本机被入侵的进程）可：消耗用户提供的临时 Key（:164）、把网关当出站 POST 代理（callbackBase 指向任意 URL）、驱动用户浏览器；网关对并发任务数也无上限（children 表无界增长）。另注：网关对 Hermes 的“只写工作目录”约束仅是提示性 prompt（:75-76），并非 OS 级沙箱。建议：本地共享密钥/回环 TLS 握手；callbackBase 校验为平台白名单；并发上限 + 沙箱。

---

## 5. 低危与提示级观察

| 编号 | 发现 | 证据 | 建议 |
| --- | --- | --- | --- |
| L01 | 会话令牌存 localStorage | src/api.js:4-9,44-47 | 迁移 HttpOnly Cookie，扩展用隔离 token |
| L02 | 会话 7 天、无轮换/吊销列表 | AuthService.java:47-52,108-111 | 缩短 + 刷新机制 + Redis 吊销集 |
| L03 | AiCenter HTML 原文入库依赖前端净化 | AiCenterContentService.java:66-77 | 后端入库前服务端净化（纵深防御） |
| L04 | 扩展研究文本入 chrome.storage 无 TTL | browser-extension/background.js lastStatus | 只存摘要 + 定期清理 |
| L05 | pdf-renderer 任意 HTML 渲染（预留未接线） | services/pdf-renderer/server.mjs:15-43 | 若启用：加禁网参数、并发限制、token 轮换 |
| L06 | 收件人邮箱 SHA-256 无盐 | DynamicMailService.java:179-188 | HMAC(pepper, email) |
| L07 | 匿名昵称无唯一约束 | AnonymousIdentityService.java:13-27 | 昵称唯一索引 |
| L08 | 异常消息原样回显 | ApiExceptionHandler.java:18-24 | 对外固定文案 + traceId |
| L09 | health 匿名且触发 DB 查询 | SystemController.java:36-40 | 分离探活与依赖检查 |
| L10 | CET 真题/答案仅登录即可取 | CetController.java:88-121 | 如需付费墙则收紧授权 |
| L11 | STATIC_LOCATIONS 可被环境变量改为 file 路径 | application.yml:17 | 若指向 uploads 会绕过 PENDING 过滤，配置校验 |
| L12 | 产物 contentType 未白名单、Content-Disposition 文件名未转义 | AiRuntimeDispatchController.java:66-72 | 白名单 + 引号/CRLF 转义 |
| L13 | CI 无依赖审计、前端依赖浮动版本 | ci.yml；package.json:14-20 | 增加 npm audit / OWASP DC / pip-audit |
| L14 | Trace/反馈存用户明文，无保留期 | AiChatService.java:159（goal 截断 200 字入 Trace）；AiFeedbackService.java:180（反馈 comment 4000 字明文） | 定时归档/脱敏；error_summary 白名单化 |

**提示级（不计入低危统计）**：
1. MCP SERVICE_TOKEN 认证模式无 resolver 实现（RuntimeMcpCredentialResolverRegistry.java:25-33），配置即调用失败——功能缺口而非漏洞。
2. CHAT 无每用户并发上限（SSE 5 分钟 × 无限会话，AiChatService.java:117），可配合 Redis 计数限并发。
3. STDIO MCP 若管理员把 node/npx 等解释器加入白名单（StdioRuntimeMcpTransport.java:141-148），等价授权任意代码执行——默认全拒（正面），需审计白名单并加参数模板。

---

## 6. 相对历史审计（2026-08-09 安全审计报告.md）的变化

**已修复 / 已缓解**：
- 旧 M06（Redis 口令前后端不一致）：compose 与 .env.example 已统一 REDIS_PASSWORD 单一来源（docker-compose.yml:45,66）。
- 旧报告当时未覆盖的 Agent 回调、浏览器桥、Chat 重构链路本次已全部纳入。
- /api/auth/register 恒 403（AuthController.java:59-62）维持不变。

**仍存在的历史发现（映射到本报告编号）**：

| 历史编号 | 本报告编号 | 说明 |
| --- | --- | --- |
| H01 容器 root | H05 | 未修复 |
| H02 Nginx 无 TLS/安全头 | M13 | 未修复 |
| H03 扩展全域权限 | H04 | 未修复 |
| M01 Cookie 无 Secure | M01 | 未修复 |
| M02 XFF 盲信 | M02 | 未修复 |
| M03 网关 URL SSRF | M03 | 未修复 |
| M04 MySQL 明文 | M11 | 未修复 |
| M05 默认弱密钥 | M12 | 未修复 |
| L01 localStorage | L01 | 未修复 |
| L02 jar chown | （并入 H05） | 未修复 |
| L03 server_tokens | （并入 M13） | 未修复 |
| L04 会话 7 天 | L02 | 未修复 |
| L05 HTML 原文存储 | L03 | 未修复 |
| L06 扩展存储无 TTL | L04 | 未修复 |

**本次新增（历史报告未覆盖或代码演进后引入）**：H01、H02、H03、M04–M10、M14、L05–L13。

---

## 7. 已确认的正面设计（安全基线）

1. **凭据加密**：AiSecretCipher/MailSecretCipher 均 AES-256-GCM、随机 12B IV、128 位 tag、密钥 32 字节强校验（AiSecretCipher.java:16,31,88-89）；解密即用即弃、char[] 清零（ResolvedAiCredential.java:40-43）；只存指纹不存明文。
2. **验证码体系**：仅 HMAC-SHA256 存 Redis（10 分钟）、MessageDigest.isEqual 常量时间比对、5 次失败锁定、Lua 原子限流（RedisVerificationService.java:24-32,76-78,82-85）。
3. **认证基元**：BCrypt（spring-security-crypto）；must_change_password 首登强制改密（AuthenticationInterceptor.java:51-59）。
4. **SQL 注入面极低**：全库 JdbcClient 命名参数；拼接均为固定白名单常量（subagent A 全仓验证）。
5. **回调鉴权**：/api/ai-center/external-agent/** 用每 run 随机 callbackToken（AiRuntimeDispatchService.java:171-182）；产物文件名去路径（ExternalAgentCallbackController.java:69）、100MB 上限、下载 startsWith 校验（AiRuntimeDispatchController.java:60-63）。
6. **出站限制**：响应 ≤32MB、超时、头名校验防 CRLF、http 仅回环、不跟随重定向（JdkRuntimeHttpTransport.java:20-87）。
7. **MCP 治理**：STDIO 可执行文件+工作目录白名单默认全拒（StdioRuntimeMcpTransport.java:141-148）；工具须管理员审批发布；OAuth PKCE + 8 分钟 state。
8. **前端净化**：全仓 v-html 仅两处且全经 DOMPurify；KaTeX trust:false；PDF 渲染无脚本执行面。
9. **Worker 加固**：扩展名/魔数/zip 炸弹/加密 PDF/音频时长多重守卫；USER nobody、read_only、tmpfs、no-new-privileges、并发 2（services/markitdown-worker）。
10. **网络收敛**：MySQL/Redis/Worker 无对外端口；backend 仅 127.0.0.1:8080；网关仅回环；public.sh 只暴露回环并经 cloudflared 随机域名。
11. **日志与 Trace 收敛**：Trace 拒写 key/secret/prompt 字段名；执行表只存 200 字 goal 摘要与 500 字错误摘要；RequestTraceFilter 不记 body。
12. **临时 BYOK 不落库**：仅内存 char[]，不进 request_payload/Trace/Redis。
13. **模块维护熔断**：ModuleMaintenanceInterceptor 后端强制 503。
14. **临时密码不入库**：16 位随机 BCrypt 临时密码只随邮件发送。

---

## 8. 修复优先级建议

| 优先级 | 编号 | 理由 |
| --- | --- | --- |
| P0（立即） | H01、H02、H03、H04、H05、M01、M13、M12 | 平台 Key 无上限 + 登录无防爆破 + 令牌暴露 + root 容器 + 明文传输：均为可远程/低门槛利用的资金与接管风险 |
| P1（短期） | M02、M03、M04、M05、M06、M08、M09、M10、M14 | IP 伪造、SSRF 面、回调/会话生命周期、OAuth CSRF、上传内容校验 |
| P2（计划内） | M07、M11、L01–L13 | 提示注入、内网明文、纵深防御与信息收敛、供应链扫描 |

## 9. 附录：局限性声明

- 本报告为静态只读审查；未做动态渗透、未比对依赖 CVE（Spring Boot 3.5.3 / POI 5.4.1 / Vue 生态的已知漏洞需单独 SCA）。
- 行号基于审查时的工作区代码；后续提交可能漂移。
- “未发现”表述仅表示本次静态审查未发现，不构成不存在性的证明。
- 报告不包含任何生产凭据或真实用户数据。

*本报告由只读静态安全审查生成，未修改任何源文件。生成日期：2026-08-14。*
