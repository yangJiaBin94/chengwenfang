# 网页 / PDF 截图 CLI

两处共用：

1. **资料补充 Vision 兜底**：正文抽取失败、摘要像导航、或模型返回「信息不足」时，打开原链截最多 3 屏 PNG，再交给多模态 AI 写事实摘要。  
2. **成文佐证截屏**：热点节点 `hot_news_attach_evidence` 按 AI 计划截来源页一屏（可 `--find-text`）；PDF 用 PyMuPDF 渲染首页。

依赖：`requirements.txt` 中的 Playwright + **PyMuPDF**；Chromium 需 `playwright install chromium`。开发默认：`chengwenfang.page-screenshot.enabled=true`（见 `application.yml`）。

## 安装（推荐一键）

仓库根目录：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-hotnews-clis.ps1
# 只要截图：加 -SkipVideo
```

macOS / Linux：`./scripts/setup-hotnews-clis.sh`（并将 yml 中 `python-executable` 改为 `.venv/bin/python`）。

### 手动安装

```powershell
cd scripts/page-screenshot
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -U pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m playwright install chromium
```

## 试跑

```powershell
.\.venv\Scripts\python.exe -m page_screenshot --url "https://example.com" --out-dir .tmp-shots --max-shots 3
```

成功时 stdout 一行 JSON：`{"ok":true,"paths":["..."],"error":""}`。

成文佐证（定位文本后截一屏；**找不到文本则 `ok=false`，不截首屏冒充**）：

```powershell
.\.venv\Scripts\python.exe -m page_screenshot --url "https://example.com/article" --out-dir .tmp-shots --find-text "关键词" --max-shots 1
```

PDF 首页（URL 以 `.pdf` 结尾或 Content-Type 为 `application/pdf`）用 PyMuPDF 渲染，不走 Chromium；下载体积上限约 25MB。

## 说明

- 需本机 Playwright Chromium + PyMuPDF；与 Lightpanda（HTML/Markdown 抽取）互补，不互相替代。
- Java 调用：`PageScreenshotFacade` → CLI；失败时资料补充保留原 snippet。
- 不绕过登录墙 / 验证码。
- 关闭：`chengwenfang.page-screenshot.enabled=false`（或 `course-only` profile）。
