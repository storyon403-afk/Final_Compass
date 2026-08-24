# Redis 缓存与安全状态模块设计

> 设计分支：`feature/redis-cache`
>
> 基线：已合并 `feature/backend` 与 `feature/ai-analysis`
>
> 当前 Flyway：V1～V17，V16 为暂挂体验，V17 为 AI 活跃度与凭据

## 1. 模块目标

Final Compass 引入 Redis 不是为了替代 MySQL，也不是给所有查询机械增加 `@Cacheable`。Redis只承担两类职责：

```text
Redis
├── Security State
│   ├── 邮箱验证码摘要
│   ├── 邮箱/IP限流
│   ├── 重发冷却
│   ├── 幂等键
│   └── 短期分布式锁
└── Read Cache
    ├── 课程和学院目录
    ├── 教师列表
    ├── 系统公告
    ├── CET目录
    └── 低频变化的学习指南
```

MySQL继续作为账号、申请、邮件、课程、审核、AI资格和审计的唯一事实来源。

## 2. 两类Redis数据不能混成一个接口

| 类型 | Redis故障时 | 数据丢失后果 | 策略 |
|---|---|---|---|
| 普通读缓存 | 回源MySQL | 性能下降 | fail-open |
| 验证码、限流、幂等 | 停止对应安全操作 | 可能绕过限制或重复发送 | fail-closed |

禁止建立全局万能的 `RedisService.get/set` 供所有业务拼接Key。建议接口：

```text
infrastructure/redis/
├── RedisConfiguration
├── RedisKeyFactory
└── RedisHealthService

security/redis/
├── VerificationCodeStore
├── RequestRateLimiter
├── IdempotencyGuard
└── DistributedLockService

cache/
├── CatalogCacheService
├── AnnouncementCacheService
└── CetCatalogCacheService
```

安全接口与缓存接口分别定义异常和降级行为。

## 3. Key命名规范

```text
fc:{environment}:{domain}:{purpose}:{identity}:v{schemaVersion}
```

示例：

```text
fc:prod:auth:email-code:128:v1
fc:prod:auth:email-cooldown:sha256-email:v1
fc:prod:auth:email-daily:sha256-email:2026-08-04:v1
fc:prod:auth:ip-hourly:sha256-ip:2026080411:v1
fc:prod:mail:send-lock:128:v1

fc:prod:cache:catalog:courses:v1
fc:prod:cache:catalog:colleges:v1
fc:prod:cache:catalog:teachers:probability-theory:v1
fc:prod:cache:system:announcement:v1
```

邮箱和IP不直接出现在Key中，使用HMAC或SHA-256后的稳定摘要。所有Key由 `RedisKeyFactory` 生成，业务类不能自行拼接。

## 4. 序列化

- 使用JSON和明确DTO。
- 禁止Java原生序列化。
- 缓存DTO不直接复用JPA/数据库实体。
- 每个Key带结构版本，如 `v1`；DTO不兼容时升级为 `v2`，旧Key等待TTL自然过期。
- API Key、SMTP授权码、密码哈希不进入普通缓存。

## 5. 第一阶段：Redis基础设施

Maven依赖：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

使用Spring Boot默认Lettuce连接池，不额外引入Jedis。

环境变量：

```text
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
REDIS_SSL
REDIS_CONNECT_TIMEOUT
REDIS_COMMAND_TIMEOUT
APP_ENV
EMAIL_CODE_PEPPER
```

应用启动时Redis可以不健康，但处理安全操作时必须返回503；普通页面缓存则直接回源MySQL。

## 6. 第二阶段：邮箱验证安全状态

### 6.1 验证码

Redis只保存HMAC摘要：

```text
HMAC-SHA256(requestId + ":" + normalizedEmail + ":" + code, EMAIL_CODE_PEPPER)
```

默认限制：

| 项目 | 默认值 |
|---|---:|
| 验证码有效期 | 10分钟 |
| 同邮箱重发冷却 | 60秒 |
| 同邮箱每日发送 | 8次 |
| 同IP每小时发送 | 15次 |
| 单验证码失败次数 | 5次 |

计数、首次设置TTL和判断阈值必须在一个Lua脚本中完成，避免 `INCR` 成功但 `EXPIRE` 失败留下永久Key。

### 6.2 状态归属

Redis保存短期秘密和计数；MySQL保存：

- 申请人邮箱、手机号。
- 申请状态。
- 创建、验证、过期时间。
- 管理员审核结果。
- 邮件发送审计。

管理员接口不再读取明文验证码。

## 7. 第三阶段：普通读缓存

采用Cache Aside：

```text
GET
 -> Redis命中：返回
 -> Redis未命中/不可用：查询MySQL
 -> Redis可用时写缓存
 -> 返回
```

推荐清单：

