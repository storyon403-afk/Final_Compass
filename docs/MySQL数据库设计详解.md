# MySQL 数据库设计详解

本文以 `services/api/src/main/resources/db/migration/V1__init.sql` 到 `V15__shared_courses_across_programs.sql` 为依据，解释“期末指南”数据库为什么这样拆表、表之间怎样联系、约束如何保证数据正确，以及当前设计距离更严格的数据库规范还有哪些差距。

阅读目标不是背 SQL，而是能够回答：

- 一条课程、老师、资料或讨论最终保存在哪些表？
- 为什么不能把所有数据放在一张大表里？
- 主键、外键、唯一约束、索引分别解决什么问题？
- Java 后端怎样把业务操作转换成 SQL？
- 新增功能时应该新建表、加字段，还是建立关系表？

---

## 1. 总体设计思路

数据库使用 MySQL、InnoDB、`utf8mb4`，结构由 Flyway 管理。设计上按业务领域分成六组：

```text
账号与认证
├── app_user
├── login_session
├── anonymous_user
└── beta_access_request

教务目录
├── college
├── course
├── course_program
├── teacher
└── teacher_course

老师圈内容
├── resource
├── resource_thank
├── discussion
├── study_guide
└── guide_submission

治理与系统
├── moderation_audit
└── system_announcement

反馈问卷
├── survey_question
├── survey_submission
└── survey_answer

CET 题库
├── cet_paper
├── cet_item
└── cet_paper_asset
```

这种拆分体现了两个原则：

1. **一张表描述一种实体或一种关系。** 用户、课程、老师和资料不是同一种东西，不混在一张表。
2. **事实只保存一次，再通过外键关联。** 资料保存 `course_id`，而不是重复保存完整课程名称。

---

## 2. 先理解关系模型中的几个概念

### 2.1 行、列和表

表可以类比为 Java 中某类对象的集合，一行类似一个对象，一列类似属性。但数据库表比普通 Java List 多了持久化、并发、约束和索引能力。

```text
course
┌────┬──────────────┬────────────┬───────────┐
│ id │ slug         │ name       │ code      │
├────┼──────────────┼────────────┼───────────┤
│ 12 │ math-analysis│ 数学分析   │ A500...   │
└────┴──────────────┴────────────┴───────────┘
```

### 2.2 主键

多数表以 `BIGINT AUTO_INCREMENT` 的 `id` 为主键。主键唯一且非空，用于稳定地引用一行。

业务名称会变化，课程代码也可能因历史数据发生冲突，因此内部关系通常引用数字 id，而不是名称。

关系表常使用联合主键：

```sql
PRIMARY KEY (resource_id, anonymous_user_id)
```

它同时表示“这两个字段共同唯一”和“按这两个字段建立索引”。同一匿名用户不能对同一资料感谢两次。

### 2.3 外键

外键确保被引用的数据真实存在：

```sql
FOREIGN KEY (course_id) REFERENCES course(id)
```

如果 `course_id=999` 不存在，数据库会拒绝插入。Java 后端即使写错 SQL，也不能轻易制造悬空关系。

### 2.4 唯一约束

唯一约束表达业务中不能重复的事实，例如：

- `app_user.username`：用户名唯一。
- `course.slug`：路由标识唯一。
- `login_session.token`：登录令牌唯一。
- `(level, exam_year, exam_month, set_number)`：同一四六级套卷唯一。
- `(course_id, teacher_id)`：一个老师圈最多一份正式复习指南。

### 2.5 NULL

`NULL` 表示未知、不适用或尚未发生，而不是空字符串。例如 `verified_at` 在验证完成前为 NULL，`reviewed_at` 在审核前为 NULL。

必需业务字段用 `NOT NULL`，可选说明、时间或文件路径允许 NULL。

---

## 3. 全局数据关系图

