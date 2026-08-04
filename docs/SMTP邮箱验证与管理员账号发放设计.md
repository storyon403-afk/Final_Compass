# SMTP 邮箱验证与管理员账号发放设计

> 设计分支：`feature/backend`
>
> 当前状态：仅设计，不创建Flyway、不修改登录代码
>
> 前置依赖：`feature/redis-cache` 的安全状态模块

## 1. 目标与边界

邮箱流程拆成两条权限完全不同的链路：

```text
邮箱所有权验证
  用户申请 -> 系统自动发送验证码 -> 用户验证

账号发放
  邮箱验证成功 -> 管理员人工审核 -> 管理员重新验证密码
  -> 预览最终邮件 -> 管理员明确确认 -> 创建账号并发送临时密码
```

自动验证码只证明申请人能够接收该邮箱的邮件，不代表平台批准账号。账号和临时密码绝不能在邮箱验证成功后自动发送。

## 2. 当前代码存在的问题

当前 `BetaAccessService`：

- 在MySQL中明文保存6位验证码。
- 申请接口只生成验证码，没有SMTP发送。
- 管理员列表可以直接读取验证码。
- 限制只有数据库失败次数，没有邮箱/IP发送限流。
- 验证成功后没有正式的管理员账号发放状态机。

现有V8迁移已经进入项目历史，不能直接修改。后续通过新迁移调整字段和增加表。

## 3. 完整状态机

### 3.1 邮箱申请状态

```text
CREATED
  -> CODE_SENT
  -> EMAIL_VERIFIED
  -> WAITING_ADMIN
  -> APPROVED
  -> ACCOUNT_CREATED
  -> CREDENTIAL_SENT

任意审核阶段 -> REJECTED
验证码过期   -> EXPIRED
```

`EMAIL_VERIFIED` 与 `APPROVED` 必须是不同状态。

### 3.2 账号邮件发送状态

```text
NOT_CREATED
  -> READY_FOR_ADMIN_CONFIRMATION
  -> SENDING
  -> SENT

SENDING -> FAILED
FAILED  -> 管理员再次验证 -> 生成新临时密码 -> SENDING
```

失败重试不能复用旧临时密码，因为系统不持久化明文密码。

## 4. 自动验证码流程

```text
POST /api/auth/beta-access/request
  1. 规范化邮箱、手机号和来源IP
  2. Redis原子检查邮箱/IP限流
  3. MySQL创建或更新申请记录
  4. SecureRandom生成6位验证码
  5. Redis保存验证码HMAC摘要，TTL 10分钟
  6. 读取当前启用的验证邮件模板
  7. SMTP同步发送，设置连接和读取超时
  8. 成功：MySQL记录CODE_SENT和投递日志
  9. 失败：删除Redis验证码，记录FAILED，返回503
```

验证码不写入MySQL，不出现在管理员接口、普通日志、异常信息或trace中。

建议限制：

| 限制 | 默认值 |
|---|---:|
| 验证码有效期 | 10分钟 |
| 重发冷却 | 60秒 |
| 单邮箱每天 | 8次 |
| 单IP每小时 | 15次 |
| 单验证码失败 | 5次 |

用户输错验证码时，只增加Redis失败计数并保留当前挑战；在有效期和失败次数内可以继续输入，不返回申请页。

## 5. SMTP配置

管理员可编辑：

- SMTP主机与端口。
- STARTTLS或SSL模式。
- SMTP用户名。
- SMTP授权码。
- 发件邮箱。
- 发件人显示名称。
- Reply-To。
- 连接超时和读取超时。
- 是否启用。

### 5.1 敏感信息

SMTP授权码使用独立主密钥AES-GCM加密：

```text
MAIL_SECRET_ENCRYPTION_KEY
```

不能复用数据库密码，也不建议与AI Key主密钥共用。接口只返回授权码指纹，如 `a13f92c1e840`，绝不返回密文、IV或解密值。

管理员更新SMTP配置必须：

1. 已登录且角色为ADMIN。
2. 重新输入当前管理员密码。
3. 发送测试邮件成功。
4. 测试成功后才能启用配置。

