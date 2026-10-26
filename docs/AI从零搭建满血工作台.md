# AI 从零搭建满血工作台（执行手册）

> **给人类**：把本文件路径发给 AI（Cursor / Codex 等），并说明目标是「按文档从零搭满血环境直到前后端可访问」。  
> **给 AI**：严格按本文阶段执行；每阶段先探测再安装；每步用「验收命令」确认后再进入下一步。不要跳过验收，不要臆造未在仓库出现的端口/账号/路径。

| 项 | 值 |
|----|-----|
| 仓库根目录 | 含 `pom.xml`、`compose.yml`、`package.json` 的目录（下文称 `$ROOT`） |
| 目标形态 | **满血**：容器全开 + 本机 Python CLI + ffmpeg + 前后端进程起来 |
| 人类仍须完成 | 在网页「设置」里填写真实 AI API Key（及可选微信公众号）；密钥勿写入仓库 |
| 默认 OS | Windows + PowerShell（作者环境）；非 Windows 见文末附录 |

相关人工说明：[`README.md`](../README.md)。正文抽取 / 视频 CLI 细节：[`scripts/article-extract/README.md`](../scripts/article-extract/README.md)、[`scripts/video-transcript/README.md`](../scripts/video-transcript/README.md)。

---

## 0. AI 行为约束

1. **工作目录**：所有相对路径相对 `$ROOT`；改目录后记得 `cd` 回来。  
2. **先探测**：用 `java -version` / `docker compose version` 等判断是否已装，避免重复装坏环境。  
3. **Windows 终端**：跑 Maven / Java / Python 前执行一次：  
   `. .\scripts\use-utf8.ps1`  
4. **禁止**：改 git config；把 API Key 写进仓库；对生产库 `docker compose down -v`（会删数据）除非用户明确要求。  
5. **不要编造**：账号密码、端口以 `compose.yml` + `workbench-app/src/main/resources/application.yml` 为准。  
6. **满血 ≠ 把 Java/Whisper 打进 Docker**：当前架构是宿主机跑应用，Compose 只提供 mysql/redis/searxng/lightpanda。  
7. 某步失败：停在该阶段，报告命令、完整错误、已确认的环境信息，再问用户如何继续。

### 满血完成定义（全部满足才算成功）

- [ ] `GET http://127.0.0.1:8001/api/health` 返回成功（`code=ok` 或等价成功信封）  
- [ ] 浏览器可打开 `http://127.0.0.1:5173`  
- [ ] `docker compose --profile all ps` 中 mysql、redis、searxng、lightpanda 为 running（或 healthy）  
- [ ] 存在 `scripts/article-extract/.venv/Scripts/python.exe`（Windows）且能 `import trafilatura`  
- [ ] 存在 `scripts/video-transcript/.venv`，且本机 `ffmpeg -version` 可用；venv 内可 `import whisper`  
- [ ] 向用户说明：须在前端「设置」配置 AI 后，生成/解析类能力才真正可用  

---

## 阶段 A — 探测本机工具

在 `$ROOT` 执行（PowerShell）：

```powershell
. .\scripts\use-utf8.ps1
java -version
mvn -version
node -v
npm -v
docker version
docker compose version
python --version
ffmpeg -version
```

| 工具 | 最低期望 | 缺失时（Windows 示例） |
|------|----------|------------------------|
| Docker Desktop + Compose | `docker compose version` 成功，且 Docker 引擎已启动 | `winget install Docker.DockerDesktop`，**请用户启动 Docker Desktop** 后再继续 |
| JDK | 21 | `winget install Microsoft.OpenJDK.21`，新开终端 |
| Maven | 能跑 `mvn -version` | `winget search maven` 后安装，或用户已有安装并在 PATH |
| Node.js | 建议 20+（Vite 7） | `winget install OpenJS.NodeJS.LTS`，新开终端 |
| Python | 3.11+ | `winget install Python.Python.3.12`，新开终端 |
| ffmpeg | 在 PATH | `winget install Gyan.FFmpeg`，新开终端 |

**验收 A**：上表六类工具命令均可执行；`java -version` 显示 21。Docker 引擎未启动则不要进入阶段 B。

---

## 阶段 B — Docker 拉起全部容器依赖

