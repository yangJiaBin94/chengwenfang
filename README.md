# Chengwenfang

本地个人内容生产成文坊：课程体系（学习素材 → 原稿 / 公众号文）与热点资讯（雷达 → 选题资料 → 成文），数据落本机。

**License:** [Apache-2.0](LICENSE) · **Conduct:** [Code of Conduct](CODE_OF_CONDUCT.md) · **Security:** 本机专用、[无鉴权](SECURITY.md)，请勿对公网暴露 · **Changelog:** [CHANGELOG.md](CHANGELOG.md) · **Content:** 本仓库不附带个人课程正文，仅含 bootstrap 演示主题（见 [`content/README.md`](content/README.md)）

技术形态：**Java 21 模块化单体**（Spring Boot 3.3）+ **React / Vite** 前端。无登录、无多人协作（个人本机使用）。

## 界面能力（与侧栏对应）

| 入口 | 说明 |
|------|------|
| 课程体系教学模式 | 主题 / 课程路线；原稿、公众号文、配图、版本、审核；生成任务轨迹 |
| 热点资讯模式 | 热点雷达、已选题、来源管理；选题资料池与核验、分步生成 |
| 工作流 | Skill 与工作流图编排 |
| 回收站 | 已删除主题等恢复 |
| 设置 | 文本 / 图像 / 多模态 AI、微信公众号等本机配置 |