```text
app_user 1 ─── N login_session
    │
    ├── 0..1 anonymous_user
    │          ├── N resource
    │          ├── N discussion
    │          ├── N guide_submission
    │          └── N resource_thank
    │
    ├── N survey_submission ─── N survey_answer ─── 1 survey_question
    └── N 次管理员编辑 / 审核行为

college（当前以名称关联导航）
    │
    └── course_program N ─── 1 course
                                  │
teacher N ─── teacher_course ─── N course
                                  │
                         course + teacher = 老师圈
                                  ├── resource
                                  ├── discussion（可自引用形成回复）
                                  ├── study_guide
                                  └── guide_submission

cet_paper 1 ─── N cet_item
    │
    └── 0..1 cet_paper_asset
```

关系中的 `1:N` 表示一对多，`N:M` 需要中间表转换成两个一对多。

---

## 4. 账号与认证域

### 4.1 `app_user`：真实登录账号

主要字段：

| 字段 | 含义 |
|---|---|
| `id` | 内部主键 |
| `username` | 唯一登录名 |
| `password_hash` | BCrypt 哈希，不是明文密码 |
| `display_name` | 管理界面显示名 |
| `role` | `ADMIN` 或 `USER` |
| `active` | 软禁用账号，不删除历史记录 |
| `password_changed_at` | 最近改密时间 |

`active` 是软删除/软禁用思想。账号停止使用时可将其设为 `FALSE`，而不是删除账号，使审计和历史关系仍然成立。开源版本不预置任何真实或内测账号。

### 4.2 `login_session`：服务端会话

一个账号可以在多台设备登录，所以 `app_user 1:N login_session`。

`token` 唯一，`expires_at` 决定是否过期。外键使用 `ON DELETE CASCADE`：如果账号确实被删除，它的登录会话自动删除。

后端收到 token 后执行类似：

```sql
SELECT ...
FROM login_session s
JOIN app_user u ON u.id = s.user_id
WHERE s.token = ? AND s.expires_at > NOW() AND u.active = TRUE
```

### 4.3 `anonymous_user`：公开身份与登录身份分离

匿名用户最初独立存在，V5 增加 `app_user_id`：

```sql
UNIQUE (app_user_id)
```

这使一个登录账号最多绑定一个匿名身份。账号私下可追溯，其他普通用户只看 `nickname`，实现“对普通用户匿名、对平台可治理”。

外键采用 `ON DELETE SET NULL`，意味着登录账号删除后，匿名作者和历史内容仍可保留，只解除账号绑定。

### 4.4 `beta_access_request`：验证码申请是一种过程记录

它不是用户表，而是内测资格验证过程：邮箱、手机号、验证码、状态、失败次数、过期时间和验证时间。

同一邮箱可以多次申请，因此没有给 email 加唯一约束，只建立 `(email, created_at)` 索引。后端读取最近一次申请，并将旧的 PENDING 请求标记为 EXPIRED。

`status`、`failed_attempts`、`expires_at` 共同组成一个小型状态机：

```text
PENDING ──验证码正确──> VERIFIED
   ├────时间到期──────> EXPIRED
   └────失败次数过多──> 拒绝继续尝试
```

---

## 5. 学院、专业、课程和老师

### 5.1 `college`

保存学院名称和启用状态。名称唯一，避免两个同名学院。

### 5.2 `course`

课程是独立实体，包含 `slug`、名称、代码、是否启用等。`slug` 用于 URL，例如 `/courses/math-analysis`，即使中文名称调整，已有路由仍可保持稳定。

V1 把 `category`、`college` 直接放在课程表中；V6 又增加 `program_name` 和 `course_type`。这在“一门课只属于一个专业”时可用，但不能表达公共课程同时属于数学类、数学与应用数学、统计学。

### 5.3 `course_program`：V15 的关键规范化

V15 把“课程本身”和“课程属于哪个专业”拆开：

