# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
where releases are tagged.

## [Unreleased]

### Added

- 空库启动自动导入提示词包、三条主工作流与演示主题；`scripts/bootstrap` 仍用于补种和强制覆盖。
- 系统就绪（`GET /api/system/builtin-ready` 与设置面板）增加 Qdrant 真连接、生成 / 嵌入 / 图像 Key、三条 CLI、SearXNG / Lightpanda，以及默认关闭的 Graph / Agent。
- `docs/体验手册.md`：课程、热点加装、知识库、提示词包四条点击路径。
- 知识库 RAG（`chengwenfang-knowledge`）：文档/网页/作品入库、按 profile 切块（`chunk_strategy_version=c2`）、Qdrant Hybrid 检索问答（引用 / 拒答）；可选 Neo4j Graph 与 Agentic（默认关）。
- Compose 默认拉起 **Qdrant**（6333/6334）；`docker compose --profile neo4j`（及 `all`）可选 Neo4j。
- 热点雷达 Top10 / 已确认选题 / 资料池条目 / 主轨原稿登记入库；单文档索引 Job 终态不推站内通知。
- README、`docs/AI从零搭建满血成文坊.md`、`docs/知识库RAG*.md`、`contracts/api-sse-contract.md` 补充知识库部署 / API / 开关说明。
- 热点成文节点 `hot_news_attach_evidence`：审校后由 AI 决定是否截来源佐证图（网页 `--find-text` / PDF 首页 PyMuPDF），插入 `/api/files/…` 并剥正文裸外链；依赖 `scripts/page-screenshot`（Chromium + PyMuPDF）。
- `scripts/start.sh` for macOS / Linux course-only (or `--all-deps`) start.
- Backend `Dockerfile` + compose profile `app` (jar only; Vite still on host).
- `.env.example` lists `MYSQL_*` + `CHENGWENFANG_SECRET_KEY` for Compose substitution.
- `docs/screenshots/hotnews-walkthrough.gif` and `05-hotnews-radar.png`.

### Changed

- 默认安装称为「课程 + 知识库」。`start.sh` / `start-java.ps1` 拉起 MySQL、Redis、Qdrant；`--all-deps` / `-AllDeps` 再拉满血容器并安装三条 Python CLI。Python 路径按操作系统选择 venv。
- README 主路径改为一键脚本，操作顺序改到体验手册。AI 部署手册只给助手执行。
- 产品「Skill」更名为 **PromptPack（提示词包）**：模块 `chengwenfang-prompt-workflow`、API `/api/settings/prompt-packs`、表 `prompt_packs` 等；与 Cursor Agent Skill（`.cursor/skills/`）区分。
- README facade: real UI screenshots (1440×900), course-only quick start first, accurate module tree, optional hot-news deps called out.
- README visuals: hero GIF + course-route under badges; populated radar as feature shot; theme/workflow/settings under details.
- Re-capture README shots via CDP at 1440×900 (no cover-crop); keep full left sidebar; GIF same canvas as stills.
- Document Spring Boot **3.3.5** pin and upgrade policy (root `pom.xml` `spring-boot.version`; patch vs minor rules).
- Document CI gate: Actions runs Vitest + Maven unit tests; excludes `*IT` / fingerprint goldens (run IT locally with Docker).
- Compose MySQL / app secrets come from `.env` (or `${VAR:-dev-default}`); no longer hardcode credentials as the only source.
- Split large theme wizards into `themeWizard/` (hook + step components); pages/components keep re-exports.
- Theme list `itemCount` / `candidateCount` via `ThemeListMetricsContributor` SPI (hot-news MP impl); drop content-module JdbcTemplate cross-table counts.
- `prod` profile: require `CHENGWENFANG_SECRET_KEY`, disable `crawler-allow-loopback`.
- `server.address` overridable via `SERVER_ADDRESS` (Docker sets `0.0.0.0`).

### Fixed

- 空库自动种子在热点演示主题尚未确认主要意图时仍导入信源沉淀池。是否重跑只看提示词包和工作流是否都为空，不看 Flyway 已写入的沉淀池基线。
- 知识库反馈表补上列注释；视频抽帧 JSON 列纳入结构规范白名单。
- `app` 容器依赖并连接 Compose 服务 `qdrant`。Qdrant 已启用但连不上时启动失败，不再静默回退内存向量库。
- AI settings connection test uses the on-page form draft (not saved DB config); blank apiKey still falls back to the stored secret.
- Radar collect persists per-channel `channelRuns` so coverage and channel breakdown are available after a run (including failure fallback).
- Theme cards show real hot-news source-item and candidate-topic counts instead of hardcoded zeros.
- Hot-news header: label daily Top 10 writing score; omit coverage % when `channelRuns` are empty.
- App favicon / brand mark use the green theme assets.
- Dockerfile: put `# syntax=docker/dockerfile:1` on line 1; copy version-stable `chengwenfang-app.jar` (`finalName`).
- Clarify compose `app` / README: local-dev secrets are env-injected defaults, not a prod template.

### Removed

- Unused `public/screenshot-placeholder.svg`.

## [0.1.1] - 2026-10-27

### Changed

- Trim public docs to deploy / contributor essentials; remove obsolete design and audit drafts.
- Align AI setup guide Node requirement with `package.json` (`engines.node >= 22`).
- Soften bootstrap 提示词包 copy that pointed at private knowledge-base paths.
- Expand `SECURITY.md` with crawling / sourced-content boundaries.

### Added

- `.gitignore` guards for `content/learning-content/`, `public/content/`, and related personal assets.
- Dependabot config for GitHub Actions, npm, and Maven.
- Docs index at `docs/README.md`.

### Fixed

- Remove abandoned empty `chengwenfang-hot-news/` shell (logic lives in `chengwenfang-mode-hotnews`).

## [0.1.0] - 2026-10-26

### Added

- Initial public release under Apache-2.0: Java 21 Spring Boot modular monolith + React/Vite UI.
- Local-first course and hot-news workflows, Docker Compose for MySQL/Redis/(optional) SearXNG/Lightpanda.
- Bootstrap seed script and CI (frontend Vitest + backend Surefire unit tests).
- GitHub Issue / PR templates, Contributor Covenant `CODE_OF_CONDUCT.md`, `.editorconfig`.

### Changed

- Product naming **Chengwenfang / 成文坊** (modules, packages, docs, bootstrap 提示词包 names).
- Open-source defaults: backend `:8001`, Vite `:5173`, Redis `database=0`, MySQL database `chengwenfang`.
- Dual-instance beside a private clone via `-BesidePrivate` / env overrides.
