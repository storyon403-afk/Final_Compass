# Finals Compass / 期末指南

Finals Compass 是一个面向高校课程复习与学习协作的开源平台。系统以“学院 → 专业 → 课程 → 任课老师 → 老师圈”组织课程、资料、讨论和复习指南，同时提供英语等级考试内容、邮箱验证与内测账号发放，以及可扩展的 AI Beta 学习分析能力。

## 当前能力

- **课程知识网络**：课程代码全局唯一，公共专业课可以关联多个专业；资料、老师、讨论和复习指南围绕课程组织。
- **学习资料协作**：支持资料上传、在线预览、感谢、讨论、指南引用和管理员审核。
- **英语等级考试**：提供 CET 内容导航、试卷展示与听力相关能力；公开仓库不分发受版权保护的试题资源。
- **身份与账号发放**：通过 Redis 管理邮箱验证码、有效期、失败次数和限流，由管理员在邮箱验证成功后确认发放账号。
- **动态邮件服务**：管理员可以配置 SMTP 和邮件模板；同时预留 Microsoft Graph OAuth2 Provider，未配置时保持关闭。
- **FinalsCompass AI Beta**：具备活跃度资格、平台 Key/BYOK、Agent 与 Skill 抽象、多供应商 Gateway，以及图片、文档和音频附件入口。
- **暂挂体验**：提供全局可访问的短暂休息交互，支持视频预加载和管理员配置。

AI Beta 当前属于可扩展基础版本。仓库提供编排、安全边界和 Provider 接口，不包含平台生产 API Key，也不部署本地大模型。

## 技术栈

- Vue 3、Vue Router、Vite
- Java 21、Spring Boot、Spring MVC、JdbcClient
- MySQL 8、Flyway
- Redis 7
- Python 3.10+、Microsoft MarkItDown（可选附件解析 Worker）
- Docker Compose、Nginx

## 运行结构

```text
浏览器 / Vue 3
      │ HTTP / JSON
      ▼
Spring Boot API ───── Redis（验证码、限流、短期安全状态）
      │       ├───── SMTP / Microsoft Graph（邮件发送）
      │       ├───── AI Provider API（平台 Key 或 BYOK）
      │       └───── MarkItDown Worker（附件标准化）
      ▼
MySQL + Flyway（账号、课程、内容、审核与审计事实）
```

## 开源版说明

公开仓库只包含源代码、数据库结构、自动测试和通用部署模板，不包含：

- 生产账号、密码、Token 或服务器信息
- 用户上传文件和数据库备份
- CET 真题、答案和听力文件
- 未经授权的图片或其他媒体文件

首次管理员由环境变量显式创建。应用不会在 Flyway 迁移中写入固定账号。

## 本地启动

### 1. 准备环境

- Node.js 22.18 或更高版本
- Java 21
- Maven 3.9 或更高版本
- MySQL 8
- Redis 7（邮箱验证功能需要）
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

## 测试与构建

```bash
npm run build

cd backend
mvn test
```

## Docker Compose

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
- [AI 活跃度与 Skill 安全架构](docs/AI活跃度资格与Skill安全架构设计.md)
- [AI V2 Skill 编排与扩展](docs/FinalsCompass_AI_V2_Skill编排设计与扩展指南.md)
- [项目 Wiki 学习树](https://github.com/storyon403-afk/Final_Compass/wiki/Learning-Tree)

## 贡献

提交代码前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。数据库结构变化必须新增 Flyway 迁移，不得修改已经发布的迁移文件。

安全问题请按 [SECURITY.md](SECURITY.md) 私下报告，不要在公开 Issue 中披露凭据或可利用细节。

## 第三方资源与内容边界

代码采用 MIT License。开场照片由项目成员及其同学拍摄，并已授权随本仓库和应用分发，但不适用 MIT 软件许可证。PDF.js、字体、CMap 和 WASM 文件适用各自许可证，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 及资源目录中的许可证文件。

MIT License 不授权传播第三方试卷、答案、音频、课程资料或用户上传内容。部署者必须自行确认其内容来源与使用权限。

## License

[MIT](LICENSE)
