# Finals Compass 安全审计报告

> 本报告为**只读审计**产物，未修改任何源代码或源文件。所有结论均可回溯到下方标注的源文件与行号。
> 配套文档：`docs/系统架构与设计总览.md` 记录架构与设计；本报告聚焦安全维度的具体风险与修复建议。

---

## 0. 审计概述

| 项目 | 说明 |
|---|---|
| 审计对象 | Finals Compass 全栈（Spring Boot 后端 + Vue 前端 + 浏览器扩展 + MarkItDown Worker + Docker/Nginx 部署） |
| 审计方式 | 静态只读源码审查，逐文件定位风险点并回填行号、代码片段与 CWE/OWASP 分类 |
| 审计约束 | 不改代码、不动源文件；仅在本目录新增文档 |
| 严重程度分级 | 高 / 中 / 低（低亦包含纵深防御观察项） |
| 分类依据 | CWE / OWASP Top 10 (2021) |

### 审计覆盖范围

- **后端**：`AuthController`、`AiRuntimeDispatchService`、`AiCenterContentService`、`application.yml`、`pom.xml`、`deploy/docker-compose.yml`、`deploy/backend/Dockerfile`
- **前端**：`src/api.js`、`src/App.vue`、`src/main.js`、`components/SafeHtml.vue`、`components/SafeMarkdown.vue`、`components/AiCenterContentManager.vue`
- **浏览器扩展**：`manifest.json`、`background.js`、`bridge.js`
- **MarkItDown Worker**：`app/main.py`、`app/guards.py`、`Dockerfile`
- **部署**：`deploy/nginx/finals-compass.conf`、`deploy/docker-compose.yml`、`deploy/.env.example`

### 未覆盖项（建议后续补充）

- 动态运行时测试（黑盒渗透、依赖项 SCA/CVE 比对）
- 数据库迁移脚本（Flyway `db/migration`）逐条审查（本报告仅抽查）
- 第三方 AI 网关（Hermes/agent-gateway，默认 `http://127.0.0.1:8642`）的实现细节

---

## 1. 风险汇总表

| 编号 | 严重度 | 标题 | 位置 | CWE / OWASP |
|---|---|---|---|---|
| FC-SEC-H01 | 高 | 后端容器以 root 运行 | `deploy/backend/Dockerfile` | CWE-250 / OWASP A05 |
| FC-SEC-H02 | 高 | Nginx 纯 HTTP 且缺安全响应头 | `deploy/nginx/finals-compass.conf` | CWE-319, CWE-1026 / OWASP A02 |
| FC-SEC-H03 | 高 | 扩展持有 `https://*/*` 全域 host 权限 | `browser-extension/manifest.json` | CWE-732 / OWASP A01 |
| FC-SEC-M01 | 中 | 会话 Cookie 缺少 Secure 标志 | `backend/.../AuthController.java:74-76` | CWE-614 / OWASP A05 |
| FC-SEC-M02 | 中 | 盲信 X-Forwarded-For 取客户端 IP | `backend/.../AuthController.java:79-82` | CWE-345 / OWASP A07 |
| FC-SEC-M03 | 中 | Agent Gateway URL 取自 DB 且无主机白名单（SSRF） | `backend/.../AiRuntimeDispatchService.java:5` | CWE-918 / OWASP A10 |
| FC-SEC-M04 | 中 | MySQL 连接 `useSSL=false` 且 `allowPublicKeyRetrieval=true` | `deploy/docker-compose.yml`（backend env） | CWE-319 / OWASP A02 |
| FC-SEC-M05 | 中 | 默认弱密钥 / 空密钥（DB 口令与加解密密钥） | `backend/.../application.yml` | CWE-798, CWE-321 / OWASP A07 |
| FC-SEC-M06 | 中 | Redis 鉴权口令默认空且前后端默认不一致 | `application.yml` + `docker-compose.yml` | CWE-259 / OWASP A07 |
| FC-SEC-L01 | 低 | 会话令牌存储于 localStorage | `src/api.js` | CWE-921 / OWASP A04 |
| FC-SEC-L02 | 低 | 后端 Dockerfile COPY 未 `--chown`（jar 属主 root） | `deploy/backend/Dockerfile` | CWE-250 |
| FC-SEC-L03 | 低 | Nginx 未关闭 `server_tokens` | `deploy/nginx/finals-compass.conf` | CWE-200 |
| FC-SEC-L04 | 低 | 会话有效期 7 天且无刷新/轮换 | `AuthController.java:43` | CWE-613 |
| FC-SEC-L05 | 低 | AiCenter 内容后端原文存储 HTML（纵深防御缺口） | `AiCenterContentService.java:3` | CWE-79（残留） |
| FC-SEC-L06 | 低 | 扩展将研究文本持久化于 chrome.storage 且无过期 | `browser-extension/background.js:43` | CWE-312 |
| FC-SEC-L07 | 低 | 无 CSRF 令牌，仅依赖 SameSite=Lax | 全局（后端） | CWE-352（已缓解） |
| FC-SEC-L08 | 低 | 扩展 manifest 中 `https://*/*` 与逐站条目重复 | `browser-extension/manifest.json` | CWE-732 |

