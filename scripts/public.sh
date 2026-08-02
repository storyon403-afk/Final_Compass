#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$ROOT_DIR/logs"
LOG_FILE="$LOG_DIR/public-$(date '+%Y%m%d-%H%M%S').log"

mkdir -p "$LOG_DIR"
exec > >(tee -a "$LOG_FILE") 2>&1
echo "运行日志：$LOG_FILE"

if [[ ! -f "$ROOT_DIR/.env.local" ]]; then
  echo "缺少 .env.local。请先按公网运行手册创建低权限数据库账号。"
  exit 1
fi
set -a
source "$ROOT_DIR/.env.local"
set +a
export SPRING_FLYWAY_ENABLED=false

if ! command -v cloudflared >/dev/null 2>&1; then
  echo "缺少 cloudflared。请先人工确认后执行：brew install cloudflared"
  exit 1
fi

echo "即将把本机期末指南通过随机公网 HTTPS 地址开放。"
echo "仅暴露 127.0.0.1:8080；数据库和开发端口不会暴露。"
echo "公开前请确认：已更换数据库密码、内测账号密码、检查待审核内容，且 Mac 不会休眠。"
read -r -p "输入 PUBLIC 才继续：" PUBLIC_CONFIRMATION
if [[ "$PUBLIC_CONFIRMATION" != "PUBLIC" ]]; then
  echo "已取消，没有启动公网入口。"
  exit 1
fi

cd "$ROOT_DIR"
npm run build

export STATIC_LOCATIONS="file:${ROOT_DIR}/dist/"
export SERVER_ADDRESS="127.0.0.1"
(cd "$ROOT_DIR/backend" && mvn spring-boot:run) &
BACKEND_PID=$!
caffeinate -i -w "$BACKEND_PID" &
CAFFEINATE_PID=$!
trap 'kill "$BACKEND_PID" "$CAFFEINATE_PID" 2>/dev/null || true' EXIT INT TERM

echo "等待主应用通过健康检查……"
for _ in {1..30}; do
  if curl --fail --silent http://127.0.0.1:8080/api/system/health >/dev/null; then break; fi
  sleep 1
done
curl --fail http://127.0.0.1:8080/api/system/health >/dev/null

echo "公网隧道启动后，终端会打印 https://*.trycloudflare.com 地址。关闭此终端即停止公开。"
cloudflared tunnel --protocol http2 --url http://127.0.0.1:8080
