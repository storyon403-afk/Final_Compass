# liveDoc 与 Scriptorium 维护指南

> 当前基线：2026-08-25，主分支 Flyway V72（仓库总体已到 V73）。

liveDoc 是 AI Center 中的独立创作空间，编辑内核为 VCP Scriptorium。它不是 `apps/web` 内的一组 Vue 组件：主站负责认证、入口和外壳，`apps/livedoc` 负责编辑器静态运行时，二者以同源 iframe 和 `postMessage` 协作。

## 1. 运行边界

```text
apps/web/src/views/LiveDocRuntimeView.vue
        │ iframe: /livedoc/ScriptoriumModules/scriptorium.html
        ▼
apps/livedoc/public/livedoc/
        │ livedoc-web-adapter.js
        ├── IndexedDB / File System Access API
        ├── /api/livedoc/projects
        └── /api/livedoc/export/pdf
```

- 主站路由是 `/ai-center/livedoc`，手机端会在加载 iframe 前阻止进入；电脑和平板可使用。
- 开发时 `apps/livedoc` 监听 `5174`，主站 Vite 配置把 `/livedoc` 代理到该端口，因此消息来源仍可按同源校验。
- 生产构建写入 `dist/livedoc/`，与主站 `dist/` 一起由 Nginx 发布。
- `LiveDocRuntimeView.vue` 只处理进入/退出动画、移动端门控、状态提示和 iframe 消息，不承载编辑器业务逻辑。

## 2. 两种工程格式

| 格式 | 文档模型 | 典型用途 |
| --- | --- | --- |
| `.vdocx` | Markdown-first 连续流文稿 | 笔记、报告、长文档、公式与 Mermaid |
| `.vpptx` | 每页一份 HTML Scene 的演示工程 | 幻灯片、交互演示和可编程页面 |

两者都是 Scriptorium 自有工程容器，不是 OOXML 的 `.docx` / `.pptx` 改名版。编辑时以工程源码为真源，KaTeX、Mermaid、Canvas、SVG 和编辑控件等派生 DOM 不应反写成正文。

编辑器内部模块的模型、事务和渲染细节见 [`apps/livedoc/public/livedoc/ScriptoriumModules/README.md`](../apps/livedoc/public/livedoc/ScriptoriumModules/README.md)。

## 3. 导入、保存与恢复

`livedoc-web-adapter.js` 是浏览器与编辑器内核之间的适配层：

- 可导入 Markdown、TXT、HTML、RTF、DOCX、PPTX 以及 liveDoc 工程文件；导入不代表对原格式进行原位编辑。
- 草稿和资源包可保存在浏览器 IndexedDB；主站明确提示用户定期导出 `.vdocx` / `.vpptx` 到本地。
- 登录用户可通过 `/api/livedoc/projects` 创建、读取、更新和删除账号项目。
- 数据库表 `livedoc_project` 由 V72 创建，保存用户、名称、工程类型、二进制内容、大小和 SHA-256 摘要；单项目上限为 100 MB。
- V65–V71 的 `live_document*` 表记录早期在线文档、修订、协作和 Agent 设计；当前账号工程文件落在 V72 的 `livedoc_project`，维护时不要混为一张表。

## 4. 导出

- HTML 可在浏览器侧直接生成并下载。
- PDF 通过 `POST /api/livedoc/export/pdf` 提交 HTML，`LiveDocService` 再调用配置的 `services/pdf-renderer` 并返回结果；未配置 URL 或 Token 时接口返回 503。
- 工程保存与 PDF/HTML 导出是不同语义：前者保留可继续编辑的真源和资源，后者是交付产物。
- `services/pdf-renderer` 是可独立部署的辅助服务，服务器 Compose 已默认启动它；部署前必须在 `deploy/.env` 中配置独立的 `PDF_RENDERER_TOKEN`。Renderer 只加入 Compose 内网，不向宿主机或公网映射端口。

## 5. 开发与构建

```bash
npm run dev          # 同时启动主站 5173 和 liveDoc 5174
npm run dev:livedoc  # 只启动 liveDoc
npm run build        # 组合构建到 dist/
```

修改静态运行时后至少执行：

```bash
npm run build:livedoc
```

涉及主站嵌入、路由或组合产物时执行根目录 `npm run check`。涉及项目 API 或存储限制时，再从 `services/api` 执行 `mvn test`。

## 6. 修改时的检查清单

- 保持 `/livedoc/ScriptoriumModules/scriptorium.html` 地址稳定，或同步修改主站 iframe、Vite 代理和 Nginx。
- 新增 iframe 消息时继续校验 `event.origin` 与频道 `final-compass:livedoc`。
- 不把渲染 DOM 当作工程真源，不绕过源码区间校验直接全量反序列化。
- 同时验证 `.vdocx` 和 `.vpptx`；演示编辑器与连续流编辑器不是同一渲染路径。
- 改动保存协议时兼顾 IndexedDB 恢复、本地文件导出和账号项目 API。
- 100 MB 是数据库 CHECK 与后端校验共同约束；调整时必须新增 Flyway 迁移，并同步上传请求上限。
