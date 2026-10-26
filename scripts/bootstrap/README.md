# 首次部署：Skill 与工作流种子

运行时以 MySQL 为准。应用启动**不会**把代码里的提示词或流程图写回数据库，只做只读体检；缺配置时在日志里提示执行本脚本。

## 何时执行

MySQL 已启动（`docker compose up -d`）之后、日常使用之前，执行一次：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap\Initialize-WorkbenchSeed.ps1
```

脚本会以 `bootstrap-seed` 配置启动一次后端：先跑 Flyway 建表，再导入 `builtin-catalog.json`，然后进程退出。

已存在的同名同场景 Skill、已发布的内置工作流会跳过，避免盖掉你在页面上改过的内容。

需要用仓库种子覆盖这些内置行时（会新增一版已发布工作流，并重写对应 Skill 正文）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap\Initialize-WorkbenchSeed.ps1 -Force
```

## 种子里有什么

`builtin-catalog.json`：

- 课程 Skill（含 global 规则 / 审校 / 配图检查，以及可编辑的「作者风格」）
- 热点成文 Skill、雷达采集 Skill（含热点作者风格等）
- 三条已发布工作流图：课程生成、热点成文、雷达采集（节点上的 Skill 先按名称占位，导入时换成库内 id；课程图已挂「作者风格」）
- 排版主题：`mdnice · 紫韵 Optima`（稳定 key=`mdnice-ziyun-optima`）

`default-themes.json`（同次导入）：

- 默认课程主题 / 热点主题：问卷与课表草案，确认后落库系列 + **课壳**（无正文生产）
- 已确认主题会跳过，避免覆盖手动改过的结构
- 开源导出使用同目录 `default-themes.oss.json`（最小 Demo Course / Demo Hot News）；见 `scripts/oss/export-to-public.ps1`

## 更新种子

- **提示词正文**：只维护在 `builtin-catalog.json`（或前端改库）。**不要**在 Java 里嵌 `systemInstructions`。
- 导入后以 MySQL `skills` 表为准；应用启动不会把代码提示词写回库。
- 若只刷新三条内置工作流图、保留现有 Skill 正文，可运行：

```text
java ... com.workbench.skill.bootstrap.ExportBuiltinCatalog scripts/bootstrap/builtin-catalog.json
```

需要覆盖库内已改过的内置行时，再执行初始化脚本的 `-Force`。

## CLI 配置档：仅课程 / 满血热点

本地不想一次装齐 Python CLI 时，可用 Spring Profile 选档（与种子导入无关，改的是运行时开关）：

| Profile | 含义 | 启动示例 |
|---------|------|----------|
| `course-only` | 关掉视频解析与 Lightpanda / Trafilatura；适合只跑课程成文 | `mvn -pl workbench-app -am spring-boot:run -Dspring-boot.run.profiles=course-only` |
| `hotnews-full` | 打开雷达正文瀑布 + 资料池视频解析 | 同上，profiles=`hotnews-full` |

对应文件：`workbench-app/src/main/resources/application-course-only.yml`、`application-hotnews-full.yml`。

**满血热点**仍须按仓库 README 装好 `article-extract` / `video-transcript` venv（及可选 Lightpanda）。CLI 未就绪时相关 Job 会失败并写中文错误，不会静默用占位正文。

Worker 并发（与 Profile 无关）：`worker-concurrency` 为全局上限；`worker-text/image/fetch-concurrency` 为分类型槽，防止出图 / Whisper 饿死短任务。指标见 Actuator `GET /actuator/metrics`（如 `workbench.jobs`、`workbench.jobs.lease.reclaim`、`workbench.jobs.duration`）。
