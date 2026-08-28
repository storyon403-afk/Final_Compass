# Finals Compass / 期末指南

Finals Compass 是一个面向课程复习、学习资料协作和 AI 辅助创作的开源平台，目前主要服务于树桶学院。项目以“学院 → 专业 → 课程 → 任课老师 → 老师圈”组织课程内容，并提供 CET 学习、问题藤交流、AI Center 与 liveDoc 创作空间。

## 项目现在能做什么

- **课程与老师圈**：按学院、专业和课程组织资料、讨论、老师与复习指南；公共课程可关联多个专业。
- **资料协作**：上传和预览学习资料，支持感谢、讨论、指南引用及管理员审核。
- **CET 学习**：维护四、六级试卷结构、分类练习和听力材料；公开仓库不分发受版权保护的真题资源。
- **问题藤与站内消息**：用户可匿名发布问题、回答并进行树状回复，相关回复会进入站内收件箱；普通用户可联系管理员，管理员可发送定向消息或全站广播，并管理违规主题。
- **AI Center**：Chat 支持知识库 RAG、Redis 对话历史、SSE 和模型降级；本地 Agent 支持浏览器只读命令、状态回调与文件产物；MultiWeb AI 通过 Chrome 扩展复用用户已有网页登录态，并由审核模型汇总结果。
- **AI 运行时治理**：统一管理 Provider、Model、Endpoint、Skill、Tool、MCP、执行 Trace 和反馈优化；支持平台 Key、加密保存的 BYOK 与仅驻留当前请求的临时 BYOK。
- **liveDoc**：内置 VCP Scriptorium 编辑器，可创建连续流文稿（`.vdocx`）和 HTML Scene 演示（`.vpptx`），支持源码与渲染态编辑、导入导出、本地恢复和账号项目保存。
- **账号与邮件**：使用 Redis 管理验证码、有效期、失败次数、限流和短期安全状态，由管理员审核并发放账号；SMTP 配置和模板可在管理端维护，Microsoft Graph OAuth2 Provider 未配置时保持关闭。
- **体验调查**：支持用户提交评分与建议，管理员可动态维护问卷题目并查看回收结果。
- **暂挂体验**：提供全局可访问的短暂休息入口，支持视频预加载和后台配置。

当前主站包含 `CHAT`、`AGENT`、`MULTI_WEB_AGENT` 三种通用 AI Runtime，以及独立的 VCP 介绍页和 liveDoc 创作空间。旧 Workflow 路由只保留重定向，历史 Skill/Workflow 数据表不是面向用户的可调用入口。仓库不包含平台生产 API Key，也不捆绑或自动部署本地大模型。

## 技术栈

- Vue 3、Vue Router、Vite 8、npm workspaces
- Java 21、Spring Boot、Spring MVC、JdbcClient
- MySQL 8、Flyway（当前迁移至 V75）
- Redis 7
- Chrome Manifest V3、WebSocket 浏览器桥接
- Python 3.10+、Microsoft MarkItDown（可选附件解析 Worker）
- Docker Compose、Nginx、Loki、Grafana Alloy

## 系统组成

```text
Vue 主站（apps/web） ───────┐
                           ├── HTTP / JSON / SSE ── Spring Boot API
liveDoc（apps/livedoc）────┘                         ├── MySQL + Flyway
                                                    ├── Redis
                                                    ├── SMTP / Graph
                                                    ├── AI Provider API
                                                    ├── MarkItDown Worker
                                                    └── Agent Gateway ── Chrome 扩展
```

开发时主站运行在 `5173`，liveDoc 独立开发服务器运行在 `5174`；主站通过代理以同源的 `/livedoc/` 路径嵌入编辑器。生产构建会把两个前端组合到根目录 `dist/`。

## 本地开发

### 环境要求

- Node.js 22.18+
- Java 21
- Maven 3.9+
- MySQL 8
- Redis 7
- Python 3.10+（仅运行 MarkItDown Worker 时需要）

### 1. 克隆并安装依赖

未配置 GitHub SSH Key 时使用 HTTPS：

```bash
git clone https://github.com/storyon403-afk/Final_Compass.git
cd Final_Compass
npm ci
```

已经配置 GitHub SSH Key 时使用 SSH：

```bash
git clone git@github.com:storyon403-afk/Final_Compass.git
cd Final_Compass
npm ci
```

