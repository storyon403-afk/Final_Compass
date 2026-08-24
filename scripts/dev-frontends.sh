#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "启动 LiveDoc：http://localhost:5174/livedoc/"
(cd "$ROOT_DIR" && npm run dev:livedoc) &
LIVEDOC_PID=$!

trap 'kill "$LIVEDOC_PID" 2>/dev/null || true' EXIT INT TERM

echo "启动 Vue 主站：http://localhost:5173"
cd "$ROOT_DIR"
npm run dev:web
