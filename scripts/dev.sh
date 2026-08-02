#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
echo "启动 Java API：http://localhost:8080"
(cd "$ROOT_DIR/backend" && mvn spring-boot:run) &
BACKEND_PID=$!

trap 'kill "$BACKEND_PID" 2>/dev/null || true' EXIT

echo "启动 Vue 前端：http://localhost:5173"
cd "$ROOT_DIR"
npm run dev
