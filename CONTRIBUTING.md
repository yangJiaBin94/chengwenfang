# Contributing

Thanks for helping improve Chengwenfang. Please keep changes focused and local-first.

## Prerequisites

- JDK 21, Maven, Node.js 22+ (see `package.json` engines), Docker Desktop (Compose)
- On Windows PowerShell, load UTF-8 before Maven/Java:

```powershell
. .\scripts\use-utf8.ps1
```

- Optional hotspot features: run `scripts/setup-hotnews-clis.ps1` (or `.sh`) for
  `article-extract` / `video-transcript` / `page-screenshot`(+Chromium + PyMuPDF),
  plus `ffmpeg` on `PATH` (see README). Same CLI covers Vision fallback and
  article evidence screenshots (PDF first page via PyMuPDF).

## Setup

Default profile is course plus knowledge base (MySQL, Redis, Qdrant). Same entry as the README:

```powershell
. .\scripts\use-utf8.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start-java.ps1
```

```bash
./scripts/start.sh
```

An empty database imports 提示词包, the three main workflows, and demo themes on startup. Re-seed or force overwrite with `scripts/bootstrap/Initialize-ChengwenfangSeed.ps1` or `.sh`. Fill API keys in Settings yourself. Python CLI paths follow the OS venv layout; do not edit `python-executable` for Linux versus Windows.

Hot-news add-on: `start-java.ps1 -AllDeps` or `./scripts/start.sh --all-deps` (`docker compose --profile all` plus the three CLIs). Neo4j may start with that profile; `chengwenfang.knowledge.graph.enabled` stays false.

If `start.sh` nohup dies with the terminal, use two foreground processes after `docker compose up -d --wait`: `mvn -pl chengwenfang-app -am spring-boot:run` and `npm run dev:frontend`.

## Tests

```powershell
npm test
mvn -pl chengwenfang-app -am test
```

Stack: Java 21, Spring Boot 3.3.5 (`spring-boot.version` in the root POM), MyBatis-Plus, Flyway, React 19, Vite, MySQL 8.4, Redis 7, Qdrant. Optional containers: Neo4j, SearXNG, Lightpanda.

**CI gate**: Actions runs Vitest + Maven unit tests only.
`*IT` / `ChengwenfangIT` and `golden/fingerprint/**` are excluded in
`.github/workflows/ci.yml` (Docker/Testcontainers cost + CRLF fingerprint flake).
When you change persistence or HTTP adapters, run the IT suites locally with
Docker Desktop before opening the PR.

## Code of Conduct

Participation is governed by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Pull requests

- Do **not** commit personal learning content under `content/learning-content/`,
  API keys, `.env`, database dumps, or machine-specific paths
- Prefer Apache-2.0 compatible contributions (by submitting a PR you agree your
  contribution is under the same license)
- Match existing COLA module boundaries and Chinese JavaDoc conventions in
  `docs/代码结构与注释规范.md` and `.cursor/skills/chengwenfang-cola-architecture/`
- Keep diffs small; explain *why* in the PR description
- Use the PR template checklist; update [CHANGELOG.md](CHANGELOG.md) under
  `[Unreleased]` for user-visible changes

## Dual-repo note

Maintainers may develop in a private daily-driver clone that holds personal
course data. The public tree must stay free of that content. Use
`scripts/oss/export-to-public.ps1` when syncing outward.
