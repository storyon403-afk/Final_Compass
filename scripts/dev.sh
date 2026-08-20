#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

if [[ -z "${DB_URL:-}" ]]; then
  echo "缺少 DB_URL。请在根目录 .env 中配置 jdbc:mysql://...，或在启动前导出该变量。" >&2
  exit 1
fi
if [[ "$DB_URL" != jdbc:* ]]; then
  echo "DB_URL 必须是 JDBC 地址，当前值必须以 jdbc: 开头。" >&2
  exit 1
fi
if [[ -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" ]]; then
  echo "缺少 DB_USER 或 DB_PASSWORD。请在根目录 .env 中配置数据库账号。" >&2
  exit 1
fi

echo "启动 Java API：http://localhost:8080"
# clean 会清除已经改名的迁移在 target/classes 中留下的旧副本，避免 Flyway 误报版本重复。
(cd "$ROOT_DIR/backend" && mvn clean spring-boot:run) &
BACKEND_PID=$!

trap 'kill "$BACKEND_PID" 2>/dev/null || true' EXIT

echo "启动 Vue 前端：http://localhost:5173"
cd "$ROOT_DIR"
npm run dev