**统计**：高危 3、中危 6、低危 9，共 18 项。

---

## 2. 高危发现

### FC-SEC-H01 · 后端容器以 root 运行

- **位置**：`deploy/backend/Dockerfile`
- **CWE / OWASP**：CWE-250（执行不必要特权）/ OWASP A05 安全配置错误
- **描述**：最终镜像 `FROM eclipse-temurin:21-jre` 后未设置 `USER`，容器进程（Java 应用）以 root 身份运行；同时 `COPY --from=build ... app.jar` 未带 `--chown`，jar 文件属主为 root。后端挂载了 `uploads` 卷（见 `docker-compose.yml`），一旦解析链（Apache POI 5.4.1 等第三方库）出现漏洞，攻击者将以容器 root 写入挂载卷与文件系统。
- **代码片段**：
  ```dockerfile
  FROM eclipse-temurin:21-jre
  WORKDIR /app
  COPY --from=build /workspace/backend/target/finals-compass-api-0.1.0.jar app.jar
  EXPOSE 8080
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```
- **修复建议**：
  - 创建非 root 用户：`RUN groupadd -r app && useradd -r -g app app`，并在 `ENTRYPOINT` 前 `USER app`。
  - `COPY --chown=app:app ... app.jar`，确保文件属主非 root。
  - 在 `docker-compose.yml` 的 backend 服务补充 `security_opt: ["no-new-privileges:true"]`（与 redis、markitdown-worker 保持一致）。

### FC-SEC-H02 · Nginx 纯 HTTP 且缺安全响应头

- **位置**：`deploy/nginx/finals-compass.conf`
- **CWE / OWASP**：CWE-319（明文传输敏感信息）/ CWE-1026（缺失内容安全策略）/ OWASP A02 加密失败
- **描述**：Nginx 仅监听 `:80`，`server_name _`，无 TLS、无 HSTS；未设置任何安全响应头（无 CSP、`X-Frame-Options`、`X-Content-Type-Options`、`Referrer-Policy`、`Permissions-Policy`）。会话 Cookie（见 M01）经明文 HTTP 传输，可被中间人窃取。`/api/ai/invoke` 处 `proxy_request_buffering off` 用于摄像头大图直传，进一步放大了明文传输面。
- **修复建议**：
  - 终止明文 HTTP：配置 TLS（或在前置 LB/CDN 终止 TLS 并强制 80→443 跳转），启用 `Strict-Transport-Security`。
  - 增加安全头：`Content-Security-Policy`（限定 script/style/connect 来源）、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: strict-origin-when-cross-origin`。
  - 关闭 `server_tokens off;`（同时解决 L03）。
  - 注意：启用 CSP 时需与前端 `marked`+`DOMPurify`+`KaTeX` 的内联渲染策略协调（KaTeX 样式可能需要 `style-src` 放行）。

### FC-SEC-H03 · 扩展持有 `https://*/*` 全域 host 权限

- **位置**：`browser-extension/manifest.json`（`host_permissions`）
- **CWE / OWASP**：CWE-732（关键资源权限定义不恰当）/ OWASP A01 访问控制失效
- **描述**：`host_permissions` 在列出 Kimi/DeepSeek/通义/Bing/Google 逐站条目的同时，又加了 `"https://*/*"`，使扩展对用户访问的**任意 HTTPS 站点**具备 `tabs`+`scripting` 注入与读取能力。虽然 `content_scripts` 仅匹配 `localhost:5173`（WebApp 桥接收敛，属正面设计），但 `https://*/*` 仍显著扩大了 `chrome.scripting.executeScript` 的可注入范围；一旦扩展被滥用或供应链被植入恶意代码，可读取任意站点内容与凭据。
- **代码片段**：
  ```json
  "host_permissions": [
    "http://localhost:5173/*", "http://127.0.0.1:5173/*",
    "https://kimi.moonshot.cn/*", "https://chat.deepseek.com/*",
    "https://www.qianwen.com/*", "https://tongyi.aliyun.com/*",
    "https://www.bing.com/*", "https://www.google.com/*",
    "https://*/*"   // ← 与逐站条目重复且过度宽泛
  ]
  ```
