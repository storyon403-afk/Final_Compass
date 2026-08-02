# Spring Boot 后端运行机制详解（Java / JavaFX 视角）

这份文档不讲完整的 Spring Boot 语法，而是回答：**这个项目的 Java 后端从启动到处理前端请求，究竟如何运行？**

若熟悉 Java 和 JavaFX，可以先建立对应关系：

| JavaFX | 本项目 Spring Boot |
|---|---|
| `Application.start()` | `FinalsCompassApplication.main()` |
| FXML Controller | `@RestController` |
| 按钮事件处理器 | `@GetMapping`、`@PostMapping` 方法 |
| Controller 持有 Service | Spring 构造器注入 Service |
| 更新 Observable 属性 | 返回对象，Jackson 转为 JSON |
| DAO / 本地文件 | `JdbcClient` / 上传目录 |

JavaFX Controller 处理本机 GUI 事件；Spring Controller 处理网络 HTTP 请求。每次请求都是一次独立的方法调用，结果随后返回浏览器。

---

## 1. 后端在系统中的位置

```text
Vue 页面
  │ fetch('/api/...')：JSON 或 FormData
  ▼
Vite 开发代理 / 生产 Nginx
  ▼
Spring Boot（内嵌 Tomcat，127.0.0.1:8080）
  ├─ Filter：追踪号、状态码、耗时
  ├─ Interceptor：登录令牌检查
  ├─ Controller：HTTP 参数和业务流程
  ├─ Service：认证、匿名身份、验证码
  ├─ JdbcClient：SQL
  └─ 文件系统：资料、试卷和音频
       ▼
     MySQL
```

开发时浏览器访问 `localhost:5173`，Vite 把 `/api` 代理到 `127.0.0.1:8080`。生产时 Nginx 提供 Vue 静态文件，并把 `/api/` 转发到 Spring Boot。前端从不直接连接 MySQL，数据库凭据、SQL 和权限判断只存在于后端。

---

## 2. 启动入口与 Spring 容器

入口为 `backend/src/main/java/cn/finalscompass/FinalsCompassApplication.java`：

```java
@SpringBootApplication
public class FinalsCompassApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinalsCompassApplication.class, args);
    }
}
```

`@SpringBootApplication` 主要完成三件事：

1. 将该类作为配置入口。
2. 按 Maven 依赖自动配置 Web、数据库、Jackson、Flyway 等组件。
3. 从 `cn.finalscompass` 向下扫描 `@Component`、`@Service`、`@RestController`、`@Configuration` 等类。

扫描到的对象由 Spring 创建和管理，称为 Bean。比如 Controller 构造器需要 `AuthService`，Spring 会将已经创建的 Service 传入，不需要手动 `new`：

```java
public AuthController(AuthService auth, BetaAccessService betaAccess) {
    this.auth = auth;
    this.betaAccess = betaAccess;
}
```

这些 Bean 默认基本是单例。不能把“当前用户”保存在 Controller 成员变量里，否则并发请求会覆盖。项目将用户放在本次 `HttpServletRequest` 的 attribute 中。

---

## 3. Maven 依赖提供什么能力

`backend/pom.xml` 的主要依赖：

- `spring-boot-starter-web`：Spring MVC、Tomcat、JSON。
- `spring-boot-starter-jdbc`：连接池、事务、`JdbcClient`。
- `spring-boot-starter-validation`：`@Valid`、`@NotBlank`、`@Size`。
- `spring-security-crypto`：这里只使用 BCrypt，并未启用完整 Spring Security Web 过滤链。
- `flyway-mysql`：数据库版本迁移。
- `mysql-connector-j`：MySQL JDBC 驱动。
- `spring-boot-starter-test`：JUnit、MockMvc 等测试能力。

本项目的请求认证是自定义 `AuthenticationInterceptor`，不要把 BCrypt 依赖误解成“整个项目由 Spring Security 自动保护”。

---

## 4. 配置如何进入程序

`backend/src/main/resources/application.yml` 配置数据库、Flyway、上传大小、静态资源、监听地址和上传目录。

`${DB_URL:默认值}` 表示优先读环境变量，不存在才使用本机默认值。

| 配置 | 作用 |
|---|---|
| `spring.datasource.*` | 数据库连接池和 `JdbcClient` |
| `spring.flyway.enabled` | 启动时检查并执行迁移 |
| `spring.servlet.multipart.*` | 上传请求大小限制 |
| `server.address` / `port` | `127.0.0.1:8080` |
| `app.upload-dir` | 上传文件根目录 |

