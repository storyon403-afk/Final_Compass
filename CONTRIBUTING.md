# Contributing

感谢你参与 Finals Compass。

## 开发流程

1. 从最新 `main` 创建功能分支，例如 `feature/course-favorite`。
2. 不提交 `.env`、数据库导出、日志、上传文件或真实账号。
3. 保持一次提交只解决一个清晰问题，提交消息建议使用 `feat:`、`fix:`、`docs:`、`test:`。
4. 提交 Pull Request，说明问题、方案、数据影响和验证结果。
5. 至少一位协作者审查且 CI 通过后再合并。

## 数据库规则

- 已经进入 `main` 的 Flyway 迁移不可修改。
- 新结构使用下一个版本号，例如 `V16__course_favorites.sql`。
- 迁移必须能在空数据库执行，也要考虑已有数据。
- 不在迁移中写真实账号、密码、邮箱、手机号或受版权保护资料。

## 提交前检查

```bash
npm ci
npm run build

cd services/api
mvn test
```

涉及接口字段时，同时检查 `apps/web/src/api.js`、Vue 使用方、Java DTO 和 SQL 映射。涉及上传功能时，不要把测试文件加入 Git。

## 部署模式

- 默认 Compose 只启动 MySQL 和 backend，适合已有宿主机 Nginx 的服务器。
- 只有需要容器内 Nginx 时才使用 `--profile container-frontend`。
- 生产部署不得同时让宿主机 Nginx 和 frontend 容器绑定 80 端口。

## 报告问题

普通缺陷可以提交 Issue。安全漏洞请遵循 `SECURITY.md`，不要公开漏洞细节或凭据。
