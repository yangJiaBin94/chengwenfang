# 文章正文抽取 CLI

供雷达解析节点调用：默认用 Trafilatura HTTP 拉取；可选经 **Lightpanda** 渲染 JS 页后再抽取。

## 安装

```powershell
cd scripts/article-extract
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -U pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

## 试跑（Trafilatura）

```powershell
.\.venv\Scripts\python.exe -m article_extract --url "https://example.com/article"
```

成功时 stdout 一行 JSON：`{"ok":true,"text":"...","date":"2026-10-14","dateInputText":"...","error":""}`。

## 可选：Lightpanda 渲染后再抽

1. 启动 sidecar（仓库根目录）：

```powershell
docker compose --profile lightpanda up -d
```

2. 试跑（`docker exec` 进 `chengwenfang-lightpanda` 容器执行 `fetch --dump html`）：

```powershell
.\.venv\Scripts\python.exe -m article_extract --url "https://quotes.toscrape.com/js/" --via lightpanda
```

Linux/macOS 若本机已装 `lightpanda` 二进制：

```bash
python -m article_extract --url "https://quotes.toscrape.com/js/" --via lightpanda --browser-mode binary
```

说明：

- Lightpanda 镜像为 **AGPL-3.0**；本仓库未修改其源码，仅作可选 sidecar。
- Compose 已设 `LIGHTPANDA_DISABLE_TELEMETRY=true`。
- **不会**用于绕过验证码/登录墙；业务侧 `skip-hosts` 仍直接走摘要。

## 来源对比抽样

```powershell
.\.venv\Scripts\python.exe compare_sources.py --csv .chengwenfang/reports/xxx.csv --limit 10
# 未起容器时：
.\.venv\Scripts\python.exe compare_sources.py --csv ... --skip-lightpanda
```