生产数据库密码通过环境变量注入，不应写入仓库。

---

## 5. 一次请求的完整生命周期

以 `GET /api/courses` 为例：

1. Vue 的公共 API 函数发出请求并附带登录 token。
2. Vite 或 Nginx 将请求转发给 Spring Boot。
3. Tomcat 构造 `HttpServletRequest` 和 `HttpServletResponse`。
4. `RequestTraceFilter` 生成追踪号并开始计时。
5. `AuthenticationInterceptor` 从 Bearer Header 或 Cookie 读取 token。
6. `AuthService.authenticate()` 联表查询会话和用户，把用户放入 request attribute。
7. Spring MVC 按路径和 HTTP 方法找到 `CatalogController.courses()`。
8. Controller 用 `JdbcClient` 查询 MySQL并映射为 Java record 或 Map。
9. Jackson 把返回对象序列化为 JSON。
10. Vue 收到 JSON，更新 `ref` / `reactive`，模板重新渲染。

前端并没有直接调用 Java 方法。HTTP 路径、请求体、状态码和 JSON 字段才是两端的契约。

---

## 6. Filter、Interceptor、Controller

### `RequestTraceFilter`

Servlet Filter 位于 Spring MVC 之前，负责为请求生成 12 位 trace id，在响应头写入 `X-Trace-Id`，并记录方法、路径、状态码和耗时。它只做观测，不做业务。

### `AuthenticationInterceptor`

Interceptor 在 Controller 前检查登录。它支持：

- `Authorization: Bearer <token>`：普通 JSON 请求。
- `finals_compass_session` Cookie：浏览器原生音频或文件请求。

`<audio src="...">` 不方便自定义 Authorization Header，所以登录后可用 `/api/auth/stream-cookie` 建立 HttpOnly Cookie。

无效 token 直接返回 401，Controller 不执行；有效用户写入：

```java
request.setAttribute(AuthService.REQUEST_USER, currentUser);
```

### `WebConfig`

它将拦截器应用于 `/api/**`，当前只排除登录、注册、验证码流程和健康检查。新增公开接口时才应明确加入排除列表；包括公告读取在内的其余接口默认要求登录。

### Controller 参数

`@RestController` 表示返回值直接作为响应体，不寻找 HTML 模板。

```java
@PathVariable long id           // /items/123
@RequestParam String level      // ?level=CET6
@RequestBody CreateCourse body  // JSON
MultipartFile file              // multipart/form-data
HttpServletRequest request      // 当前请求上下文
```

---

## 7. `ApiModels` 与 JSON 契约

`model/ApiModels.java` 主要包含 Java record，充当 DTO。它不是 JPA 实体，本项目没有 `@Entity`。

```java
public record LoginRequest(
    @NotBlank String username,
    @NotBlank String password
) {}
```

前端的 `{"username":"beta01","password":"..."}` 会由 Jackson 构造成 record。Controller 参数上的 `@Valid` 触发约束检查；空用户名在业务方法执行前就会被拒绝。

返回 record、List 或 Map 时，Jackson 反向转换成 JSON。Java 字段名和前端读取的 JSON 字段名构成接口契约，改动时必须两端一起检查。

---

## 8. 各 Controller 负责什么

### `AuthController`

负责内测验证码申请/验证、登录、注册、改密码、退出和流媒体 Cookie：

- `POST /api/auth/beta-access/request`
- `POST /api/auth/beta-access/verify`
- `POST /api/auth/login`
- `POST /api/auth/stream-cookie`
- `POST /api/auth/register`
- `POST /api/auth/change-password`
- `POST /api/auth/logout`

### `CatalogController`

负责学院、专业、课程和老师目录：

- `GET /api/courses`、`GET /api/courses/colleges`
- 管理员新增学院或课程
- `GET /api/courses/{courseSlug}/teachers`
- 管理员给课程添加老师

课程代码唯一；同一课程通过 `course_program` 可属于多个专业。

### `CircleController`

前缀为 `/api/circles/{courseSlug}/{teacherSlug}`，负责一个老师圈中的资料、感谢、讨论、汇总、复习指南和指南投稿。

上传文件本体写入 `uploads/`，数据库保存相对路径、原文件名、贡献者、圈子关系和审核状态。公开查询只返回已审核内容。

### `CetController`

负责 CET4/CET6 套卷、结构化题目、完整试卷附件和音频。普通用户读取，管理员维护套卷、题目和文件。文件响应是二进制 `Resource`，不是 JSON。

### `SurveyController`

