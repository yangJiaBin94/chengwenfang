#!/usr/bin/env bash
# 安装热点模式本机 Python CLI：article-extract / video-transcript / page-screenshot(+Chromium+PyMuPDF)。
# 用法（仓库根目录）：
#   chmod +x scripts/setup-hotnews-clis.sh && ./scripts/setup-hotnews-clis.sh
#   ./scripts/setup-hotnews-clis.sh --skip-video
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SKIP_VIDEO=0
SKIP_PAGE_SCREENSHOT=0

usage() {
  cat <<'EOF'
Usage: scripts/setup-hotnews-clis.sh [options]

  --skip-video              跳过 video-transcript（Whisper）
  --skip-page-screenshot    跳过 page-screenshot（Playwright Chromium）
  -h, --help                帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-video) SKIP_VIDEO=1 ;;
    --skip-page-screenshot) SKIP_PAGE_SCREENSHOT=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
  shift
done

if ! command -v python3 >/dev/null 2>&1 && ! command -v python >/dev/null 2>&1; then
  echo "未找到 python3/python，请先安装 Python 3.11+" >&2
  exit 1
fi
PYTHON_BIN="$(command -v python3 || command -v python)"
echo "Python: $PYTHON_BIN"

setup_venv() {
  local rel="$1"
  local import_check="$2"
  local after_install="${3:-}"
  local dir="$ROOT/$rel"
  local venv_python="$dir/.venv/bin/python"
  echo "==> $rel"
  if [[ ! -d "$dir" ]]; then
    echo "目录不存在: $rel" >&2
    exit 1
  fi
  if [[ ! -x "$venv_python" ]]; then
    (cd "$dir" && "$PYTHON_BIN" -m venv .venv)
  fi
  "$venv_python" -m pip install -U pip
  "$venv_python" -m pip install -r "$dir/requirements.txt"
  if [[ -n "$after_install" ]]; then
    eval "$after_install"
  fi
  if [[ -n "$import_check" ]]; then
    "$venv_python" -c "$import_check"
  fi
  echo "ok: $rel"
}

setup_venv "scripts/article-extract" "import trafilatura,htmldate; print('article-extract ok')"

if [[ "$SKIP_VIDEO" -eq 0 ]]; then
  setup_venv "scripts/video-transcript" "import whisper, PIL, numpy; print('video-transcript ok')"
else
  echo "跳过 video-transcript（--skip-video）"
fi

if [[ "$SKIP_PAGE_SCREENSHOT" -eq 0 ]]; then
  setup_venv "scripts/page-screenshot" \
    "from playwright.sync_api import sync_playwright; import fitz; print('page-screenshot ok')" \
    '"$venv_python" -m playwright install chromium'
else
  echo "跳过 page-screenshot（--skip-page-screenshot）"
fi

echo
echo "完成。请将 application.yml 中 python-executable 改为 .venv/bin/python（非 Windows）。"
echo "开发默认启用 page-screenshot；资料补充 Vision 还需在设置里配置多模态 Vision。"
echo "验收: scripts/page-screenshot/.venv/bin/python -m page_screenshot --url https://example.com --out-dir .tmp-shots --max-shots 1"
