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

  --all-deps       满血：docker compose --profile all，并安装三条 Python CLI
  --skip-compose   do not start Compose services
  --open-browser   open http://127.0.0.1:5173 after start
  -h, --help       show this help

默认档是「课程 + 知识库」：MySQL + Redis + Qdrant。
空库由应用启动时自动灌种子。Python 路径按操作系统选择 venv，不必改配置。
热点加装用 --all-deps（SearXNG + Lightpanda + Neo4j 容器；Graph 开关仍默认关闭）。
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
    echo "Starting MySQL + Redis + Qdrant + SearXNG + Lightpanda + Neo4j (Graph 应用开关仍关闭)..."
    docker compose --profile all up -d --wait
    echo "Installing article-extract, video-transcript, page-screenshot..."
    chmod +x scripts/setup-hotnews-clis.sh
    ./scripts/setup-hotnews-clis.sh
  else
    echo "Starting MySQL + Redis + Qdrant (课程 + 知识库)..."
    docker compose up -d --wait
  fi
fi

echo "Python CLI 路径按操作系统自动选择 venv，无需修改 application.yml。"
echo "空库会在后端启动时自动导入提示词包与三条主工作流。"

echo "Building chengwenfang-app..."
mvn -pl chengwenfang-app -am -DskipTests package

JAR="chengwenfang-app/target/chengwenfang-app.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Jar not found: $JAR" >&2
  exit 1
fi

# nohup 在部分 IDE/SSH 会话结束时会被带走；需要时改用两个前台终端（见 README）
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
