#!/usr/bin/env bash
# Import scripts/bootstrap/builtin-catalog.json (+ default-themes.json) into local MySQL.
# macOS / Linux 等价于 Initialize-ChengwenfangSeed.ps1。须先起好 MySQL。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

FORCE=false
if [[ "${1:-}" == "--force" || "${1:-}" == "-Force" ]]; then
  FORCE=true
fi

CATALOG="$ROOT/scripts/bootstrap/builtin-catalog.json"
DEFAULT_THEMES="$ROOT/scripts/bootstrap/default-themes.json"
APP_ARGS="--chengwenfang.bootstrap.catalog=${CATALOG} --chengwenfang.bootstrap.default-themes=${DEFAULT_THEMES} --chengwenfang.bootstrap.force=${FORCE}"

echo "Importing bootstrap catalog=${CATALOG} defaultThemes=${DEFAULT_THEMES} force=${FORCE}"
# Only run on chengwenfang-app; -am hits parent POM and fails without mainClass
mvn -pl chengwenfang-app -DskipTests spring-boot:run \
  -Dspring-boot.run.profiles=bootstrap-seed \
  "-Dspring-boot.run.arguments=${APP_ARGS}"

echo "Import done. Start the backend normally; log should show builtin config health OK."
echo "Note: 热点演示主题若报「请先由 AI 分析并确认主题主要意图」，属预期——先在设置填 API Key，再于主题页确认意图；课程主题与提示词包/工作流不受影响。"
