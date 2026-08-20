#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

if [[ -z "${DB_URL:-}" || "$DB_URL" != jdbc:* ]]; then
  echo "DB_URL 必须配置为 jdbc:mysql://..." >&2
  exit 1
fi
if [[ -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" ]]; then
  echo "缺少 DB_USER 或 DB_PASSWORD。" >&2
  exit 1
fi

cat <<'EOF'
此操作会更新 flyway_schema_history 中的校验值，不会重新执行迁移 SQL。
它只适用于：V29、V31 已成功执行，且当前报错仅为这两个版本的 checksum mismatch。
请先备份数据库，并核对 V29/V31 创建和写入的数据完整。
EOF
read -r -p "输入 REPAIR-V29-V31 继续：" CONFIRMATION
if [[ "$CONFIRMATION" != "REPAIR-V29-V31" ]]; then
  echo "已取消。"
  exit 1
fi

cd "$ROOT_DIR/backend"
# Flyway Maven 插件原生读取这些环境变量；密码不会出现在进程参数中。
export FLYWAY_URL="$DB_URL"
export FLYWAY_USER="$DB_USER"
export FLYWAY_PASSWORD="$DB_PASSWORD"
mvn clean flyway:repair

echo "Repair 完成。请重新运行 ./scripts/dev.sh，让 Flyway validate 并继续迁移。"
