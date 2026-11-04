# 网页分段截图 CLI

供资料补充文章摘要 **Vision 兜底**：正文抽取失败、摘要像导航、或模型返回「信息不足」时，打开原链截最多 3 屏 PNG，再交给多模态 AI 写事实摘要。

开发默认：`chengwenfang.page-screenshot.enabled=true`（见 `application.yml`）。

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

## 说明

- 需本机 Playwright Chromium；与 Lightpanda（HTML/Markdown 抽取）互补，不互相替代。
- Java 调用：`PageScreenshotFacade` → CLI；失败时资料补充保留原 snippet。
- 不绕过登录墙 / 验证码。
- 关闭：`chengwenfang.page-screenshot.enabled=false`（或 `course-only` profile）。
