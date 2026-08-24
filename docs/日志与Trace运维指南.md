# 日志与 Trace 运维指南

## Trace 对应关系

- HTTP 请求：响应头 `X-Trace-Id`，同时写入日志字段 `traceId`。
- AI Chat：`ai_runtime_execution.trace_id` 对应日志字段 `aiTraceId`；创建记录时，HTTP trace 保存在 `metadata.httpTraceId`。
- Agent / MultiWeb Agent：页面显示的 `run_key` 是任务标识，对应请求 trace 保存在 `ai_runtime_run.http_trace_id`。

后端日志默认同时输出到控制台和 `logs/finals-compass-api.log`。文件按日期和 50 MB 大小滚动，默认保留 30 天且总量不超过 2 GB。容器内目录 `/app/logs` 使用 Docker volume 持久化。

## 本地查看

```bash
tail -f services/api/logs/finals-compass-api.log
rg 'traceId=追踪号' services/api/logs/
rg 'aiTraceId=AI追踪号' services/api/logs/
```

可用 `LOG_DIR`、`LOG_MAX_FILE_SIZE`、`LOG_MAX_HISTORY` 和 `LOG_TOTAL_SIZE_CAP` 调整滚动策略。

## Docker、Loki 与 Grafana

先在 `deploy/.env` 配置 `GRAFANA_ADMIN_PASSWORD`，然后启动：

```bash
cd deploy
docker compose up -d --build
docker compose ps
```

Grafana 默认只监听宿主机 `127.0.0.1:3000`，Loki 只监听 `127.0.0.1:3100`。远程服务器建议通过 SSH 隧道访问 Grafana：

```bash
ssh -L 3000:127.0.0.1:3000 user@server
```

浏览器打开 `http://127.0.0.1:3000`，进入 Explore，数据源 `Loki` 已自动配置。

常用 LogQL：

```logql
{job="finals-compass", container="backend"}
{job="finals-compass", container="backend"} | logfmt | traceId="HTTP追踪号"
{job="finals-compass", container="backend"} | logfmt | aiTraceId="AI追踪号"
{job="finals-compass", container="backend"} |= "level=ERROR"
```

Trace ID 不作为 Loki 标签保存，避免高基数索引膨胀；查询时使用 `logfmt` 解析日志字段。

容器 stdout/stderr 使用 Docker `json-file` 驱动，每个文件最大 20 MB、最多 5 个。Loki 数据和 Grafana 配置分别持久化到 `loki_data`、`grafana_data` volume，Loki 默认保留 30 天。

## 数据库反查

```sql
SELECT id, execution_id, trace_id, status, metadata, created_at
FROM ai_runtime_execution
WHERE trace_id = 'AI追踪号'
   OR JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.httpTraceId')) = 'HTTP追踪号';

SELECT run_key, http_trace_id, runtime_type, status, created_at
FROM ai_runtime_run
WHERE run_key = '页面任务号' OR http_trace_id = 'HTTP追踪号';
```

## 故障检查

```bash
docker compose logs --tail=200 backend alloy loki grafana
curl -fsS http://127.0.0.1:3100/ready
```

若 Alloy 无法读取容器日志，检查 `/var/run/docker.sock` 是否存在，以及运行 Docker 的用户/平台是否允许只读挂载该 socket。