```text
course：数理统计，代码 A500070211
course_program：
  ├── 数学类 / 专业课
  ├── 数学与应用数学 / 专业课
  └── 统计学 / 专业课
```

联合主键：

```sql
PRIMARY KEY (course_id, college, program_name)
```

保证同一课程不会在同一学院和专业重复归类。`course_type` 属于“课程与专业的关系”，因为同一课程在不同培养方案中可能承担不同类型。

这就是规范化的典型价值：不复制三门相同课程，也不会产生三个老师圈、三套资料和三个讨论区。

### 5.4 为什么课程代码现在只有普通索引

业务规则要求代码唯一，但历史生产数据曾存在同码异课。V15 没有猜测性删除或合并数据，只加：

```sql
INDEX idx_course_code (code)
```

新增接口在应用层阻止继续产生冲突。这是迁移安全上的合理折中，但不是最终最强约束。完成历史数据清理后，建议新增迁移改成唯一索引：

```sql
CREATE UNIQUE INDEX uk_course_code ON course(code);
```

MySQL 唯一索引允许多个 NULL，因此没有代码的历史课程仍可共存；若业务要求所有课程必填代码，还应先补全数据再改为 `NOT NULL`。

### 5.5 `teacher` 与 `teacher_course`

老师和课程是多对多：一位老师可教多门课，一门课可由多位老师讲授。

`teacher_course` 的联合主键是：

```sql
(teacher_id, course_id, term)
```

因此同一老师可以在不同学期重复教授同一课程，但同一学期不会重复关联。`review_note` 属于一次授课关系，而不属于老师或课程本身。

---

## 6. 老师圈内容域

项目把 `course_id + teacher_id` 视为老师圈的业务坐标。资料、讨论、指南都保存这两个外键。

### 6.1 `resource`

资料表同时关联课程、老师和匿名上传者。主要字段分为：

- 内容描述：`title`、`resource_type`、`description`。
- 文件元数据：原文件名、磁盘存储名、MIME、大小。
- 治理：`status`。
- 统计缓存：下载数、感谢数。
- 时间：`created_at`。

数据库不保存 PDF 或 ZIP 二进制，只保存文件元数据和相对路径。文件本体位于 `uploads/`。这样避免数据库被大文件迅速撑大，也便于 Web 服务流式传输。

索引 `(teacher_id, course_id, status, created_at)` 与典型查询完全对应：查询某老师圈内某状态的资料，再按时间排序。

### 6.2 `resource_thank`

感谢是用户与资料之间的多对多关系：

```text
anonymous_user N ─── resource_thank ─── N resource
```

联合主键防止重复感谢。资源或匿名身份删除时 `ON DELETE CASCADE` 清除失去意义的关系行。

`resource.thanks_count` 是缓存计数，`resource_thank` 才是每次感谢的明细事实。缓存能快速显示数字，但更新时必须在同一事务中同时写关系和计数，否则会不一致。可定期用 `COUNT(*)` 校正。

### 6.3 `discussion`

讨论通过 `parent_id` 外键引用自己的 `discussion.id`：

```text
主帖 parent_id = NULL
├── 回复 A parent_id = 主帖 id
└── 回复 B parent_id = 主帖 id
```

这叫自关联。当前适合主帖/回复结构；若未来支持任意深度楼中楼，需要递归查询或增加路径、层级字段。

`status` 控制待审核、可见和移除。内容通常不物理删除，以便审计。

### 6.4 `study_guide`

正式复习指南对每个老师圈最多一份：

```sql
UNIQUE (course_id, teacher_id)
```

更新者 `updated_by` 指向登录账号，因为这是管理员编辑行为，不是匿名投稿。

### 6.5 `guide_submission`

用户投稿与正式指南分表保存。投稿状态从 PENDING 到 APPROVED、REJECTED 或 INCORPORATED；审核通过不等于自动覆盖正式指南，避免用户投稿直接改变公开内容。