```powershell
cd $ROOT
. .\scripts\use-utf8.ps1
docker compose --profile all up -d --wait
docker compose --profile all ps
```

期望服务：

| 服务 | 端口 | 用途 |
|------|------|------|
| mysql | `127.0.0.1:3306` | 库名 `workbench_v2`，用户 `workbench`，密码 `workbench_dev` |
| redis | `127.0.0.1:6379` | Job 租约等 |
| searxng | `127.0.0.1:8080` | 热点聚合搜索 |
| lightpanda | `127.0.0.1:9222` | JS 正文回退（容器名 `workbench-lightpanda`） |

可选探测：

```powershell
curl.exe -s "http://127.0.0.1:8080/search?q=test&format=json" | Select-Object -First 1
docker ps --filter "name=workbench-lightpanda"
```

**验收 B**：`--wait` 成功退出；`ps` 中四服务均在跑。若仅需课程、不要热点容器，可用 `docker compose up -d --wait`（非满血，须告知用户）。

改 `deploy/searxng/settings.yml` 后：

```powershell
docker compose --profile searxng up -d --force-recreate searxng
```

---

## 阶段 C — 本机 Python CLI（正文 + 视频）

开发配置默认路径（Windows，见 `application.yml`）：

- `workbench.article-extract.python-executable` → `scripts/article-extract/.venv/Scripts/python.exe`  
- `workbench.video-transcript.python-executable` → `scripts/video-transcript/.venv/Scripts/python.exe`  

### C1 正文抽取

```powershell
cd $ROOT\scripts\article-extract
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -U pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -c "import trafilatura,htmldate; print('article-extract ok')"
cd $ROOT
```

### C2 视频解析

```powershell
cd $ROOT\scripts\video-transcript
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -U pip
pip install -r requirements.txt
ffmpeg -version
yt-dlp --version
python -c "import whisper, PIL, numpy; print('video-transcript ok')"
cd $ROOT
```

说明：`openai-whisper` 体积大；首次真正跑 ASR 还会下载 `base` 模型。可设 `HTTP_PROXY` / `HTTPS_PROXY`（若用户网络需要）。

**验收 C**：C1/C2 的 `print(... ok)` 均成功；`ffmpeg -version` 成功。

---

## 阶段 D — 前端 npm 依赖

```powershell
cd $ROOT
npm install
```

**验收 D**：`npm install` 退出码 0；存在 `node_modules`。

---

## 阶段 E — 启动后端与前端

任选其一。启动前确认阶段 B 的 MySQL 已就绪。

### 方式 E1：分进程（便于看日志，推荐 AI 排障）

**终端 1 — 后端：**

```powershell
cd $ROOT
. .\scripts\use-utf8.ps1
mvn -pl workbench-app -am spring-boot:run
```

等到日志出现 Tomcat / Spring 启动完成（端口 `8001`）。

**终端 2 — 前端：**

```powershell
cd $ROOT
npm run dev:frontend
```

### 方式 E2：一键脚本（Windows；后台起 jar + Vite）

须已完成阶段 D。

```powershell
cd $ROOT
powershell -ExecutionPolicy Bypass -File .\scripts\start-java.ps1 -AllDeps
```

`-AllDeps` 会再执行一遍 `docker compose --profile all up -d --wait`。若容器已在跑可加 `-SkipCompose`。可选 `-OpenBrowser`。

停止后端 Java：`.\scripts\stop-java.ps1`（不停 Vite / Docker）。

**验收 E：**

```powershell
curl.exe -s http://127.0.0.1:8001/api/health
# 前端：浏览器打开 http://127.0.0.1:5173 或 curl 首页
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:5173
```

健康检查应为成功 JSON；前端 HTTP 状态 200。

| 地址 | 用途 |
|------|------|
| http://127.0.0.1:5173 | 前端（`/api` 代理到 8001） |
| http://127.0.0.1:8001/api/health | 健康检查 |
| http://127.0.0.1:8001/swagger-ui.html | 开发 API 文档 |

库表：后端首次启动时 Flyway 自动迁移，无需手工导 SQL。