- **修复建议**：
  - 删除 `"https://*/*"`，仅保留实际编排所需的逐站条目（Kimi/DeepSeek/通义/Bing）。
  - Agent 研究的 `agentResearch` 当前用 Bing 搜索并抓取**任意**结果页（`extractReadablePage`），其抓取目标不可预知——如需保留，应将通用抓取迁至后端代理（受控抓取），而非扩展全域权限。
  - 收敛后 `permissions` 中 `scripting` 的实际可注入面将降至已知站点集。

---

## 3. 中危发现

### FC-SEC-M01 · 会话 Cookie 缺少 Secure 标志

- **位置**：`backend/src/main/java/cn/finalscompass/controller/AuthController.java:74-76`
- **CWE / OWASP**：CWE-614（未设 Secure 的敏感 Cookie）/ OWASP A05
- **描述**：`setSessionCookie` 设置了 `httpOnly(true)` 与 `sameSite("Lax")`，但**未设置 `.secure(true)`**。结合 H02（Nginx 纯 HTTP），会话令牌会以明文随请求/Cookie 头在网络中传输，可被中间人窃取后重放。`/stream-cookie` 与 `/logout` 也复用同一方法，问题一致。
- **代码片段**：
  ```java
  private void setSessionCookie(HttpServletResponse response, String token, long maxAge) {
      response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SESSION_COOKIE, token)
              .httpOnly(true).sameSite("Lax").path("/api").maxAge(maxAge).build().toString());
  }
  ```
- **修复建议**：追加 `.secure(true)`；在 TLS 终止于前置代理的场景，配合 `server.forward-headers-strategy` 与受信代理链，确保仅信任来自受信源的协议标记。

### FC-SEC-M02 · 盲信 X-Forwarded-For 取客户端 IP

- **位置**：`backend/src/main/java/cn/finalscompass/controller/AuthController.java:79-82`
- **CWE / OWASP**：CWE-345（数据真实性验证不足）/ OWASP A07 身份与认证失效
- **描述**：`clientIp` 直接读取 `X-Forwarded-For` 头并取首个值，无受信代理校验。该方法用于 `beta-access` 流程的客户端标识，攻击者可任意伪造该头以绕过基于 IP 的频控、伪造审计来源，或在风控中嫁祸他人。
- **代码片段**：
  ```java
  private String clientIp(HttpServletRequest request) {
      String forwarded = request.getHeader("X-Forwarded-For");
      return forwarded == null || forwarded.isBlank()
          ? request.getRemoteAddr()
          : forwarded.split(",", 2)[0].trim();
  }
  ```
- **修复建议**：仅当 `request.getRemoteAddr()` 为受信反向代理（Nginx/容器网关）时才采信 `X-Forwarded-For`，并取链中受信代理之后的那一跳；否则直接使用 `getRemoteAddr()`。配合 `server.forward-headers-strategy=NATIVE` 与受信代理 allowlist。

### FC-SEC-M03 · Agent Gateway URL 取自 DB 且无主机白名单（SSRF）

- **位置**：`backend/src/main/java/cn/finalscompass/ai/runtime/agent/AiRuntimeDispatchService.java:5`（`invokeAgent`）
- **CWE / OWASP**：CWE-918（服务端请求伪造）/ OWASP A10 SSRF
- **描述**：`invokeAgent` 从 `ai_agent_definition` 表读取 `gateway_url`，若不存在则回退到 `http://127.0.0.1:8642`。读取的 URL 被直接 `URI.create(url+"/agent-runs")` 后以 `HttpClient` 发起 POST，**未校验协议或主机**。若该表行被具备写权限的账号（或 SQL 注入/迁移失误）改为内网/云元数据地址，后端将代攻击者发起请求（SSRF）。
- **代码片段**：
  ```java
  String url = jdbc.sql("SELECT gateway_url FROM ai_agent_definition WHERE status='ACTIVE' ORDER BY id LIMIT 1")
      .query(String.class).optional().orElse(fallbackUrl);
  // ...
  HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/agent-runs"))
      .timeout(Duration.ofSeconds(90))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body)).build();
  HttpResponse<String> res = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
      .send(req, HttpResponse.BodyHandlers.ofString());
  ```
