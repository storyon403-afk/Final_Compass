#!/usr/bin/env bash
set -Eeuo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="${COMPOSE_FILE:-$project_root/deploy/docker-compose.yml}"
backup_dir="${BACKUP_DIR:-$project_root/backups/mysql}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="$backup_dir/finals-compass-$timestamp.sql.gz"

mkdir -p "$backup_dir"
umask 077
docker compose -f "$compose_file" exec -T mysql sh -eu -c \
  'exec mysqldump --single-transaction --quick --routines --triggers --events --hex-blob --set-gtid-purged=OFF -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip -9 > "$backup_file"
gzip -t "$backup_file"

if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$backup_file" > "$backup_file.sha256"
else
  sha256sum "$backup_file" > "$backup_file.sha256"
fi
printf 'Backup created: %s\nChecksum: %s.sha256\n' "$backup_file" "$backup_file"
