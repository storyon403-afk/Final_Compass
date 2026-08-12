# Finals Compass AI Runtime Bridge

当前版本为 `0.6.3`，与 `manifest.json` 和 AI Center 下载入口保持一致。该 Chrome Manifest V3 扩展包含两条相互隔离的协议：

- Agent Browser Gateway：搜索、打开并读取公开网页，结果作为外部 Agent 的浏览器上下文。
- Multi-WebAgent：使用用户已有登录态打开网页 AI、提交角色任务并回传结果。

## 安装

1. 解压发布包，或直接使用仓库中的 `browser-extension/` 目录。
2. 打开 `chrome://extensions`，启用“开发者模式”。
3. 点击“加载已解压的扩展程序”，选择解压目录。
4. 打开本地 AI Center（`http://localhost:5173` 或 `http://127.0.0.1:5173`），扩展会从已登录页面取得平台会话并连接后端 `/ws/browser-bridge`。
5. 进入 Agent 或 MultiWeb AI 发起任务。若某个网页 AI 尚未登录，扩展会打开登录页；完成登录后原任务会自动续跑。

## 当前运行方式

- **Agent Browser Gateway**：本地 Agent Gateway 通过后端回调接口提交浏览器命令，后端再经 WebSocket 发给属于当前用户的扩展连接。扩展完成搜索、打开和读取后返回结构化结果。
- **MultiWeb AI**：前端创建 `MULTI_WEB_AGENT` 运行，为 Kimi、DeepSeek、Qwen 建立参与者。用户明确要求并行分工时生成三份精简子任务，否则三者独立回答同一问题；完成后由审核模型汇总和复核。
- **等待登录与取消**：v0.6.3 会保留等待登录的运行状态，并支持从 Finals Compass 取消整个运行。
- **标签页清理**：Agent 搜索结束后关闭检索标签页并返回平台；MultiWeb AI 全部执行结束后也会关闭由扩展打开的网页 AI 标签页。

## 安全边界

扩展不会读取或保存密码；登录、注册、验证码和服务条款确认始终由用户完成。Agent Browser Gateway 只执行只读动作，不自动提交表单、上传文件、下载或支付。MultiWeb AI 只在用户主动发起的运行中操作已支持的网页 AI。网页结构或站点策略变化后，对应适配器可能需要升级。

扩展不是独立的 AI Provider，也不直接访问 Finals Compass 数据库。运行记录、用户归属、状态推进与 Agent 回调均由 Spring Boot 后端控制；扩展只承担浏览器环境内必须完成的操作。