两个索引分别服务于全局审核队列和圈内投稿列表。

---

## 7. 审核和系统配置

### 7.1 `moderation_audit`

每次审核记录内容类型、内容 id、决定、审核者和时间。

`item_type + item_id` 是多态引用：item id 可能来自 resource、discussion 或 guide_submission。优点是审核日志统一，缺点是数据库不能为 `item_id` 建一个同时指向三张表的外键。

因此它依赖后端先更新目标内容，再插入审计记录。规模扩大时可以选择：

- 分成三张类型明确的审核表；或
- 建统一 `moderatable_content` 父表，让各种内容共享主键。

当前统一表对小规模系统更简单。

### 7.2 `system_announcement`

公告表固定使用 `id=1`，本质是单例配置表。`updated_by` 记录最后修改管理员，`enabled` 控制显示。

如果未来需要历史公告、定时发布和多条轮播，应改成普通多行公告表，并增加发布时间、结束时间和排序。

---

## 8. 问卷设计

### 8.1 为什么分三张表

```text
survey_question：题库
survey_submission：用户的一次完整提交
survey_answer：该次提交中的每题答案
```

一份提交包含多道答案，一道题又被许多提交回答，所以 question 与 submission 是多对多，由 answer 关系实体连接。

`survey_answer` 不只是两个外键，还包含 `rating`、`suggestion` 和快照，因此它是有自身属性的关系表。

### 8.2 快照字段

`question_snapshot` 看似重复了 `survey_question.prompt`，属于有意反规范化。管理员更换问题后，历史答案仍需要知道当时的原题；若只保留外键，展示历史时可能看到新题文案。

### 8.3 数据库检查约束

```sql
CHECK (rating BETWEEN 1 AND 5)
```

前端校验改善体验，Java validation 提前返回友好错误，数据库 CHECK 是最后防线。三层校验并不重复：任何脚本、管理工具或未来服务绕过前端时，数据库仍保证评分合法。

`survey_answer` 对 submission 使用 `ON DELETE CASCADE`，删除一份提交时答案自动删除；对 question 不级联，因为历史答案仍依赖问题记录。

---

## 9. CET 题库设计

### 9.1 `cet_paper`

套卷的业务唯一键为：

```sql
UNIQUE (level, exam_year, exam_month, set_number)
```

这比只依赖标题可靠，因为标题是显示文本，可能调整。

### 9.2 `cet_item`

一套试卷包含多条结构化练习。`mode` 区分真题练习和精听精读，`section` 区分写作、听力、阅读等，`item_order` 控制顺序。

`options_json` 是一个折中：选择题选项数量和内容结构比较灵活，项目目前无需单独查询某个选项，所以存 JSON 简单有效。若以后要分析“每个错误选项被选择次数”，应拆成 `cet_option` 和 `cet_attempt` 等关系表。

索引 `(paper_id, mode, section, item_order)` 与页面筛选和排序一致。

删除套卷时 `ON DELETE CASCADE` 删除结构化题目，防止无所属套卷的孤儿题。

### 9.3 `cet_paper_asset`

`paper_id` 同时是主键和外键，表达 `cet_paper 1:0..1 cet_paper_asset`：一份套卷最多一组完整附件。

来源页面、使用说明和原文件名被单独保存，用于版权来源和用户下载显示；磁盘存储名用于服务器实际读取。

---

## 10. 这个数据库如何做到“规范”

### 10.1 第一范式（1NF）

要求字段值基本保持原子性，不在一个字符串中混放多组重复数据。

项目中课程、老师、用户、资料均一行一个实体；多专业没有用 `"数学类,统计学"` 逗号字符串保存，而是拆成多行 `course_program`。

`options_json` 是明确的 JSON 类型，不是随意拼接字符串，但从严格关系模型看仍是一个半结构化例外。

### 10.2 第二范式（2NF）

对于联合主键，非键字段应依赖整个主键。

