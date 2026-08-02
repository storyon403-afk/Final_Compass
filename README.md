# Finals Compass / 期末指南

Finals Compass 是一个基于 Vue 3、Spring Boot 和 MySQL 的课程复习互助平台。它按照“学院 → 专业 → 课程 → 任课老师 → 老师圈”组织资料、讨论和复习指南，并提供内容审核、匿名身份、反馈问卷与 CET 练习模块。

## 技术栈

- Vue 3、Vue Router、Vite
- Java 21、Spring Boot、Spring MVC、JdbcClient
- MySQL 8、Flyway
- Docker Compose、Nginx

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
mvn spring-boot:run
```

首次启动时 Flyway 创建数据库结构；若同时配置 `APP_ADMIN_USERNAME` 和 `APP_ADMIN_PASSWORD`，应用会在账号不存在时创建管理员。创建后可以移除这两个环境变量，已有账号不会被覆盖。

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

编辑 `.env`，将全部 `replace-with-...` 替换为随机密码，然后执行：

```bash
docker compose up -d --build
```

随后访问 `http://localhost`。

`.env`、数据库卷和上传卷都不应提交到 Git。

## 项目结构

```text
Final_Compass/
├── src/                    Vue 前端
├── backend/                Spring Boot API 与 Flyway 迁移
├── public/pdfjs/           PDF.js 运行资源及其许可证
├── pictures/               已获授权的项目成员开场照片
├── deploy/                 通用 Docker / Nginx 模板
├── docs/                   架构学习文档
├── uploads/.gitkeep        上传目录占位符
└── .github/workflows/      持续集成
```

详细学习资料：

- [Vue 前端运行机制](docs/Vue前端运行机制详解.md)
- [Spring Boot 后端运行机制](docs/SpringBoot后端运行机制详解.md)
- [MySQL 数据库设计](docs/MySQL数据库设计详解.md)
- [课程、老师与接口维护](docs/课程老师与接口维护手册.md)

## 贡献

提交代码前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。数据库结构变化必须新增 Flyway 迁移，不得修改已经发布的迁移文件。

安全问题请按 [SECURITY.md](SECURITY.md) 私下报告，不要在公开 Issue 中披露凭据或可利用细节。

## 第三方资源与内容边界

代码采用 MIT License。开场照片由项目成员及其同学拍摄，并已授权随本仓库和应用分发，但不适用 MIT 软件许可证。PDF.js、字体、CMap 和 WASM 文件适用各自许可证，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 及资源目录中的许可证文件。

MIT License 不授权传播第三方试卷、答案、音频、课程资料或用户上传内容。部署者必须自行确认其内容来源与使用权限。

## License

[MIT](LICENSE)
