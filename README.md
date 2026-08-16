# Finals Compass / 期末指南

Finals Compass 是一个面向鬼大课程复习与学习协作的开源平台（目前只在树桶学院开放）。系统以“学院 → 专业 → 课程 → 任课老师 → 老师圈”组织课程、资料、讨论和复习指南，同时提供英语等级考试内容，以及由 Chat、Agent 和 MultiWeb AI 组成的 AI Center。

## 当前功能

- **课程知识网络**：课程代码全局唯一，公共专业课可以关联多个专业；资料、老师、讨论和复习指南围绕课程组织。
- **学习资料协作**：支持资料上传、在线预览、感谢、讨论、指南引用和管理员审核。
- **英语等级考试**：提供 CET 内容导航、试卷展示与听力相关能力；公开仓库不分发受版权保护的试题资源。
- **身份与账号发放**：通过 Redis 管理邮箱验证码、有效期、失败次数和限流，由管理员在邮箱验证成功后确认发放账号。
- **动态邮件服务**：管理员可以配置 SMTP 和邮件模板；同时预留 Microsoft Graph OAuth2 Provider，未配置时保持关闭。
- **AI Center Chat**：知识库 RAG、Redis 对话历史、SSE 响应、多 Provider/Model/Endpoint 匹配以及主模型失败后的候选降级。
- **Agent Runtime**：由平台创建运行记录并向本地 Agent Gateway 下发任务，支持知识上下文、浏览器只读命令、状态回调和文件产物。
- **MultiWeb AI**：通过 Chrome 扩展复用用户已有网页登录态，让 Kimi、DeepSeek、Qwen 分工或独立回答，再由配置的审核模型汇总与复核。
- **AI 运行时治理**：Provider、Model、Endpoint、Skill、Tool、MCP 和执行 Trace 均由注册表与状态机管理；管理员可维护知识库、MCP 审批、反馈优化和 AI Evolution 指标。
- **AI 凭据边界**：支持平台 Key、加密保存的 BYOK 和仅驻留当前请求的临时 BYOK；模型调用统一通过凭据解析入口取得 Key。
- **暂挂体验**：提供全局可访问的短暂休息交互，支持视频预加载和管理员配置。

AI Center 当前提供三种对外 Runtime：`CHAT`、`AGENT`、`MULTI_WEB_AGENT`。历史上的硬编码 Skill 编排、Workflow 页面和旧版解题链路已经退出当前入口；数据库仍保留部分注册表和演进基础设施，但是旧 Runtime 不可由用户调用。仓库不包含平台生产 API Key，也不捆绑或自动部署本地大模型。

## 技术栈

- Vue 3、Vue Router、Vite
- Java 21、Spring Boot、Spring MVC、JdbcClient
- MySQL 8、Flyway
- Redis 7
- Chrome Manifest V3 扩展、WebSocket 浏览器桥接
- Python 3.10+、Microsoft MarkItDown（可选附件解析 Worker）
- Docker Compose、Nginx

## 运行结构

```text
浏览器 / Vue 3
      │ HTTP / JSON / SSE
      ▼
Spring Boot API ───── Redis（验证码、对话历史、OAuth state、短期安全状态）
      │       ├───── SMTP / Microsoft Graph（邮件发送）
      │       ├───── AI Provider API（平台 Key / 已保存 BYOK / 临时 BYOK）
      │       ├───── MarkItDown Worker（附件标准化）
      │       └───── Agent Gateway（任务执行、回调和文件产物）
      ▼                            │ WebSocket
MySQL + Flyway                     ▼
（业务事实、AI 注册表与 Trace）  Chrome 扩展（网页搜索 / MultiWeb AI）
```

## 开源版说明

公开仓库只包含源代码、数据库结构、自动测试和通用部署模板，不包含：

- 生产账号、密码、Token 或服务器信息
- 用户上传文件和数据库备份
- CET 真题、答案和听力文件
- 未经授权的图片或其他媒体文件

首次管理员由环境变量显式创建。应用不会在 Flyway 迁移中写入固定账号。

## 快速克隆

未配置 GitHub SSH Key 时使用 HTTPS：

```bash
git clone https://github.com/storyon403-afk/Final_Compass.git
cd Final_Compass
npm ci
```

已经配置 GitHub SSH Key 的贡献者可以使用 SSH：

```bash
git clone git@github.com:storyon403-afk/Final_Compass.git
cd Final_Compass
npm ci
```