- **修复建议**：
  - 对 `gateway_url` 做主机/网段白名单校验（如仅允许 `127.0.0.1`/容器内网段/受信任网关域名），拒绝非预期协议（`file://`、`gopher://` 等）。
  - 对该表的写入接口施加管理员授权与审计；将"可配置网关 URL"收敛为受控选项集。
  - 对出站请求禁用重定向跟随，限制响应体大小与超时（超时已设 90s/3s，建议下调外联超时）。

### FC-SEC-M04 · MySQL 连接 `useSSL=false` 且 `allowPublicKeyRetrieval=true`

- **位置**：`deploy/docker-compose.yml`（backend 服务 `DB_URL` 环境变量，源自 `application.yml` 默认）
- **CWE / OWASP**：CWE-319 / OWASP A02
- **描述**：后端连接串带 `useSSL=false&allowPublicKeyRetrieval=true`，意味着后端与 MySQL 之间的认证流量（含 RSA 公钥交换）以明文进行。虽然 MySQL 容器未发布端口（仅 `app-network` 内部），跨容器链路被同网桥上的其他容器可见，存在凭据泄露与中间人风险。
- **修复建议**：对内网链路启用 TLS（`useSSL=true` 且配置 `requireSSL`/信任 MySQL 服务端证书），关闭 `allowPublicKeyRetrieval`；或确保 `app-network` 严格隔离、仅可信容器接入。

### FC-SEC-M05 · 默认弱密钥 / 空密钥

- **位置**：`backend/src/main/resources/application.yml`
- **CWE / OWASP**：CWE-798（硬编码凭据）/ CWE-321（密钥管理不足）/ OWASP A07
- **描述**：默认配置含弱值：`DB_PASSWORD=change-me`；`REDIS_PASSWORD` 默认空；`AI_SECRET_ENCRYPTION_KEY`、`MAIL_SECRET_ENCRYPTION_KEY`、`EMAIL_CODE_PEPPER` 均默认空字符串。这些密钥用于 Envelope Encryption（AES-256-GCM 包络加密）与邮箱验证码加盐。若运维以默认值部署，包络加密的外层密钥为空，等价于明文存储；邮箱验证码 pepper 为空则削弱抗预计算能力。
- **修复建议**：
  - 所有密钥/口令改为**必填**（缺失即启动失败），并从 `deploy/.env`（已 gitignore）注入强随机值（`.env.example` 已给出 `openssl rand` 指引，落实即可）。
  - 启动期断言：当 `AI_SECRET_ENCRYPTION_KEY` 等为空时拒绝启动并打印明确错误。

### FC-SEC-M06 · Redis 鉴权口令默认空且前后端默认不一致

- **位置**：`application.yml`（`spring.data.redis.password` 默认空）与 `docker-compose.yml`（redis `--requirepass` 设置了口令）
- **CWE / OWASP**：CWE-259（硬编码口令/默认口令风险）/ OWASP A07
- **描述**：Redis 容器侧启用了 `--requirepass`，但后端配置的默认口令为空。若运维忘记在 `application.yml`/`.env` 中填入对应口令，后端将无法鉴权或连接失败；反之若两端均回到默认空口令，Redis 暴露无口令。`noeviction` + `maxmemory 256mb` 策略本身合理。
- **修复建议**：将 Redis 口令纳入同一 `.env` 单一来源，后端与 compose 共用同一变量；启动期校验口令非空且一致。

---

## 4. 低危 / 纵深防御观察

### FC-SEC-L01 · 会话令牌存储于 localStorage

- **位置**：`src/api.js`（`localStorage['finals-compass-session']`）
- **CWE / OWASP**：CWE-921（敏感数据存储不当）/ OWASP A04
- **描述**：会话令牌存于 `localStorage`，可被任何在同源执行的脚本读取。当前前端 XSS 面已收敛于 `SafeHtml`/`SafeMarkdown`（均经 DOMPurify，见 §6 正面发现），故实际可利用性较低；但 localStorage 令牌仍比 HttpOnly Cookie 更易在第三方脚本/依赖被污染时泄露。
- **修复建议**：鉴权主路径已使用 HttpOnly Cookie（`/api` 路径），建议将流式调用也统一收敛到 Cookie（已存在 `/api/auth/stream-cookie` 端点），逐步淘汰 localStorage 中的令牌明文存储。

