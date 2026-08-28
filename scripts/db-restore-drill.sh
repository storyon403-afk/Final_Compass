#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 1 ]]; then printf 'Usage: %s BACKUP.sql.gz\n' "$0" >&2; exit 64; fi
backup_file="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
[[ -r "$backup_file" ]] || { printf 'Backup is not readable: %s\n' "$backup_file" >&2; exit 66; }
gzip -t "$backup_file"

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="${COMPOSE_FILE:-$project_root/deploy/docker-compose.yml}"
drill_db="finals_compass_restore_$(date -u +%Y%m%d%H%M%S)_$$"
mysql_root() {
  docker compose -f "$compose_file" exec -T mysql sh -eu -c \
    'exec mysql --protocol=TCP -h127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" "$@"' sh "$@"
}
cleanup() { mysql_root -e "DROP DATABASE IF EXISTS \`$drill_db\`;" >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

mysql_root -e "CREATE DATABASE \`$drill_db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
gzip -dc "$backup_file" | mysql_root "$drill_db"
table_count="$(mysql_root -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$drill_db';")"
flyway_rows="$(mysql_root -Nse "SELECT COUNT(*) FROM \`$drill_db\`.flyway_schema_history WHERE success=1;" 2>/dev/null || printf '0')"
user_rows="$(mysql_root -Nse "SELECT COUNT(*) FROM \`$drill_db\`.app_user;" 2>/dev/null || printf '0')"
[[ "$table_count" =~ ^[0-9]+$ && "$table_count" -gt 0 ]] || { printf 'Restore drill failed: no tables restored.\n' >&2; exit 1; }
[[ "$flyway_rows" =~ ^[0-9]+$ && "$flyway_rows" -gt 0 ]] || { printf 'Restore drill failed: Flyway history is missing.\n' >&2; exit 1; }
printf 'Restore drill passed. database=%s tables=%s flyway_migrations=%s app_users=%s\n' "$drill_db" "$table_count" "$flyway_rows" "$user_rows"