例如 `teacher_course.review_note` 描述“某老师在某学期教授某课程”这一完整关系，而不是只依赖 teacher 或 course。`course_program.course_type` 依赖课程与专业的完整组合。

### 10.3 第三范式（3NF）

非键字段不应通过另一个非键字段间接依赖主键。

例如 resource 只保存 `course_id`，不重复保存课程名称和学院；需要时 JOIN course 和 course_program。账号密码、匿名昵称和讨论内容也分别在各自表中。

当前 `course` 中仍保留 V1/V6 的 `college`、`program_name`、`course_type`，而 V15 已用 `course_program` 表达相同关系。这是历史兼容字段，严格来说形成冗余，未来应迁移完调用后删除。

### 10.4 参照完整性

外键保证关系端点存在；`CASCADE`、`SET NULL` 或默认 RESTRICT 根据业务语义选择：

| 策略 | 本项目含义 |
|---|---|
| `CASCADE` | 父对象消失，完全依附的会话/感谢/答案随之删除 |
| `SET NULL` | 账号消失但匿名历史内容保留 |
| 默认 RESTRICT | 有课程、老师、审核历史时阻止误删主体 |

### 10.5 域完整性

`NOT NULL`、长度、ENUM、CHECK、默认值共同限制字段范围。应用层 validation 提供用户友好提示，数据库约束负责最终一致性。

### 10.6 可追溯性

多数业务表有 `created_at`，可编辑表有 `updated_at`，审核与指南还保存操作者。软禁用和状态迁移保留历史，这比直接删除更适合内测平台治理。

---

## 11. 索引是按查询设计的

索引不是越多越好。它加速读取，但会占空间，并使 INSERT/UPDATE/DELETE 维护更多 B+Tree。

本项目常见复合索引遵循“等值筛选列在前，排序/范围列在后”：

```sql
(teacher_id, course_id, status, created_at)
(paper_id, mode, section, item_order)
(college, program_name, course_type)
```

复合索引通常支持最左前缀。例如 `(paper_id, mode, section, item_order)` 能很好支持按 paper 查询、按 paper+mode 查询；单独只按 section 查询则不一定有效。

主键和唯一约束本身也会创建索引。`login_session.token` 已经 UNIQUE，额外的 `(token, expires_at)` 索引可能存在前缀重复；是否保留应通过 `EXPLAIN` 和实际数据量判断，而不是凭感觉删除。

查询优化的标准步骤：

1. 找真实慢 SQL。
2. 用 `EXPLAIN` 看访问类型、候选索引和扫描行数。
3. 根据 WHERE、JOIN、ORDER BY 设计索引。
4. 比较修改前后耗时和写入成本。

---

## 12. Java 后端如何使用这些表

项目不用 JPA/Hibernate，而是 `JdbcClient` 直接执行 SQL：

```java
jdbc.sql("SELECT id,name FROM course WHERE slug=:slug")
    .param("slug", slug)
    .query(CourseRow.class)
    .single();
```

### 查询关系

需要组合信息时使用 JOIN：

```sql
SELECT c.id, c.name, cp.program_name
FROM course c
JOIN course_program cp ON cp.course_id = c.id
WHERE c.active = TRUE;
```

这会返回一门课程的多个专业归属。后端返回多行给前端，前端按学院和专业组织导航。

### 写入关系

新增公共课程时，后端先按代码查 `course`：

- 不存在：插入 course，再插入 course_program。
- 同码同名存在：只补 course_program 关系。
- 同码异名：拒绝，避免冲突。

该过程用 `@Transactional` 包裹，保证两张表要么一起成功，要么一起回滚。

### 并发正确性

数据库唯一约束比“先查再插”更可靠，因为两个请求可能同时查到不存在。当前用户名、token、感谢、匿名绑定、套卷等都有数据库唯一约束；课程代码暂时只有应用层保护，是已知待改进项。