课程侧常见链路：素材与路线 → 课程原稿 → 公众号文章 → 配图 → 排版（复制 Markdown / 打开 [mdnice](https://editor.mdnice.com/)）→ 审核 → AI 修复 → 人工确认。

## 技术栈

| 层 | 选型 |
|----|------|
| 后端 | Spring Boot `3.3.5`、Java 21、MyBatis-Plus、Flyway、Redisson；AI 侧 LangChain4j |
| 工具 | Lombok、Hutool、MapStruct；开发环境 springdoc-openapi（Swagger UI） |
| 前端 | React 19、Vite 7、TypeScript、Vitest |
| 基础设施 | MySQL 8.4、Redis 7（见 `compose.yml`） |
| 构建 | Maven 多模块（`pom.xml`）+ npm（`package.json`） |

分层约定（COLA 思路）：Adapter（HTTP / Job）→ Application 编排 → 领域 Facade → Infrastructure。详见 [`.cursor/skills/chengwenfang-cola-architecture/SKILL.md`](.cursor/skills/chengwenfang-cola-architecture/SKILL.md)、[`docs/代码结构与注释规范.md`](docs/代码结构与注释规范.md)。

## 环境要求（本机）

| 工具 | 用途 | 说明 |
|------|------|------|
| Docker Desktop（含 Compose） | MySQL / Redis / SearXNG / Lightpanda | 容器依赖见下方「一条命令」 |
| JDK 21、Maven | 跑后端 | 与父 POM `java.version=21` 一致 |
| Node.js（建议 22 LTS+） | 跑 Vite 前端 | jsdom/vitest 需 Node 22+ |
| Python 3.11+ | 正文抽取、视频解析 CLI | Java 用 `ProcessBuilder` 调本机 venv，**不能**只靠 Docker |
| ffmpeg（在 PATH 中） | 视频解析抽音频 / 抽帧 | 同上，须装在跑 Java 的机器上 |

Windows 可用 winget 装部分工具（装完**新开终端**再校验）：

```powershell
winget install Docker.DockerDesktop   # 安装后需启动 Docker Desktop
winget install Microsoft.OpenJDK.21
winget install OpenJS.NodeJS.LTS
winget install Python.Python.3.12
winget install Gyan.FFmpeg
# Maven：自行安装或 winget search maven
```

```powershell
java -version          # 应为 21
mvn -version
node -v
docker compose version
python --version       # 3.11+
ffmpeg -version
```

仓库启停脚本以 **Windows PowerShell** 为主。macOS / Linux：用 Homebrew 等装齐上表工具后，Docker / Maven / npm 命令相同；跳过 `use-utf8.ps1`。`application.yml` 默认 Python 路径是 Windows 的 `.../.venv/Scripts/python.exe`，其它系统需改成 `bin/python`。

## 快速开始（AI 方式）

把下面这份手册交给 AI（Cursor / Codex 等），让它按阶段探测、安装依赖并启动到可访问为止：

**[`docs/AI从零搭建满血成文坊.md`](docs/AI从零搭建满血成文坊.md)**

可对 AI 直接说：

> 请阅读并严格执行仓库内 `docs/AI从零搭建满血成文坊.md`，从零搭建满血成文坊（Docker 全依赖 + Python CLI + ffmpeg + 前后端），每步验收后再继续，直到 `http://127.0.0.1:8001/api/health` 与 `http://127.0.0.1:5173` 可访问；API Key 留给我在设置页填写。

人工逐步操作见下一节「快速开始」。

## 快速开始

下列命令默认在仓库根目录、PowerShell 中执行。

### 0. 终端 UTF-8（Windows）

```powershell
. .\scripts\use-utf8.ps1
```

### 1. Docker：拉起容器依赖

**最小（仅库，课程主流程够用）：**

```powershell
docker compose up -d --wait
```

**全部容器依赖（推荐热点模式）：MySQL + Redis + SearXNG + Lightpanda**

```powershell
docker compose --profile all up -d --wait
```

| 服务 | 镜像 | 端口 | 作用 |
|------|------|------|------|
| mysql | `mysql:8.4` | `3306` | 主库 `chengwenfang`（用户 `chengwenfang` / 密码 `chengwenfang_dev`） |
| redis | `redis:7` | `6379` | Job 租约等 |
| searxng | `searxng/searxng` | `8080` | 热点聚合搜索（来源管理里配置） |
| lightpanda | `lightpanda/browser:nightly` | `127.0.0.1:9222` | JS 页正文回退（`docker exec`） |

单项也可：`docker compose --profile searxng up -d`、`--profile lightpanda`。改 `deploy/searxng/settings.yml` 后需：

```powershell
docker compose --profile searxng up -d --force-recreate searxng
```

说明：Java / 前端 / Python CLI / ffmpeg **当前不在 Compose 里**。应用在宿主机起进程，并调用本机 Python 与（视频场景下的）ffmpeg；强行把 Whisper 等打进镜像需要改调用方式，本仓库尚未提供。

### 2. 本机 Python CLI（热点正文 / 视频）

不做热点雷达正文与视频解析可跳过；跳过后相关 Job 会失败提示，不阻断课程主流程。

**正文抽取（Trafilatura；Lightpanda 回退也依赖此 CLI）：**

```powershell
cd scripts\article-extract
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -U pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
cd ..\..
```

**视频解析（资料池点「解析」时用；另需本机 ffmpeg）：**

```powershell
cd scripts\video-transcript
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -U pip
pip install -r requirements.txt
cd ..\..

ffmpeg -version
yt-dlp --version
python -c "import whisper, PIL, numpy; print('ok')"
```

首次 Whisper 会下载 `base` 模型（约一百多 MB）。更细说明：[`scripts/article-extract/README.md`](scripts/article-extract/README.md)、[`scripts/video-transcript/README.md`](scripts/video-transcript/README.md)。

### 3. 前端依赖（首次或 lock 变更后）

```powershell
npm install
```

### 4. 启动后端 + 前端

等 Compose `--wait` 结束后再起后端（避免 MySQL 未就绪时 Flyway 失败）。

**首次部署还要导入 Skill、内置工作流与排版主题**（只做一次；已有数据会跳过，不会覆盖你改过的提示词）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap\Initialize-ChengwenfangSeed.ps1
```

脚本会用 `bootstrap-seed` 配置启动一次后端：Flyway 建表并导入 `scripts/bootstrap/builtin-catalog.json`，然后退出。未导入就日常启动时，日志会报「基础配置缺失」并提示上述命令，进程仍会起来。说明见 [`scripts/bootstrap/README.md`](scripts/bootstrap/README.md)。

**方式 A — 分终端：**

```powershell
# 终端 1：后端 http://127.0.0.1:8001
mvn -pl chengwenfang-app -am spring-boot:run

# 终端 2：前端 http://127.0.0.1:5173（/api 代理到 8001）
npm run dev:frontend
```

**方式 B — 一键脚本（打包 jar + 起 Vite；仅 Windows）：**

```powershell
# 仅 MySQL/Redis：
powershell -ExecutionPolicy Bypass -File .\scripts\start-java.ps1

# 含 SearXNG + Lightpanda：
powershell -ExecutionPolicy Bypass -File .\scripts\start-java.ps1 -AllDeps

# 其它：-OpenBrowser  -SkipCompose

# 与本机另一成文坊实例并存（后端 8002 / 前端 5174 / Redis DB 1，共用 MySQL:3306）：
powershell -ExecutionPolicy Bypass -File .\scripts\start-java.ps1 -BesidePrivate -SkipCompose
```

首次请先完成步骤 3 的 `npm install`（脚本不会替你装前端依赖）。

### 与另一实例并存（可选）

默认端口为 **8001 / 5173**，Redis **database=0**，适合单独跑本仓库。若本机已有另一套成文坊占用上述端口，可：

| 方式 | 做法 |
|------|------|
| 一键脚本 | `.\scripts\start-java.ps1 -BesidePrivate`（可加 `-SkipCompose` 共用已有 MySQL/Redis） |
| 环境变量 | `SERVER_PORT=8002`、`SPRING_DATA_REDIS_DATABASE=1`、`VITE_DEV_PORT=5174`、`VITE_API_PROXY=http://127.0.0.1:8002` |

MySQL 仍用 `127.0.0.1:3306`，库名保持 **`chengwenfang`**（另一实例若用 `chengwenfang_private` 即可共存）。SearXNG / Lightpanda 也可共用已有容器。

| 地址 | 用途 |
|------|------|
| http://127.0.0.1:5173 | 前端 |
| http://127.0.0.1:8001/api/health | 健康检查 |
| http://127.0.0.1:8001/swagger-ui.html | API 文档（开发；`prod` 关闭） |
| http://127.0.0.1:8001/v3/api-docs | OpenAPI JSON |

库表由 Flyway 在后端启动时迁移。内置 Skill / 工作流不随启动写入，见上方 bootstrap 脚本。IDE Debug：用 `ChengwenfangApplication` launch（已带 UTF-8 `vmArgs`）。

### 5. 首次使用配置

1. 打开前端 → **设置**：配置文本 / 图像（及热点视频画面解析用的）多模态 AI，并做连接测试。  
2. 热点「来源管理」若用 SearXNG：类型选 SearXNG，实例填 `http://127.0.0.1:8080`。  
3. 可选校验 SearXNG：  
   `curl "http://127.0.0.1:8080/search?q=test&format=json"`

停止后端 Java：`.\scripts\stop-java.ps1`（不停 Vite / Docker）。停容器：`docker compose --profile all down`（加 `-v` 会删 MySQL 数据卷，慎用）。

### npm 脚本

| 命令 | 说明 |
|------|------|
| `npm run dev` | 调用 `scripts/start-java.ps1`（仅库，无 `-AllDeps`） |
| `npm run dev:frontend` | 仅 Vite |
| `npm run dev:api` | `mvn package` 后启动 jar |
| `npm run build` | 前端生产构建 |
| `npm test` / `npm run test:backend` | Vitest / Maven 测试 |

## 目录与模块

```text
chengwenfang/
├── chengwenfang-app/              # 启动入口：adapter.web / adapter.worker / application.*
├── chengwenfang-common/           # ApiResult、异常、分页、ErrorCodes 等
├── chengwenfang-infra/            # ChengwenfangProperties、存储路径等
├── chengwenfang-file/             # 文件元数据与本地存储
├── chengwenfang-job/              # 异步任务、租约、事件
├── chengwenfang-ai/               # 文本 / 图像 / 多模态
├── chengwenfang-crawler/          # 采集、搜索、正文抽取门面
├── chengwenfang-content/          # 主题、课程、文章版本
├── chengwenfang-hot-news/         # 热点主题、雷达、选题、资料池
├── chengwenfang-skill-workflow/   # Skill、工作流图
├── chengwenfang-notification/   # 站内通知（P2 迁入）
├── chengwenfang-quality/        # 审核与修复（P2 迁入）
├── chengwenfang-publishing/     # 排版主题、渲染快照、公众号
├── chengwenfang-sourcing/       # 采集与信源（P2 迁入）
├── chengwenfang-mode-course/    # 课程模式（P2 迁入）
├── chengwenfang-mode-hotnews/   # 热点模式（P2 迁入）
├── src/ public/ index.html     # React 前端（Vite）
├── content/                    # 学习内容资源
├── contracts/                  # API / SSE 契约
├── docs/                       # 设计与规范
├── scripts/                    # 启停、UTF-8、article-extract、video-transcript 等
├── deploy/searxng/             # 可选本地 SearXNG 配置
├── compose.yml
├── pom.xml
└── package.json
```

业务 / 平台模块分包：`facade` → `application` → `domain` → `infrastructure`。`chengwenfang-app` 用 `adapter.web` / `adapter.worker` 接 HTTP 与 Job，业务规则不进 Controller。

运行时目录 `.chengwenfang/`（已 gitignore）。文件元数据在表 `stored_files`，业务表存 `file_id`；下载：`GET /api/files/{fileId}`。学习内容同步：`POST /api/migrations/sync-learning-content`。

## 依赖补充说明

安装与启动步骤见上方「快速开始」。补充行为约定：

| 能力 | 依赖 | 未就绪时 |
|------|------|----------|
| 课程主流程 | MySQL + Redis + JDK + Node + AI 配置 | 无法正常开发 |
| 雷达正文 | `article-extract` venv；瀑布 `Jina → Trafilatura → Lightpanda → 摘要` | 正文质量下降 / 抽取失败 |
| Lightpanda 回退 | Compose 起容器；开发配置 `browser-enabled=true` | 跳过浏览器回退 |
| SearXNG 来源 | Compose 起服务 + 来源管理填 `http://127.0.0.1:8080` | 仅该渠道失败 |
| 资料池视频解析 | `video-transcript` venv + PATH 中的 ffmpeg + 多模态 AI | 调研可用，解析失败提示 |

- Lightpanda 镜像 AGPL-3.0，本仓未改源码；不用于绕过验证码 / 登录墙；`skip-hosts` 仍走摘要。  
- 开发 `crawler-allow-loopback=true` 允许采本机 SearXNG；生产请自行关闭（`application-prod.yml` 未自动关）。  
- 配置项：`chengwenfang.article-content.*`、`chengwenfang.video-transcript.*`、`chengwenfang.ai-vision-timeout-seconds`。

## 测试

```powershell
. .\scripts\use-utf8.ps1   # Windows 建议
mvn -pl chengwenfang-app -am test
npm test
```

## 文档

| 路径 | 内容 |
|------|------|
| [`docs/AI从零搭建满血成文坊.md`](docs/AI从零搭建满血成文坊.md) | **给 AI 的从零满血部署手册**（见「快速开始（AI 方式）」） |
| [`docs/design/01-产品需求文档.md`](docs/design/01-产品需求文档.md) | 产品定位 |
| [`docs/design/02-技术架构方案.md`](docs/design/02-技术架构方案.md) | 技术架构 |
| [`docs/代码结构与注释规范.md`](docs/代码结构与注释规范.md) | 分层与注释 |
| [`docs/类命名词典.md`](docs/类命名词典.md) | 类命名 |
| [`contracts/api-sse-contract.md`](contracts/api-sse-contract.md) | API / SSE |
| [`docs/cutover.md`](docs/cutover.md) | 割接说明 |
| [`docs/前端回归清单.md`](docs/前端回归清单.md) | 前端回归 |

更多方案在 `docs/design/`。Java 重写前备份分支：`backup/main-20260907-before-java-rewrite`。

## 配置注意

- API Key 只存本机配置 / 数据库，勿提交，勿写入日志或公开 API。
- 文本 / 配图生成失败直接报错，不再用占位稿或占位图顶替。
- 长任务：`jobs` + 租约 + `job_events` / SSE；前端经 Vite 代理访问 `/api`。
- 启用 `prod` profile 时会关闭 Swagger；环回爬虫、正文 / 视频 Python 路径等仍需按部署环境核对。
- **仅课程 / 满血热点**：`course-only` / `hotnews-full` Profile 见 [`scripts/bootstrap/README.md`](scripts/bootstrap/README.md)；Job 并发槽与 Actuator metrics 说明同页。
