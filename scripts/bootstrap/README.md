# 提示词包与工作流种子

运行时以 MySQL 为准。空库（还没有提示词包、也没有工作流定义）在应用启动时自动导入本目录的种子，然后继续跑，不退出。已有数据不会被启动过程覆盖。

本目录的脚本留给补种和强制覆盖：缺行时补上，`-Force` / `--force` 时用仓库种子覆盖已有内置行。

## 何时手工执行

自动导入没跑成，或要强制覆盖时再跑（库名见 `application.yml`，默认 `chengwenfang`；与私有实例共用宿主机 MySQL 时勿再起本仓 mysql 容器，确保库已创建即可）：

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap\Initialize-ChengwenfangSeed.ps1
```

```bash
# macOS / Linux（无 PowerShell 时用这个）
chmod +x scripts/bootstrap/Initialize-ChengwenfangSeed.sh
./scripts/bootstrap/Initialize-ChengwenfangSeed.sh
```

脚本会以 `bootstrap-seed` 配置启动一次后端：先跑 Flyway 建表，再导入 `builtin-catalog.json`、`default-themes.json`、`source-channel-catalog.json`（信源沉淀池），然后进程退出。

已存在的同名同场景 提示词包、已发布的内置工作流、同 `code` 的沉淀池渠道会跳过，避免盖掉你在页面上改过的内容。

需要用仓库种子覆盖这些内置行时（会新增一版已发布工作流，并重写对应 提示词包 正文）：

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap\Initialize-ChengwenfangSeed.ps1 -Force
```

```bash
# macOS / Linux
./scripts/bootstrap/Initialize-ChengwenfangSeed.sh --force
```

**热点演示主题**：导入依赖「主要意图」已确认。若尚未在设置页配置可用的 AI API Key，日志可能出现「请先由 AI 分析并确认主题主要意图」——提示词包、工作流、课程主题通常仍已写入；填好 Key 后在热点主题页走一遍意图分析并确认即可，不必整库重来。

## 种子里有什么

`builtin-catalog.json`：

- 课程提示词包（含 global 规则 / 审校 / 配图检查，以及可编辑的「作者风格」）
- 热点成文提示词包、雷达采集提示词包（含热点作者风格等）
- 三条已发布工作流图：课程生成、热点成文、雷达采集（节点上的 提示词包 先按名称占位，导入时换成库内 id；课程图已挂「作者风格」）
- 排版主题：`mdnice · 紫韵 Optima`（稳定 key=`mdnice-ziyun-optima`）

`default-themes.json`（同次导入）：

- 默认课程主题「AI 自学零基础」、热点主题「AI 前沿热点」：问卷与课表草案，确认后落库系列 + **课壳**（无正文生产）
- 已确认主题会跳过，避免覆盖手动改过的结构
- 热点种子主题须已确认主要意图；无 AI Key 时该步可能失败，不影响课程种子与 catalog
- 开源对外导出用同目录 `default-themes.oss.json`（最小 Demo Course / Demo Hot News）；见 `scripts/oss/export-to-public.ps1`

`source-channel-catalog.json`（同次导入）：

- 跨主题信源沉淀池（RSS/Atom 等），带 `ai` / `web3` 场景标签
- 按 `code` 缺则插；`-Force` 时覆盖名称、URL、标签与排序
- 说明见 [`docs/信源沉淀池.md`](../../docs/信源沉淀池.md)

## 更新种子

- **提示词正文**：只维护在 `builtin-catalog.json`（或前端改库）。**不要**在 Java 里嵌 `systemInstructions`。
- **沉淀池渠道**：只维护在 `source-channel-catalog.json`，并保持 `SourceChannelCatalogCodes` / `Tags` 与 JSON 一致。
- 导入后以 MySQL `prompt_packs` / `source_channel_catalog` 表为准。启动只在空库时导入；已改过的提示词包不会被下次启动盖掉。
- 若只刷新三条内置工作流图、保留现有 提示词包 正文，可运行：

```text
java ... com.chengwenfang.prompt.bootstrap.ExportBuiltinCatalog scripts/bootstrap/builtin-catalog.json
```

需要覆盖库内已改过的内置行时，再执行初始化脚本的 `-Force`。

若只想补齐内置工作流**节点**（例如新增 `hot_news_attach_evidence`），不改提示词包正文：

```powershell
# 仓库根目录；MySQL 已就绪（用 fat jar 名 chengwenfang-app.jar）
. .\scripts\use-utf8.ps1
mvn -pl chengwenfang-app -am -DskipTests package
java "-Dfile.encoding=UTF-8" "-Dserver.port=18099" -jar chengwenfang-app\target\chengwenfang-app.jar --spring.profiles.active=sync-builtin-workflows
```

日常启动也会自动核对：缺 builtin 节点则覆盖发布（见 `BuiltinWorkflowGraphSyncRunner`）。

## CLI 配置档：课程 + 知识库 / 满血热点

默认档已经包含 Qdrant。本地不想装 Python CLI 时，可以用 Spring Profile 关掉热点侧开关（与种子导入无关）：

| Profile | 含义 | 启动示例 |
|---------|------|----------|
| `course-only` | 关掉视频解析与 Lightpanda / Trafilatura；适合只跑课程成文和知识库 | `mvn -pl chengwenfang-app -am spring-boot:run -Dspring-boot.run.profiles=course-only` |
| `hotnews-full` | 打开雷达正文瀑布 + 资料池视频解析 | 同上，profiles=`hotnews-full` |

对应文件：`chengwenfang-app/src/main/resources/application-course-only.yml`、`application-hotnews-full.yml`。

**满血热点**请跑 `scripts/setup-hotnews-clis.ps1` 或 `.sh`（`article-extract` / `video-transcript` / `page-screenshot`，含 Chromium 与 PyMuPDF），并 `docker compose --profile all`（SearXNG + Lightpanda；Neo4j 容器可随 all 起来，Graph 开关仍默认关）。解释器按操作系统选择 venv，不必改 `python-executable`。CLI 未就绪时相关 Job 会失败并写中文错误，不会静默用占位正文；截图还要设置里的多模态模型。网页佐证截图对不上定位文本时不会用首屏顶上；PDF 取首页。

Worker 并发（与 Profile 无关）：`worker-concurrency` 为全局上限；`worker-text/image/fetch-concurrency` 为分类型槽，防止出图 / Whisper 饿死短任务。指标见 Actuator `GET /actuator/metrics`（如 `chengwenfang.jobs`、`chengwenfang.jobs.lease.reclaim`、`chengwenfang.jobs.duration`）。
