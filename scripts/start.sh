#!/usr/bin/env bash
# Cross-platform thin wrapper around the Windows PowerShell launcher.
# Requires: bash, Docker Compose, JDK 21, Maven, Node 22+.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ALL_DEPS=0
SKIP_COMPOSE=0
OPEN_BROWSER=0

usage() {
  cat <<'EOF'
Usage: scripts/start.sh [options]

  --all-deps       docker compose --profile all (SearXNG + Lightpanda)
  --skip-compose   do not start Compose services
  --open-browser   open http://127.0.0.1:5173 after start
  -h, --help       show this help

Course-only (MySQL + Redis) is the default. Hot-news Python CLIs are optional;
see README「仅课程主流程」.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all-deps) ALL_DEPS=1 ;;
    --skip-compose) SKIP_COMPOSE=1 ;;
    --open-browser) OPEN_BROWSER=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
  shift
done

if [[ "$SKIP_COMPOSE" -eq 0 ]]; then
  if [[ "$ALL_DEPS" -eq 1 ]]; then
    echo "Starting MySQL + Redis + SearXNG + Lightpanda..."
    docker compose --profile all up -d --wait
  else
    echo "Starting MySQL + Redis..."
    docker compose up -d --wait
  fi
fi

echo "Building chengwenfang-app..."
mvn -pl chengwenfang-app -am -DskipTests package

JAR="chengwenfang-app/target/chengwenfang-app.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Jar not found: $JAR" >&2
  exit 1
fi

echo "Starting backend on :8001..."
nohup java \
  -Dfile.encoding=UTF-8 \
  -Dserver.port=8001 \
  -Dspring.data.redis.database=0 \
  -jar "$JAR" \
  >"chengwenfang-app/boot-start.sh.out.log" 2>"chengwenfang-app/boot-start.sh.err.log" &
echo $! >"$ROOT/.chengwenfang-backend.pid"

if [[ ! -d node_modules ]]; then
  echo "Running npm install..."
  npm install
fi

echo "Starting Vite on :5173..."
export VITE_DEV_PORT=5173
export VITE_API_PROXY=http://127.0.0.1:8001
nohup npx --yes vite --host 127.0.0.1 --port 5173 \
  >"vite-start.sh.out.log" 2>"vite-start.sh.err.log" &
echo $! >"$ROOT/.chengwenfang-frontend.pid"

if [[ "$OPEN_BROWSER" -eq 1 ]]; then
  sleep 5
  if command -v xdg-open >/dev/null 2>&1; then xdg-open "http://127.0.0.1:5173" || true
  elif command -v open >/dev/null 2>&1; then open "http://127.0.0.1:5173" || true
  fi
fi

echo "Done. Health: http://127.0.0.1:8001/api/health  UI: http://127.0.0.1:5173"
echo "Stop: kill \$(cat .chengwenfang-backend.pid) \$(cat .chengwenfang-frontend.pid)"
