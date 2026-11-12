# 安装热点模式本机 Python CLI：article-extract / video-transcript / page-screenshot(+Chromium+PyMuPDF)。
# 用法（仓库根目录）：
#   powershell -ExecutionPolicy Bypass -File .\scripts\setup-hotnews-clis.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\setup-hotnews-clis.ps1 -SkipVideo
param(
  # 跳过体积较大的 video-transcript（Whisper）
  [switch]$SkipVideo,
  # 跳过资料补充截图 Vision 兜底
  [switch]$SkipPageScreenshot
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
. "$PSScriptRoot\use-utf8.ps1"

function Ensure-Python {
  $python = Get-Command python -ErrorAction SilentlyContinue
  if (-not $python) {
    throw "未找到 python，请先安装 Python 3.11+"
  }
  Write-Host "Python: $($python.Source)"
}

function Setup-Venv([string]$RelDir, [string]$ImportCheck, [scriptblock]$AfterInstall) {
  $dir = Join-Path $Root $RelDir
  if (-not (Test-Path $dir)) {
    throw "目录不存在: $RelDir"
  }
  $venvPython = Join-Path $dir ".venv\Scripts\python.exe"
  Write-Host "==> $RelDir"
  if (-not (Test-Path $venvPython)) {
    Push-Location $dir
    try {
      python -m venv .venv
    } finally {
      Pop-Location
    }
  }
  if (-not (Test-Path $venvPython)) {
    throw "创建 venv 失败: $venvPython"
  }
  & $venvPython -m pip install -U pip
  if ($LASTEXITCODE -ne 0) { throw "pip upgrade failed: $RelDir" }
  & $venvPython -m pip install -r (Join-Path $dir "requirements.txt")
  if ($LASTEXITCODE -ne 0) { throw "pip install failed: $RelDir" }
  if ($AfterInstall) {
    & $AfterInstall $venvPython $dir
  }
  if ($ImportCheck) {
    & $venvPython -c $ImportCheck
    if ($LASTEXITCODE -ne 0) { throw "验收失败: $RelDir" }
  }
  Write-Host "ok: $RelDir"
}

Ensure-Python

Setup-Venv "scripts\article-extract" "import trafilatura,htmldate; print('article-extract ok')" $null

if (-not $SkipVideo) {
  Setup-Venv "scripts\video-transcript" "import whisper, PIL, numpy; print('video-transcript ok')" $null
} else {
  Write-Host "跳过 video-transcript（-SkipVideo）"
}

if (-not $SkipPageScreenshot) {
  Setup-Venv "scripts\page-screenshot" "from playwright.sync_api import sync_playwright; import fitz; print('page-screenshot ok')" {
    param($venvPython, $dir)
    Write-Host "安装 Playwright Chromium..."
    & $venvPython -m playwright install chromium
    if ($LASTEXITCODE -ne 0) { throw "playwright install chromium failed" }
  }
} else {
  Write-Host "跳过 page-screenshot（-SkipPageScreenshot）"
}

Write-Host ""
Write-Host "完成。开发默认已启用 chengwenfang.page-screenshot.enabled（见 application.yml）。"
Write-Host "资料补充 Vision 兜底还需在「设置」配置好多模态 Vision。"
Write-Host "验收截图 CLI："
Write-Host "  .\scripts\page-screenshot\.venv\Scripts\python.exe -m page_screenshot --url `"https://example.com`" --out-dir .tmp-shots --max-shots 1"