### FC-SEC-L02 · 后端 Dockerfile COPY 未 `--chown`

- **位置**：`deploy/backend/Dockerfile`
- **CWE / OWASP**：CWE-250
- **描述**：`COPY --from=build ... app.jar` 未指定 `--chown`，文件属主为 root。与 H01 同源，修复 H01 时一并解决。

### FC-SEC-L03 · Nginx 未关闭 `server_tokens`

- **位置**：`deploy/nginx/finals-compass.conf`
- **CWE / OWASP**：CWE-200（信息暴露）
- **描述**：默认 `server_tokens on`，错误页/响应头暴露 Nginx 版本，便于攻击者匹配已知漏洞。
- **修复建议**：`server_tokens off;`（与 H02 修复一并完成）。

### FC-SEC-L04 · 会话有效期 7 天且无刷新/轮换

- **位置**：`AuthController.java:43`（`7 * 24 * 60 * 60`）
- **CWE / OWASP**：CWE-613（会话有效期不足）/ OWASP A07
- **描述**：令牌最长 7 天，无刷新令牌、无滑动续期、无服务端吊销列表（登出仅清 Cookie）。令牌泄露后在其全生命周期内可重放。
- **修复建议**：缩短有效期并引入刷新机制；登出时在 Redis 维护吊销集合；服务端支持主动失效。

### FC-SEC-L05 · AiCenter 内容后端原文存储 HTML（纵深防御缺口）

- **位置**：`backend/.../AiCenterContentService.java:3`（`update` 直接写 `content_html`）
- **CWE / OWASP**：CWE-79（残留）/ OWASP A03
- **描述**：`update` 将管理员提交的 `contentHtml` 原文写入 `content_html` 列（参数化查询，无 SQL 注入）。**前端经 `SafeHtml`（DOMPurify `USE_PROFILES:{html:true}`）渲染**（见 `components/AiCenterContentManager.vue`），故当前**已缓解**。残留风险在于：后端未做服务端净化，完全依赖前端单一净化点；若未来新增任何直接 `v-html` 该字段的消费方，将立即形成存储型 XSS。
- **修复建议**：在后端入库前以服务端 HTML 净化（如 OWASP Java HtmlSanitizer）做一次净化，形成纵深防御；维持"仅经 SafeHtml/SafeMarkdown 渲染"的前端约束。

### FC-SEC-L06 · 扩展将研究文本持久化于 chrome.storage 且无过期

- **位置**：`browser-extension/background.js:43`（`chrome.storage.local.set({ lastStatus: {...payload} })`）
- **CWE / OWASP**：CWE-312（明文存储敏感信息）
- **描述**：扩展将完整研究文本（可能含抓取的页面正文，最长 16000 字符/页）写入 `chrome.storage.local` 且无 TTL/清理。本地任何获取扩展存储读取权限的实体可回看历史研究内容。
- **修复建议**：仅存最近一次状态摘要而非全文；写入时带时间戳，定期清理过期项。

### FC-SEC-L07 · 无 CSRF 令牌，仅依赖 SameSite=Lax

- **位置**：全局（后端鉴权 Cookie）
- **CWE / OWASP**：CWE-352（已缓解）/ OWASP A01
- **描述**：项目未使用 Spring Security（仅 `spring-security-crypto` 提供 BCrypt，鉴权由自定义 `AuthenticationInterceptor` 完成），无 CSRF 令牌机制。依赖 Cookie 的 `SameSite=Lax` 缓解 CSRF。对顶层导航 GET 的 POST 表单足够，但对子资源请求/某些跨站上下文的防护有限。当前 API 多以 `Bearer` 头鉴权（前端 `api.js` 注入），进一步降低 CSRF 风险。
- **修复建议**：维持 Bearer 头优先策略；若未来全面转向 Cookie，补齐 CSRF 令牌或采用 `SameSite=Strict` + Origin/Referer 校验。

### FC-SEC-L08 · 扩展 manifest 中 `https://*/*` 与逐站条目重复