负责问卷读取、提交和管理员维护。提交时先写 `survey_submission`，再逐题写 `survey_answer`；事务保证不会留下半份问卷。答案保存 `question_snapshot`，题目以后改变也能还原当时内容。

### `IdentityController`

为当前账号取得或创建唯一匿名身份。账号与匿名名一对一，客户端不能传入 user id 或指定昵称冒充别人。

### `SystemController`

负责健康检查、公告、后台统计、内容审核、内测申请列表和管理员删除讨论。审核主要更新状态，并写入 `moderation_audit`，而不是直接物理删除。

### `ApiExceptionHandler`

集中把 `IllegalArgumentException` 和 validation 异常转成 400 JSON。401 表示未登录，403 表示无权限，404 表示资源不存在，410 表示验证码过期，429 表示尝试过多。

### `SpaController`

当 Spring Boot 提供前端静态文件时，将 Vue 深层路由转发到 `index.html`，再由 Vue Router 解析。生产若由 Nginx 做 SPA fallback，它是额外保障。

---

## 9. 三个 Service 的职责

### `AuthService`

- BCrypt 验证和生成密码哈希。
- 登录后创建 UUID token，写入 `login_session`，有效期 7 天。
- 认证时联查 session 和 `app_user`。
- 从 request attribute 读取当前用户。
- `requireAdmin()` 统一检查管理员权限。
- 改密码和退出。

数据库保存 BCrypt 哈希，不保存可还原的明文密码。Token 是临时通行证，前端必须妥善保存。

### `BetaAccessService`

负责邮箱申请记录、六位验证码、30 分钟有效期、失败次数和验证状态。验证使用 `SELECT ... FOR UPDATE` 锁住记录，避免并发请求同时通过。

事务配置 `noRollbackFor = ResponseStatusException.class` 很关键：验证码输错既要返回错误，又要提交失败次数；默认回滚会把计数一并撤销。

目前验证码业务状态已经自动化，但是否真正发邮件取决于是否接入外部邮件发送服务。

### `AnonymousIdentityService`

生成随机词语加数字的匿名名。`INSERT IGNORE` 配合唯一约束，确保一个账号只有一个身份，并处理并发首次访问。

---

## 10. `JdbcClient` 如何访问 MySQL

Spring 根据 datasource 配置创建连接池和 `JdbcClient` Bean，再通过构造器注入。

```java
return jdbc.sql("SELECT id,slug,name FROM course WHERE active=TRUE")
    .query(Course.class)
    .list();
```

```java
int changed = jdbc.sql("UPDATE discussion SET status=:status WHERE id=:id")
    .param("status", "REMOVED")
    .param("id", id)
    .update();
```

命名参数由 JDBC 预编译传值，不能用字符串拼接用户输入。`SystemController` 的动态表名只来自后端写死的白名单分支。

| 调用 | 结果 |
|---|---|
| `.query(Type.class).list()` | 多行对象列表 |
| `.query(Type.class).single()` | 恰好一行 |
| `.query(Type.class).optional()` | 可能没有 |
| `.query().listOfRows()` | 多行 Map |
| `.query().singleRow()` | 单行 Map |
| `.update()` | 受影响行数 |

常见 SQL `snake_case` 列可映射到 record 的 `camelCase` 参数。自增主键用 `GeneratedKeyHolder` 取得，例如问卷主记录插入后再写答案。

---

## 11. 事务

`@Transactional` 的基本过程：

```text
代理开启事务 → 执行一组 SQL
                 ├─ 正常返回：COMMIT
                 └─ 运行时异常：ROLLBACK
```

典型场景包括课程和专业关联一起写入、问卷和全部答案一起写入。Spring 事务通过代理工作，同一个对象内部 `this.someMethod()` 调用可能绕过事务代理。

数据库事务不能自动撤销已经写入磁盘的文件。上传时若文件写成功而 SQL 失败，可能产生孤立文件；正式扩展应加入补偿删除、定期一致性检查或对象存储方案。

---

## 12. Flyway 管理数据库结构

迁移位于 `backend/src/main/resources/db/migration/`。`V15__...sql` 表示版本 15。

启动时 Flyway 检查历史表：旧版本不重复执行，新版本按顺序执行，失败时阻止应用在错误结构上启动。已上线迁移不要直接修改，应新增 V16、V17。

- V1：课程、老师、资料、讨论。
- V2：账号和登录会话。
- V3–V5：审核、感谢、匿名账号绑定。
- V6–V7：学院专业导航、复习指南。
- V8–V10：内测验证、问卷。
- V11–V14：CET、公告和附件。
- V15：课程与专业多对多。

