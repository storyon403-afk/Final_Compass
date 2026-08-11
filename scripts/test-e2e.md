# AI Center 本地端到端测试清单

验证目标：chat 问答（SSE+RAG）、Gateway 调用本地 agent、浏览器扩展 WS 桥接、artifact 下载。

前置条件：MySQL / Redis 已启动，Flyway 已执行到 V52（`ai_runtime_run.callback_token` 列与 `ai_runtime_run_artifact` 表存在）。

## 1. 启动后端 + 前端 + mock agent

```bash
cd /Users/storyon/study/Final_Compass
./scripts/dev.sh          # 后端 8080 + 前端 5173（另开终端）
node scripts/hermes-agent.mjs # Hermes Agent Gateway，监听 127.0.0.1:8642
# 若 hermes 不在 PATH：HERMES_BIN=/绝对路径/hermes node scripts/hermes-agent.mjs
# 仅测试协议、不消耗模型额度时可改用：node scripts/mock-agent.mjs
# 可选：MOCK_BROWSER_URL=https://example.com node scripts/mock-agent.mjs
```

预期 mock agent 输出：`[mock-agent] listening on http://127.0.0.1:8642`

## 2. 登录取 token

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"<用户名>","password":"<密码>"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
echo "$TOKEN"
```

## 3. 验证 chat SSE（RAG）

```bash
SESSION=$(curl -s -X POST http://127.0.0.1:8080/api/ai-center/chat/sessions \
  -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["sessionKey"])')

curl -N -X POST "http://127.0.0.1:8080/api/ai-center/chat/sessions/$SESSION/messages" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
  -d '{"message":"介绍一下知识库里有什么"}'
```

预期依次收到事件：`event: meta`（messageId）→ `event: sources`（知识库命中条目，可为空数组）→ `event: delta`（回答文本块）→ `event: done`。凭据未配置时会收到 `event: error`。

## 4. 加载浏览器扩展，确认 WS 连接

1. Chrome 打开 `chrome://extensions`，开发者模式，加载已解压扩展：`Final_Compass/browser-extension`。
2. 点击扩展图标，在弹窗中填入：
   - Bridge URL：`ws://127.0.0.1:8080/ws/browser-bridge`
   - Token：第 2 步的 `$TOKEN`
   - 勾选启用，点击保存。
3. 预期状态显示已连接；后端日志出现 browser-bridge handshake 成功。

## 5. Agent 任务：驱动浏览器并产出 artifact

方式 A（前端）：打开 `http://localhost:5173/ai-center/chat`，选 Agent 模式，发送任务（如"调研 example.com 并生成报告"）。页面轮询运行状态，最终出现 `agent-report.md` 下载链接。

方式 B（curl）：

```bash
RUN=$(curl -s -X POST http://127.0.0.1:8080/api/ai-center/dispatch \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"runtimeType":"AGENT","goal":"调研 example.com 并生成报告"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["run"]["run_key"])')

# 轮询状态，直到 COMPLETED
curl -s "http://127.0.0.1:8080/api/ai-center/dispatch/$RUN" -H "Authorization: Bearer $TOKEN"

# 列出并下载 artifact
curl -s "http://127.0.0.1:8080/api/ai-center/dispatch/$RUN/artifacts" -H "Authorization: Bearer $TOKEN"
curl -s -o report.md "http://127.0.0.1:8080/api/ai-center/dispatch/$RUN/artifacts/1" -H "Authorization: Bearer $TOKEN"
cat report.md
```

预期链路：后端 POST `/agent-runs` → mock agent 回调 `RUNNING` → 经浏览器中继下发 `navigate` / `get_content`（扩展新开标签页抓取）→ 上传 `agent-report.md` → 回调 `COMPLETED`。mock agent 终端应逐步打印 navigate / got content / artifact uploaded / completed。

## 6. 故障路径：Gateway 不可用

停止 mock agent（Ctrl+C），再发起一次 Agent 任务。预期 run 状态为 `WAITING_CONFIGURATION`，错误码 `AGENT_GATEWAY_UNAVAILABLE`，前端 Agent 模式提示网关未启动。

## 常见问题

- 扩展状态一直"连接中"：确认后端 8080 在监听、token 有效（未过期/未登出）。
- mock agent 报 `HTTP 401 from .../browser/commands`：callbackToken 与 runKey 不匹配，重启任务即可。
- `get_content` 超时：目标页面加载慢，调大 `timeoutMs` 或换 `MOCK_BROWSER_URL`。
- chat 返回 error `NO_CREDENTIAL`：在管理后台配置模型凭据，或请求体带 `provider`/`model`/`ephemeralApiKey`。