修改配置、测试和启用分别写管理员审计。

## 6. 邮件模板

至少提供：

```text
EMAIL_VERIFICATION
ACCOUNT_CREDENTIAL
```

管理员可编辑：

- 主题。
- 纯文本正文。
- HTML正文。
- 是否启用。
- 模板备注。

允许变量：

```text
{{displayName}}
{{verificationCode}}
{{expiresMinutes}}
{{username}}
{{temporaryPassword}}
{{loginUrl}}
{{supportEmail}}
```

模板引擎只做受控变量替换，禁止SpEL、脚本、任意Java方法和数据库表达式。标题、发件人和Reply-To拒绝CR/LF，防止邮件头注入。HTML使用允许标签白名单清洗。

每次发送记录 `template_id + template_version`，以后模板修改不会改变历史审计含义。

## 7. 管理员人工发放账号

### 7.1 管理员界面

管理员打开已验证申请后看到：

- 邮箱和手机号。
- 申请与验证时间。
- 发送失败历史。
- 拟创建用户名、显示名称。
- 账号邮件最终预览。

最终按钮不叫普通“保存”，而是：

```text
核实申请并创建账号
```

点击后弹出确认层，要求：

- 重新输入当前管理员密码。
- 勾选“我已人工核实该申请人和收件邮箱”。
- 再次确认收件邮箱。
- 点击“创建账号并发送一次性临时密码”。

### 7.2 后端执行顺序

```text
1. requireAdmin
2. BCrypt重新验证管理员密码
3. SELECT申请 FOR UPDATE
4. 确认状态为EMAIL_VERIFIED/WAITING_ADMIN
5. 检查邮箱没有绑定其他申请或账号
6. 生成用户名和高强度临时密码
7. BCrypt临时密码并创建app_user
8. 设置must_change_password=true
9. 创建account_provisioning和审计记录
10. 提交数据库事务
11. 使用内存中的char[]临时密码渲染邮件
12. SMTP同步发送
13. 更新投递结果
14. finally清零char[]
```

SMTP调用不能放在数据库事务内，防止网络延迟长时间占用数据库锁。

如果第12步失败：

- 账号保留但标记凭据未送达。
- 临时密码明文立即清零。
- 不自动后台重试账号密码邮件。
- 管理员必须再次登录确认。
- 系统生成新的临时密码、更新密码哈希后重新发送。

这样无需在数据库或Outbox保存可解密的明文密码。

## 8. 为什么账号密码邮件不走普通Outbox重试

验证码、通知类邮件可以通过MySQL Outbox异步重试；账号临时密码不能直接放入普通 `payload_json`，否则数据库中会长期存在密码。

账号凭据采用“管理员确认后的同步发送 + 失败时人工重新生成”方案：

- 满足管理员亲自验证发送。
- 不持久化明文临时密码。
- 不会被后台任务在管理员不知情时重复发送。
- 每次失败重试都会使旧密码失效。

长期更推荐改成一次性激活链接；如果产品以后接受该方式，可以取消邮件中的临时密码。

## 9. 首次登录强制改密

`app_user` 增加：

```text
must_change_password BOOLEAN NOT NULL DEFAULT FALSE
```

临时账号创建时设为TRUE。登录成功响应增加：

```json
{
  "mustChangePassword": true
}
```

处于该状态的会话只允许访问：

- 当前用户信息。
- 修改密码。
- 退出登录。

其他业务接口返回403并提示先修改密码。成功修改后在同一事务中设置FALSE，并可撤销其他旧Session。

## 10. 持久化表设计

最终迁移建议包含：

### smtp_configuration

```text
id
host
port
security_mode
username
encrypted_credential
credential_iv
credential_fingerprint
from_address
from_name
reply_to
enabled
last_tested_at
last_test_status
updated_by
updated_at
```

### email_template

```text
id
template_type
version
subject_template
text_template
html_template
enabled
updated_by
updated_at
UNIQUE(template_type, version)
```

### email_delivery_log