- **位置**：`browser-extension/manifest.json`
- **CWE / OWASP**：CWE-732
- **描述**：与 H03 同源，逐站条目在 `https://*/*` 存在时纯属冗余。修复 H03 后此条目自然消解。

---

## 5. 修复优先级建议

| 优先级 | 编号 | 理由 |
|---|---|---|
| P0（立即） | H01, H02, M01, M05 | 容器提权面 + 明文令牌传输 + 弱默认密钥，任一可被远程利用窃取会话或横向移动 |
| P1（短期） | M02, M03, M04, M06 | IP 伪造绕过频控、SSRF、内网明文与口令一致性 |
| P2（计划内） | H03, L01–L08 | 收敛扩展权限面、纵深防御与服务端净化、会话轮换、信息收敛 |

---

## 6. 已确认的安全设计（正面发现）

为避免"只见风险不见防护"，下列设计经源码确认，构成当前安全基线：

1. **密码哈希**：使用 `spring-security-crypto` 的 BCrypt（项目未引入完整 Spring Security，仅取其 BCrypt 实现，避免不必要的攻击面）。
2. **参数化查询**：后端全面使用 `JdbcClient` 的 `:named` 参数化查询（如 `AiCenterContentService`、`AiRuntimeDispatchService`），未见字符串拼接 SQL，SQL 注入面低。
3. **Worker 多重守卫**：MarkItDown Worker（`app/guards.py`）具备
   - 扩展名白名单（`main.py` `ALLOWED_EXTENSIONS`）；
   - magic-bytes 签名校验（PDF/OOXML/legacy xls/PNG/JPEG/WebP/WAV/MP3/M4A/UTF-8 文本）；
   - zip 炸弹防御（条目数、解压总字节、压缩比上限、拒绝加密归档、预期根目录 + `[Content_Types].xml` 校验）；
   - PDF 拒绝加密 + 页数上限；
   - 音频时长上限（`mutagen`）；
   - `MarkItDown(enable_plugins=False)` 关闭插件；
   - 转换实例请求级隔离（非线程安全第三方解析器）；
   - `asyncio.Semaphore` 并发限流与 429 退避。
4. **Worker 容器加固**：`services/markitdown-worker/Dockerfile` 使用 `USER nobody`；`docker-compose.yml` 中 worker `read_only: true`、`tmpfs /tmp:256m`、`no-new-privileges`、未发布端口。
5. **Redis 加固**：`no-new-privileges`、`--maxmemory 256mb`、`noeviction` 策略、`--requirepass`。
6. **常量时间令牌比较**：Worker 鉴权 `hmac.compare_digest`（`main.py`），避免时序侧信道。
7. **前端 HTML 渲染统一净化**：全仓 `v-html` 仅出现在 `SafeHtml.vue`（DOMPurify `html` profile）与 `SafeMarkdown.vue`（`marked`→`DOMPurify` 管线），含 KaTeX 数学公式在代码块外渲染的安全处理。AiCenter 管理内容经 `SafeHtml` 渲染。
8. **输入校验**：`AiRuntimeDispatchService.start` 校验 `goal` 长度（≤20000）与 `runtimeType` 白名单（`AGENT`/`MULTI_WEB_AGENT`）；`AiCenterContentService` 校验 `page_key` 白名单与各字段长度上限。
9. **网络收敛**：后端 `server.address=127.0.0.1`；MySQL 未发布端口（仅 `app-network` 内部）；密钥/口令示例与 `.env` 已在 `.gitignore` 排除（保留 `.env.example`）。
10. **注册端点已禁用**：`/api/auth/register` 直接抛 403，避免开放注册。

---

## 7. 附录：审计方法与可信度说明

- 所有发现均**逐条回溯到源文件行号/片段**，未依赖运行时动态测试或第三方 SCA/CVE 数据库比对。
- 中高危结论已在前端渲染链、容器配置、Cookie 属性、SQL 写入方式等关键环节通过**直接读取源码**复核，而非仅凭静态扫描器输出。
- 低危项多为纵深防御建议，不意味着已被实际利用；标注"已缓解"者表示现有设计已大幅降低风险，残留为未来回归防护。
- 建议结合动态渗透（尤其 `/api/ai/invoke` 摄像头图像链路与 Agent 网关 SSRF）与依赖项 CVE 比对作为后续工作。

---

*本报告由只读审计生成，未修改任何源文件。生成日期：2026-08-09。*