| 数据 | TTL | 失效触发 |
|---|---:|---|
| 学院列表 | 10分钟±10% | 新增/停用学院 |
| 课程目录 | 5分钟±10% | 新增或修改课程 |
| 课程教师列表 | 5分钟±10% | 教师关联变化 |
| 系统公告 | 1分钟±10% | 管理员更新 |
| CET试卷列表 | 5分钟±10% | 试卷增删改 |
| CET题目列表 | 3分钟±10% | 题目增删改 |
| 学习指南 | 1分钟±10% | 更新或采纳内容 |

TTL加入随机抖动，避免大量Key同时失效。写操作应在MySQL事务提交后删除缓存，不能先删缓存再提交数据库。

第一版不缓存：

- 登录权限和密码。
- 管理员审核列表。
- 论坛讨论与回复。
- 下载数、感谢数。
- AI使用日志、排行榜实时积分。
- 邮件Outbox。
- 文件二进制内容。

## 8. 登录Session策略

第一版保持 `login_session` 在MySQL，不与验证码改造同时迁移。原因：

- 现有认证链稳定。
- Redis故障不应立刻让全部用户掉线。
- Session迁移需要双写、撤销、TTL和多端登录策略单独评审。

以后迁移时按以下顺序：

1. MySQL+Redis双写。
2. Redis优先读取、MySQL回退。
3. 观察稳定后停止MySQL新写入。
4. 最后通过新Flyway迁移移除旧表，而不是直接删除。

## 9. 邮件必须使用MySQL Outbox

Redis不能作为邮件发送的唯一队列。建议：

```text
管理员确认/验证码申请
 -> 同一MySQL事务写业务状态和email_outbox
 -> 后台发送器读取PENDING
 -> Redis取得幂等发送锁
 -> SMTP发送
 -> MySQL记录SENT或FAILED
```

Redis锁丢失可以重建，MySQL Outbox保证任务和审计不会消失。

## 10. Redis部署

第一阶段单实例：

```yaml
redis:
  image: redis:7.4-alpine
  restart: unless-stopped
  command:
    - redis-server
    - --requirepass
    - ${REDIS_PASSWORD}
    - --maxmemory
    - 256mb
    - --maxmemory-policy
    - noeviction
  networks:
    - app-network
```

- 不向宿主机公开6379。
- `noeviction` 防止验证码和限流Key被静默淘汰。
- 所有业务Key必须有TTL。
- 内存满导致安全Key写入失败时，对外返回503。
- 普通缓存写入失败时记录指标并回源MySQL。

规模扩大后拆成：

```text
redis-security：noeviction，可选AOF
redis-cache：allkeys-lru，无需持久化
```

## 11. Flyway迁移治理

### 11.1 当前编号

```text
V16__suspend_experience.sql
V17__ai_activity_and_credentials.sql
```

两份迁移均已合并，内容和编号不能再修改。

### 11.2 Redis模块是否需要迁移

Redis基础、Key、TTL、限流脚本和普通缓存不需要Flyway，不占用V18。Redis是短期存储，结构版本在Key后缀中管理。

### 11.3 邮件模块的迁移

SMTP配置、邮件模板、Outbox、管理员发放记录属于持久业务数据，需要MySQL。若开始开发时集成分支最高仍是V17，则使用一份聚合迁移：

```text
V18__mail_delivery_and_account_provisioning.sql
```

该迁移可以创建：

- `smtp_configuration`
- `email_template`
- `email_outbox`
- `account_provisioning`
- 必要的 `beta_access_request` 状态字段

开始写V18前必须：

```bash
git fetch origin
git merge origin/main
find services/api/src/main/resources/db/migration -type f | sort -V
```

如果另一个分支已经占用V18，并且双方迁移都没有在共享数据库执行，则将后合并的迁移重命名为V19；如果已经在任何共享或生产数据库执行，禁止重命名或修改，改用新的补偿迁移。

### 11.4 分支规则

- 需要数据库迁移的业务分支必须从最新集成基线创建。
- PR说明中声明占用的Flyway编号。
- 合并前CI检查同版本是否出现多个文件。
- Redis Key版本与Flyway版本无关，不能把Redis Key变更伪装成数据库迁移。

## 12. 测试要求

使用Testcontainers Redis或嵌入式兼容方案覆盖：

- 首次计数会设置TTL。
- 并发请求不会突破限额。
- 验证成功后验证码立即删除。
- 失败5次后锁定。
- Redis不可用时安全流程返回503。
- Redis不可用时课程读取回源MySQL。
- 数据库写入回滚时不错误删除缓存。
- 管理员更新后缓存正确失效。
- Key和日志不暴露邮箱、验证码、密码或授权码。

## 13. 推荐提交顺序

```text
chore(redis): add redis infrastructure and deployment
feat(redis): add atomic rate limiter and idempotency guard
feat(auth): move email verification secrets to redis
feat(cache): cache catalog and announcement reads
feat(mail): add smtp settings templates and outbox
feat(auth): require admin confirmation for credential delivery
```

每个提交都应保持MySQL为事实来源，并可单独回滚。