克隆完成后，按照下方“本地启动”配置 MySQL、Redis 和后端环境变量。准备参与开发时，请先阅读 [贡献流程](CONTRIBUTING.md) 和 Wiki 的 [学习树](https://github.com/storyon403-afk/Final_Compass/wiki/Learning-Tree)。

## 本地启动

### 1. 准备环境

- Node.js 22.18 或更高版本
- Java 21
- Maven 3.9 或更高版本
- MySQL 8
- Redis 7（邮箱验证、AI 对话历史和 MCP OAuth 状态需要）
- Python 3.10 或更高版本（仅独立运行附件解析 Worker 时需要）

创建数据库和开发账号：

```sql
CREATE DATABASE finals_compass CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'finals'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON finals_compass.* TO 'finals'@'localhost';
```

### 2. 启动后端

```bash
cd backend
export DB_URL='jdbc:mysql://localhost:3306/finals_compass?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
export DB_USER='finals'
export DB_PASSWORD='change-me'
export APP_ADMIN_USERNAME='admin'
export APP_ADMIN_PASSWORD='replace-with-at-least-12-random-characters'
# 以下值只存在于当前终端，不得写入仓库
export MAIL_SECRET_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export EMAIL_CODE_PEPPER="$(openssl rand -hex 32)"
export AI_SECRET_ENCRYPTION_KEY="$(openssl rand -base64 32)"
mvn spring-boot:run
```

首次启动时 Flyway 创建数据库结构；若同时配置 `APP_ADMIN_USERNAME` 和 `APP_ADMIN_PASSWORD`，应用会在账号不存在时创建管理员。创建后可以移除这两个环境变量，已有账号不会被覆盖。

Redis 默认连接 `127.0.0.1:6379`。SMTP 授权码通过管理员界面加密保存，不应写入环境示例、源码或提交记录。AI Provider Key 同样由管理员或用户在运行时提供。

### 3. 启动前端

```bash
npm ci
npm run dev
```

浏览器访问 `http://localhost:5173`，API 默认位于 `http://127.0.0.1:8080/api`。

### 4. 启动本地 Agent Gateway（可选）

```bash
node scripts/hermes-agent.mjs
```

`scripts/hermes-agent.mjs` 是仓库提供的本地 Gateway 实现，默认监听 `127.0.0.1:8642`。AI Center 的 Agent 模式会把临时 BYOK 仅传给本机 Gateway。Gateway 使用 `hermes -z` 创建一次性子进程，并将 Key 注入该子进程环境；任务完成、失败、超时或 Gateway 退出后子进程终止，Key 不写入项目文件或数据库。

需要网页搜索或 MultiWeb AI 时，还要安装 [`browser-extension/`](browser-extension/README.md) 中的扩展。扩展通过 `/ws/browser-bridge` 与后端连接，只负责浏览器侧任务；网页登录、验证码和服务条款确认始终由用户完成。

Agent 单个生成产物默认最大 100 MB。部署环境可通过 `MAX_UPLOAD_FILE_SIZE` 和 `MAX_UPLOAD_REQUEST_SIZE` 调整服务端上传限制；由于当前 Gateway 使用 Base64 封装产物，请让请求上限至少比文件上限高约三分之一。

## 测试与构建

```bash
npm run build

cd backend
mvn test
```

## Docker Compose

日志滚动、HTTP/AI Trace 关联以及 Loki + Grafana 的使用方式见
[`docs/日志与Trace运维指南.md`](docs/日志与Trace运维指南.md)。

```bash
cd deploy
cp .env.example .env
```

编辑 `.env`，将全部 `replace-with-...` 替换为随机密码。如果目标机器没有宿主机 Nginx，希望前端、后端和 MySQL 全部由 Docker 提供，执行：

```bash
docker compose --profile container-frontend up -d --build
```

随后访问 `http://localhost`。

默认不启用 frontend 容器，避免与已有宿主机 Nginx 争用 80 端口。已有 Nginx 的服务器使用：

```bash
docker compose up -d --build mysql redis markitdown-worker backend
```

前端执行 `npm ci && npm run build` 后，将 `dist/` 发布到宿主机 Nginx 的静态目录，并继续把 `/api/` 代理到 `127.0.0.1:8080`。

`.env`、数据库卷和上传卷都不应提交到 Git。

## 项目结构

```text
Final_Compass/
├── src/                    Vue 前端
├── backend/                Spring Boot API 与 Flyway 迁移
├── services/               隔离运行的附件解析等辅助服务
├── browser-extension/      Agent 搜索与 MultiWeb AI 的 Chrome 桥接扩展
├── scripts/                本地启动、Agent Gateway 与端到端测试脚本
├── public/pdfjs/           PDF.js 运行资源及其许可证
├── pictures/               已获授权的项目成员开场照片
├── deploy/                 通用 Docker / Nginx 模板
├── docs/                   架构学习文档
├── uploads/.gitkeep        上传目录占位符
└── .github/workflows/      持续集成
```

详细学习资料：

- [文档总目录](docs/README.md)
- [Vue 前端运行机制](docs/Vue前端运行机制详解.md)
- [Spring Boot 后端运行机制](docs/SpringBoot后端运行机制详解.md)
- [MySQL 数据库设计](docs/MySQL数据库设计详解.md)
- [课程、老师与接口维护](docs/课程老师与接口维护手册.md)
- [SMTP 邮箱验证与账号发放](docs/SMTP邮箱验证与管理员账号发放设计.md)
- [AI Center Runtime 研发交接文档](docs/AI模块研发交接文档.md)
- [系统架构与设计总览](docs/系统架构与设计总览.md)
- [MarkItDown 附件解析与运行指南](docs/MarkItDown内置附件解析与运行指南.md)
- [项目 Wiki 学习树](https://github.com/storyon403-afk/Final_Compass/wiki/Learning-Tree)

## 贡献

提交代码前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。数据库结构变化必须新增 Flyway 迁移，不得修改已经发布的迁移文件。

安全问题请按 [SECURITY.md](SECURITY.md) 私下报告，不要在公开 Issue 中披露凭据或可利用细节。

## 第三方资源与内容边界

代码采用 MIT License。开场照片由项目成员及其同学拍摄，并已授权随本仓库和应用分发，但不适用 MIT 软件许可证。PDF.js、字体、CMap 和 WASM 文件适用各自许可证，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 及资源目录中的许可证文件。

MIT License 不授权传播第三方试卷、答案、音频、课程资料或用户上传内容。部署者必须自行确认其内容来源与使用权限。

## License

[MIT](LICENSE)
