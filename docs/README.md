# Finals Compass 文档总目录

本目录集中记录 Finals Compass 的运行机制、数据设计、功能模块与维护约束。文档按学习顺序组织；每篇文档保持独立，可直接通过标题链接进入对应主题。

## 一、项目全景

| 文档 | 内容摘要 | 适合阶段 |
| --- | --- | --- |
| [系统运行全景图与数学网络模型](系统运行全景图与数学网络模型.md) | 浏览器、Nginx、Vue、Spring Boot、MySQL 与文件存储之间的完整调用链，并用图模型解释系统关系。 | 首次了解项目 |
| [Vue 前端运行机制详解](Vue前端运行机制详解.md) | `src/` 目录职责、组件通信、响应式状态、路由与 API 调用。 | 阅读或修改前端前 |
| [Spring Boot 后端运行机制详解](SpringBoot后端运行机制详解.md) | Controller、Service、认证拦截器、JdbcClient 和异常响应的协作方式。 | 阅读或修改后端前 |
| [MySQL 数据库设计详解](MySQL数据库设计详解.md) | 表关系、范式、主外键、索引、课程跨专业复用与 Flyway 演进原则。 | 修改数据结构前 |

## 二、核心业务模块

| 文档 | 内容摘要 |
| --- | --- |
| [课程、老师与接口维护手册](课程老师与接口维护手册.md) | 课程、专业、老师等基础数据的接口与维护边界。 |
| [SMTP 邮箱验证与管理员账号发放设计](SMTP邮箱验证与管理员账号发放设计.md) | Redis 验证码、动态 SMTP、邮件模板、临时密码与顺序账号分配。 |
| [暂挂体验模块设计与维护](暂挂体验模块设计与维护.md) | 全局暂挂入口、视频预加载、管理员配置和恢复交互。 |

## 三、AI Beta

| 文档 | 内容摘要 |
| --- | --- |
| [AI 活跃度资格与 Skill 安全架构设计](AI活跃度资格与Skill安全架构设计.md) | 活跃积分、月度资格、平台 Key/BYOK、安全边界与 Skill 抽象。 |
| [Finals Compass AI V2：Skill 编排设计与扩展指南](FinalsCompass_AI_V2_Skill编排设计与扩展指南.md) | Agent 编排、Skill Registry、凭据解析和 Provider Gateway 的接口关系。 |
| [Finals Compass AI V3：真实调用与临时拍题设计](FinalsCompass_AI_V3_真实调用与临时拍题设计.md) | DeepSeek/OpenAI 真实调用、手机拍题的请求级内存链路与平台用量保护。 |
| [Finals Compass AI V4：Gemini 视觉与 DeepSeek 解题编排](FinalsCompass_AI_V4_Gemini视觉与DeepSeek解题编排.md) | Gemini 题面识别、用户意图路由、DeepSeek V4 Flash 解题及双模型安全边界。 |
| [MarkItDown 内置附件解析与运行指南](MarkItDown内置附件解析与运行指南.md) | 文档、图片和音频的标准化解析链路、Worker 限制与故障处理。 |

## 四、基础设施与安全状态

| 文档 | 内容摘要 |
| --- | --- |
| [Redis 缓存与安全状态模块设计](Redis缓存与安全状态模块设计.md) | Key 命名空间、TTL、验证码限流、失效策略和不可缓存边界。 |

部署方式、贡献流程和 Flyway 操作原则同时维护在项目 [Wiki](https://github.com/storyon403-afk/Final_Compass/wiki)。仓库文档描述实现细节，Wiki 描述学习路径和设计思想，两者互相链接但不重复保存生产凭据或私有运维数据。