验证码验证使用事务和 `SELECT ... FOR UPDATE` 锁定一行，避免两个并发请求同时修改失败次数或验证状态。

---

## 13. Flyway 如何保证结构一致

数据库设计不是只看“今天有哪些 CREATE TABLE”，还要看如何从旧版本安全演进。

Flyway 按 V1、V2……V15 执行迁移，并在 `flyway_schema_history` 记录版本、脚本校验值、执行时间和成功状态。

规范做法：

1. 已在任何共享环境执行的迁移不再修改。
2. 每次结构变化新增下一个版本。
3. DDL 前考虑旧数据能否满足新约束。
4. 先备份，再在生产数据副本验证。
5. 应用代码与迁移保持向前/向后兼容，避免发布窗口中一半新一半旧。

V15 是很好的例子：先创建关系表并回填旧数据；由于历史代码冲突，不直接增加唯一约束，也不猜测性删除课程。

---

## 14. 当前设计中的折中与改进顺序

这套设计整体适合当前规模，但“规范”不是一次完成的。建议按风险排序演进：

### 第一优先级：强化课程唯一性

清理全部同码异课后，将 `course.code` 改为业务要求的 `NOT NULL + UNIQUE`。这样即使绕过 Java API，也无法插入重复课程代码。

### 第二优先级：移除课程旧归属字段

确认所有查询都使用 `course_program` 后，删除 `course.college`、`program_name`、`course_type`，并评估 `category` 是否仍有独立业务含义。

### 第三优先级：规范学院和专业

当前 `course_program.college` 保存学院字符串，没有外键指向 `college`；专业也没有独立表。若扩展到多学院，建议：

```text
college(id, name)
program(id, college_id, name)
course_program(course_id, program_id, course_type)
```

这样学院改名只改一行，也不会出现错别字形成“新学院”。

### 第四优先级：保证老师圈组合真实存在

resource、discussion、guide 分别引用 course 和 teacher，但数据库没有保证这对 `(teacher_id, course_id)` 一定存在于 `teacher_course`。可增加稳定的 `teacher_course.id` 或单独 `circle` 表，然后让内容引用 `circle_id`。

由于 teacher_course 目前还含 term，同一老师和课程可有多学期记录；老师圈是跨学期还是按学期拆分，需要先由产品规则决定。

### 第五优先级：计数一致性

为下载数、感谢数制定原子更新和定期校正任务；若数据量不大，也可实时 COUNT，减少缓存字段。

### 第六优先级：安全与隐私

验证码不应长期明文保存，可改为哈希并设置定期清理；会话 token 也可只保存哈希。对邮箱、手机号制定保留期限和脱敏策略。

---

## 15. 新增数据库功能时如何设计

假设增加“课程收藏”：

### 第一步：识别实体和关系

收藏不是 course 的一个字段，因为每个用户能收藏多门课程，每门课程也能被多人收藏。它是用户与课程的多对多关系。

```sql
CREATE TABLE course_favorite (
  user_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, course_id),
  FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
  INDEX idx_favorite_course (course_id, created_at)
);
```

### 第二步：逐项检查

- 主键是否稳定？
- 哪些字段必须 NOT NULL？
- 什么业务事实必须唯一？
- 删除父记录时 CASCADE、SET NULL 还是 RESTRICT？
- 页面最常用查询需要什么索引？
- 是否需要 created/updated/operator 审计字段？
- 多步写入是否需要事务？
- 是否包含个人信息和清理期限？

### 第三步：通过新迁移上线

新建 V16，不修改 V15。先在本地和生产副本执行，验证迁移、回滚方案、Java 测试和真实查询。

---
需求：**这个数据库用独立实体表保存“是什么”，用关系表保存“彼此如何关联”，用约束保证“什么数据允许存在”，用索引服务“系统实际怎样查询”，再用 Flyway保证所有环境按同一历史演进。**