准备参与开发时，请先阅读 [贡献流程](CONTRIBUTING.md) 和项目 Wiki 的 [学习树](https://github.com/storyon403-afk/Final_Compass/wiki/Learning-Tree)。

### 2. 准备数据库

```sql
CREATE DATABASE finals_compass CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'finals'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON finals_compass.* TO 'finals'@'localhost';
```

在仓库根目录创建不提交的 `.env`：

```dotenv
DB_URL=jdbc:mysql://localhost:3306/finals_compass?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
DB_USER=finals
DB_PASSWORD=change-me
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=replace-with-at-least-12-random-characters
MAIL_SECRET_ENCRYPTION_KEY=replace-with-a-base64-encoded-32-byte-key
EMAIL_CODE_PEPPER=replace-with-a-random-pepper
AI_SECRET_ENCRYPTION_KEY=replace-with-a-base64-encoded-32-byte-key
```

随机密钥可用 `openssl rand -base64 32` 或 `openssl rand -hex 32` 生成。首次启动时 Flyway 建表；同时配置管理员用户名和密码时，后端只会在账号不存在时创建管理员，不会覆盖已有账号。创建成功后可以移除这两个管理员环境变量。

### 3. 启动完整开发环境

```bash
./scripts/dev.sh
```

该脚本会检查 JDBC 地址，以 `mvn clean` 清除旧迁移残留，然后启动 API、Vue 主站和 liveDoc。访问：

- 主站：`http://localhost:5173`
- liveDoc 独立入口：`http://localhost:5174/livedoc/`
- API：`http://127.0.0.1:8080/api`

也可以分开运行：

```bash
cd services/api
export DB_URL='jdbc:mysql://localhost:3306/finals_compass?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
export DB_USER='finals'
export DB_PASSWORD='change-me'
export MAIL_SECRET_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export EMAIL_CODE_PEPPER="$(openssl rand -hex 32)"
export AI_SECRET_ENCRYPTION_KEY="$(openssl rand -base64 32)"
mvn clean spring-boot:run

# 另一个终端，在仓库根目录
npm run dev
```

根命令 `npm run dev` 会同时启动两个前端；`npm run dev:web` 和 `npm run dev:livedoc` 可单独启动。

Redis 默认连接 `127.0.0.1:6379`。SMTP 授权码与 AI Provider Key 应通过运行时配置或 Git 外环境变量提供，不得写入仓库。

如果旧开发库只报告 V29、V31 的已知 checksum mismatch，请先备份并核对迁移内容，再运行 `./scripts/flyway-repair.sh`；不要对未知校验失败直接 repair，也不要修改已经发布的迁移文件。

### 4. 可选运行时

本地 Agent Gateway：

```bash
node scripts/hermes-agent.mjs
```

Gateway 默认监听 `127.0.0.1:8642`，通过 `hermes -z` 创建一次性子进程。Agent 模式的临时 BYOK 只注入该子进程环境；任务结束、失败、超时或 Gateway 退出后子进程终止，Key 不写入项目文件或数据库。

网页搜索或 MultiWeb AI 还需要安装 [Chrome 扩展](browser-extension/README.md)。扩展通过 `/ws/browser-bridge` 与后端连接，只负责浏览器侧任务；登录、验证码和第三方服务条款确认始终由用户完成。

Agent 单个产物默认最大 100 MB。部署环境可通过 `MAX_UPLOAD_FILE_SIZE` 与 `MAX_UPLOAD_REQUEST_SIZE` 调整限制；当前 Gateway 使用 Base64 封装产物，请让请求上限至少比文件上限高约三分之一。

MarkItDown Worker 的安装、Token 和限制见 [附件解析指南](docs/MarkItDown内置附件解析与运行指南.md)。

## 测试与构建

```bash
npm run check

cd services/api
mvn test

cd ../markitdown-worker
.venv/bin/pytest -q
```

`npm run check` 会先执行前端纯业务与 SSE 解析测试，再构建主站和 liveDoc，输出分别位于 `dist/` 和 `dist/livedoc/`。GitHub Actions 还会验证 MarkItDown Worker、在空 MySQL 库执行全部 Flyway 迁移并校验，以及构建 backend、frontend、Worker、PDF Renderer 四个 Docker 镜像。Agent 与浏览器桥接的手工联调见 [E2E 指南](scripts/test-e2e.md)。

## Docker Compose

```bash
cd deploy
cp .env.example .env
```

将 `.env` 中全部 `replace-with-...` 替换为随机值。生产后端会在启动时校验数据库和 Redis 密码、邮件 Pepper、AI/邮件加密 Key、Worker Token、站点 URL、浏览器扩展 Origin 及可选 Microsoft Graph 配置；存在空值、示例值、通配 Origin 或格式错误时会拒绝启动。全容器部署：

```bash
docker compose --profile container-frontend up -d --build
```

随后访问 `http://localhost`。`frontend` 容器默认不启用，以免与宿主机 Nginx 争用 80 端口。后端镜像固定以 UID/GID `10001` 的非 root 用户运行；全新服务器首次创建 `uploads` 和 `backend_logs` 命名卷时会继承镜像目录权限，无需在宿主机手工创建同 UID 用户或执行 `chown`。后端只接受 `TRUSTED_PROXY_ADDRESSES` 中代理提供的 `X-Forwarded-For`；默认值覆盖宿主机 Nginx 和固定为 `172.28.0.10` 的容器 frontend。

已有宿主机 Nginx 时，只启动服务端组件：

```bash
docker compose up -d --build mysql redis markitdown-worker pdf-renderer backend
```

数据库一致性备份与隔离恢复演练分别使用 `./scripts/db-backup.sh` 和 `./scripts/db-restore-drill.sh BACKUP.sql.gz`，完整周期、RPO/RTO 和灾难恢复约束见[数据库备份与恢复演练](docs/数据库备份与恢复演练.md)。

随后在仓库根目录执行 `npm ci && npm run build`，把组合后的 `dist/` 发布到静态目录，并将 `/api/` 和 `/ws/` 代理给后端。

备案前在 Ubuntu 本机或受控局域网测试时，使用 `deploy/nginx/finals-compass-local-http.conf`；它接受 `http://localhost`、`http://finalscompass` 和本机局域网 IP，并代理到宿主机 `127.0.0.1:8080` 的后端。此模式需设置 `SESSION_COOKIE_SECURE=false`，且不应暴露到公网。

备案后绑定域名与 HTTPS 时，以 `deploy/nginx/finals-compass-https.conf.example` 为模板，将全部 `__DOMAIN__` 替换为完整的实际域名。`FinalsCompass` 可作为产品名或局域网主机名，但不是可签发公网证书的完整域名。DNS 指向当前公网 IP 并签发证书后，把站点 URL 更新为 HTTPS，同时设置 `SESSION_COOKIE_SECURE=true`；以后更换服务器 IP 只需更新 DNS。

日志滚动、Trace 关联及 Loki + Grafana 的配置见 [日志与 Trace 运维指南](docs/日志与Trace运维指南.md)。`.env`、数据库卷、日志与上传文件均不得提交。

## 仓库结构

```text
Final_Compass/
├── apps/
│   ├── web/                 Vue 主站
│   └── livedoc/             Scriptorium 独立前端与静态运行时
├── services/
│   ├── api/                 Spring Boot API 与 Flyway 迁移
│   ├── markitdown-worker/   附件解析服务
│   └── pdf-renderer/        liveDoc 可选 PDF 渲染服务
├── browser-extension/       Agent 搜索与 MultiWeb AI 浏览器桥
├── scripts/                 开发、迁移维护与联调脚本
├── deploy/                  Docker、Nginx 与可观测性配置
├── docs/                    当前指南与历史设计快照
├── dist/                    可重新生成的组合前端产物
├── uploads/                 本地运行数据（只提交 .gitkeep）
└── .github/workflows/       持续集成
```

进一步阅读：

- [文档总目录](docs/README.md)
- [仓库结构与应用边界](docs/仓库结构与应用边界.md)
- [liveDoc / Scriptorium 维护指南](docs/liveDoc与Scriptorium维护指南.md)
- [AI 模块研发交接](docs/AI模块研发交接文档.md)
- [课程、老师与接口维护](docs/课程老师与接口维护手册.md)
- [Vue 前端运行机制](docs/Vue前端运行机制详解.md)
- [Spring Boot 后端运行机制](docs/SpringBoot后端运行机制详解.md)
- [MySQL 数据库设计](docs/MySQL数据库设计详解.md)
- [项目 Wiki 学习树](https://github.com/storyon403-afk/Final_Compass/wiki/Learning-Tree)

## 开源与内容边界

公开仓库包含源代码、数据库迁移、测试和通用部署模板，不包含生产凭据、用户上传文件、数据库备份、CET 真题/答案/听力或未经授权的媒体。首次管理员由环境变量显式创建，Flyway 不写入固定生产账号。

代码采用 [MIT License](LICENSE)。开场照片经项目成员及其同学授权随仓库和应用分发，但不适用 MIT 软件许可证；PDF.js、字体、CMap、WASM 等适用各自许可证，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。MIT License 不授权传播第三方试卷、答案、音频、课程资料或用户上传内容，部署者必须自行确认内容来源与使用权限。

参与开发前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。数据库变化必须新增 Flyway 迁移，不得修改已发布的迁移文件。安全问题请按 [SECURITY.md](SECURITY.md) 私下报告，不要在公开 Issue 中披露凭据或可利用细节。