生产业务账号权限较小，未必能建表。新迁移应先备份，用具备 DDL 权限的迁移账号执行，再让业务账号运行应用。

---

## 13. 主要数据关系

```text
app_user ──< login_session
    ├── 1:1 anonymous_user
    └──< survey_submission ──< survey_answer

college ──< course_program >── course ──< teacher_course >── teacher
                                      │
                                      └─ course + teacher 组成老师圈
                                           ├──< resource ──< resource_thank
                                           ├──< discussion
                                           ├── study_guide
                                           └──< guide_submission

cet_paper ──< cet_item
     └──< cet_paper_asset
```

`teacher_course` 和 `course_program` 是多对多中间表。URL 使用稳定 slug，而不是中文显示名。内容用状态控制可见性：新内容通常为 `PENDING`，通过后变为 `PUBLISHED`、`VISIBLE` 或 `APPROVED`。

---

## 14. 与 Vue 如何交互

前端公共封装位于 `src/api.js`，负责 JSON Header、Bearer token、响应解析和统一错误。组件表达业务意图，不关心数据库。

```text
点击课程
→ Vue Router 改变 URL
→ View 读取 route.params.courseId
→ api.js 请求 /api/courses/{slug}/teachers
→ CatalogController 查询 teacher_course
→ Jackson 返回 JSON
→ Vue 保存到响应式状态
→ 页面更新
```

上传使用 `FormData` 与 `MultipartFile`；下载和音频使用二进制响应。改 Java record、SQL alias 或 JSON 结构时，要搜索前端如何读取对应字段。

---

## 15. 错误和排查顺序

1. 浏览器 Network 检查路径、方法、请求体、状态码。
2. 查看响应是 400、401、403、404、410、429 还是 500。
3. 用 `X-Trace-Id` 对照后端日志。
4. 判断是 Interceptor 拒绝，还是已进入 Controller。
5. 检查 SQL 结果和 Flyway 版本。
6. 文件功能同时核对数据库相对路径与磁盘文件。

`/api/system/health` 会执行 `SELECT 1`，所以 200 同时表示 Tomcat 和基本数据库连接可用。

---

## 16. 新增功能的标准步骤

以“课程收藏”为例：

1. 定义 HTTP 路径、方法、请求和返回字段。
2. 新增 Flyway 迁移和唯一约束。
3. 在 `ApiModels` 增加需要的 record。
4. 在合适 Controller 接收 HTTP 参数；可复用复杂逻辑提取到 Service。
5. 用 `auth.current(request)` 取得用户，不能相信前端传来的 user id。
6. 用命名参数 SQL，并考虑重复请求的幂等性。
7. 在 `src/api.js` 加调用，Vue 更新状态。
8. 增加测试，运行 `mvn test` 和 `npm run build`。
9. 更新文档和更新日志，生产迁移前备份。

位置判断：HTTP 属于 Controller；复用业务规则属于 Service；数据形状属于 ApiModels；结构变化属于 Flyway；当前项目的数据读写直接用 JdbcClient，SQL 继续增多时可抽出 Repository。

---

## 17. 当前设计的优点与边界

优点是代码量小、SQL 直观、构造器依赖清晰、迁移可追踪，适合当前封闭内测规模。

需要知道的边界：Controller 中 SQL 较多；自定义认证缺少完整安全框架配套；文件和数据库不是原子事务；Map 响应缺少编译期字段约束；验证码仍需邮件服务；正式扩大使用前还需速率限制、文件扫描、对象存储、统一审计和恢复演练。

---

## 18. 推荐阅读顺序

1. `application.yml`、`pom.xml`：环境与能力来源。
2. `FinalsCompassApplication`：启动和扫描。
3. Filter、WebConfig、Interceptor：请求入口。
4. `AuthController` + `AuthService`：JSON、认证、session。
5. `CatalogController`：JdbcClient 和多对多。
6. `CircleController`：上传、下载、审核。
7. `SurveyController`：validation、主键和事务。
8. `BetaAccessService`：行锁和回滚规则。
9. V1 到 V15 migration：数据模型演进。
10. 回到 `src/api.js` 和 Vue View，对照一次完整请求。

一句话总结：**Spring Boot 在这里是一个长期运行的 Java 进程；Tomcat 把 HTTP 请求变成方法调用，Controller/Service 用 JdbcClient 和文件系统完成业务，再把 Java 返回值变成 JSON。**
