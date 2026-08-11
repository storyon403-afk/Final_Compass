# Finals Compass WebAgent Bridge

Chrome Manifest V3 扩展，包含两条相互隔离的协议：

- Agent Browser Gateway：搜索、打开并读取公开网页，结果作为外部 Agent 的浏览器上下文。
- Multi-WebAgent：使用用户已有登录态打开网页 AI、提交角色任务并回传结果。

## 安装

1. 解压发布包。
2. 打开 `chrome://extensions`，启用“开发者模式”。
3. 点击“加载已解压的扩展程序”，选择解压目录。
4. 进入 MultiWeb AI 发起任务。若某个网站尚未登录，扩展会打开登录页；完成登录后原任务会自动续跑。

v0.6.3 会保留等待登录的运行状态，并支持从 Finals Compass 取消整个运行。Agent Web 搜索兼容 Bing 跳转链接和多种结果页结构，结束后关闭检索标签页并返回平台；MultiWeb AI 全部执行结束后也会关闭插件打开的网页 AI 标签页并返回平台。用户明确要求并行分工时生成三份精简子任务，否则三者独立回答同一问题。

扩展不会读取或保存密码；登录、注册、验证码和服务条款确认始终由用户完成。当前 Agent Browser Gateway 只执行只读动作，不自动提交表单、上传文件、下载或支付。网页结构变化后，站点适配器可能需要升级。