内置 Skill 与三条工作流不在启动时从代码写入。阶段 E 验收通过后执行一次：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap\Initialize-WorkbenchSeed.ps1
```

未执行时，再次启动后端日志会出现「基础配置缺失」及上述命令；进程不因此退出。详见 [`scripts/bootstrap/README.md`](../scripts/bootstrap/README.md)。

---

## 阶段 F — 满血业务侧配置（需人类参与）

AI **不能**代替用户填写真实密钥。完成后请用户：

1. 打开 `http://127.0.0.1:5173` → **设置**  
   - 配置文本 AI、图像 AI  
   - 热点「画面解析」需要多模态（Vision）时一并配置  
   - 使用页面上的连接测试  
2. **热点资讯 → 来源管理**（若用本地 SearXNG）  
   - 类型：SearXNG 搜索  
   - 实例地址：`http://127.0.0.1:8080`  
3. 开发配置已含 `workbench.crawler-allow-loopback=true`（允许采本机 SearXNG）；勿把该开关当成生产默认。

可选：`POST /api/migrations/sync-learning-content` 同步学习内容（若用户需要导入 `content/` 资源）。

**验收 F**：用户确认 AI 连接测试通过；若启用 SearXNG，来源已保存实例 URL。至此「满血可运营」才成立；阶段 E 只保证进程与依赖就绪。

---

## 阶段 G — 向用户汇报

用简短列表汇报：

1. 已安装 / 已存在的本机工具版本号  
2. Compose 四服务状态  
3. 两个 Python venv 是否就绪、ffmpeg 是否在 PATH  
4. 后端 health、前端 URL  
5. 是否已执行 `scripts/bootstrap/Initialize-WorkbenchSeed.ps1`（未执行则生成用的 Skill/工作流不在库里）  
6. 仍待用户完成的设置项（API Key、SearXNG 来源）  
7. 常用停止命令：`.\scripts\stop-java.ps1`；`docker compose --profile all stop`  

---

## 故障速查

| 现象 | 排查 |
|------|------|
| Flyway / 连不上库 | `docker compose ps`；是否跳过了 `--wait`；端口 3306 是否被占用 |
| 日志「基础配置缺失」 | 执行 `.\scripts\bootstrap\Initialize-WorkbenchSeed.ps1`；不要指望启动自动灌 Skill |
| `npm run dev` 找不到 powershell | 用阶段 E1，或安装 PowerShell 7 |
| 正文抽取失败 | 查 `scripts/article-extract/.venv`；Lightpanda：`docker ps` 是否有 `workbench-lightpanda` |
| 视频解析失败 | `ffmpeg -version`；video-transcript venv；设置里是否配置 Vision |
| SearXNG 超时 / 空结果 | 见 `deploy/searxng/settings.yml`；勿只依赖 SearXNG，可加 RSS 等 |
| 中文日志乱码 | 重新 `. .\scripts\use-utf8.ps1` 后再起 Java |
| 8001 / 5173 端口占用 | 查占用进程；或先 `.\scripts\stop-java.ps1` |
| `start-java.ps1` 前端白屏 | 是否执行过 `npm install` |

---

## 附录 — macOS / Linux 差异

- 跳过 `use-utf8.ps1`。  
- 包管理：Homebrew 等安装 `openjdk@21`、`maven`、`node`、`python`、`ffmpeg`、Docker。  
- Compose 命令相同：`docker compose --profile all up -d --wait`。  
- Python venv 解释器为 `.venv/bin/python`，**必须**改 `application.yml`（或环境覆盖）中两处 `python-executable`，否则 Java 仍找 Windows 路径。  
- `start-java.ps1` 不可用时改用阶段 E1。  

---

## 一键命令清单（Windows 满血，供复制）

在已装好 Docker/JDK/Maven/Node/Python/ffmpeg 且 Docker 已启动的前提下：

```powershell
cd $ROOT
. .\scripts\use-utf8.ps1

docker compose --profile all up -d --wait

cd scripts\article-extract
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -U pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
cd ..\video-transcript
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -U pip
pip install -r requirements.txt
cd ..\..

npm install

# 然后 E1 双终端 spring-boot:run + npm run dev:frontend
# 或：powershell -ExecutionPolicy Bypass -File .\scripts\start-java.ps1 -AllDeps -SkipCompose
```

最后提醒用户完成阶段 F（AI Key + 可选 SearXNG 来源）。