```text
id
request_id
delivery_type
recipient_hash
template_id
template_version
status
provider_message_id
attempt_number
error_code
requested_by
sent_at
created_at
```

日志默认只保存收件邮箱摘要；管理员业务页面需要展示邮箱时从申请表按权限读取。

### account_provisioning

```text
id
request_id UNIQUE
user_id UNIQUE
status
reviewed_by
reviewed_at
credential_sent_at
last_delivery_status
created_at
updated_at
```

### beta_access_request调整

- `verification_code` 改为可空并清除历史明文。
- 扩展状态枚举或改成VARCHAR状态。
- 增加 `last_code_sent_at`、`reviewed_by`、`reviewed_at`、`rejection_reason`。

### app_user调整

- 增加 `email` 唯一字段或独立账号邮箱绑定表。
- 增加 `must_change_password`。

## 11. Flyway编号

当前 `feature/backend` 最高是V16，但集成分支已经存在AI的V17。因此不能在backend分支直接创建另一份V17。

正确流程：

```text
先完成并合并feature/redis-cache
  -> backend获得V17 AI迁移
  -> 再从最新集成基线开发邮箱
  -> 邮箱持久化使用V18
```

计划文件名：

```text
V18__mail_delivery_and_account_provisioning.sql
```

在真正创建文件前再次检查所有远程分支；如果V18已被占用，则顺延。已进入共享数据库的迁移禁止修改或重命名。

## 12. API设计

### 用户接口

```text
POST /api/auth/beta-access/request
POST /api/auth/beta-access/resend
POST /api/auth/beta-access/verify
POST /api/auth/login
POST /api/auth/change-password
```

申请接口不返回验证码、SMTP状态或内部限流Key。

### 管理员SMTP接口

```text
GET  /api/system/mail/smtp
PUT  /api/system/mail/smtp
POST /api/system/mail/smtp/test
POST /api/system/mail/smtp/enable
```

### 管理员模板接口

```text
GET  /api/system/mail/templates
PUT  /api/system/mail/templates/{type}
POST /api/system/mail/templates/{type}/preview
```

### 管理员账号发放接口

```text
GET  /api/system/beta-access?status=WAITING_ADMIN
POST /api/system/beta-access/{id}/reject
POST /api/system/beta-access/{id}/credential-preview
POST /api/system/beta-access/{id}/approve-and-send
POST /api/system/beta-access/{id}/regenerate-and-resend
```

`approve-and-send` 与 `regenerate-and-resend` 都要求管理员当前密码和明确确认字段。

## 13. 日志与隐私

禁止记录：

- 验证码。
- 临时密码。
- SMTP授权码。
- 完整邮件正文。
- Redis安全Key完整值。

允许记录：

- traceId。
- 请求编号。
- 模板类型和版本。
- 邮箱摘要。
- SMTP响应分类。
- 操作管理员ID。
- 发送耗时和结果。

错误信息不直接返回SMTP服务器原始响应，避免泄露账号、主机或认证细节。

## 14. 测试清单

- 同邮箱60秒内不能重复发送。
- 同IP和邮箱达到限额后返回429。
- Redis不可用时验证码发送返回503。
- SMTP失败后Redis验证码被删除。
- 验证码输错不会跳回账号申请页。
- 第5次失败后挑战锁定。
- 验证成功不会自动创建账号。
- 非管理员无法查看待发放申请。
- 管理员密码错误不能发送账号。
- 未勾选人工核实不能发送。
- 重复点击只创建一个账号。
- SMTP失败后临时密码不进入数据库或日志。
- 重发会使上一次临时密码失效。
- 首次登录只能修改密码或退出。
- 模板变量白名单和邮件头注入测试。
- SMTP密钥加密、指纹与内存清零测试。

## 15. 推荐开发顺序

```text
1. 合并Redis安全状态基础
2. 创建V18持久表和状态机
3. SMTP加密配置与测试邮件
4. 邮件模板、预览和安全渲染
5. 自动验证码发送与Redis校验
6. 管理员审核与重新认证
7. 创建账号和同步凭据邮件
8. 首次登录强制改密
9. 限流、并发、失败和泄密测试
```
