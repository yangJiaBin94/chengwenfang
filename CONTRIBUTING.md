# Contributing

Thanks for helping improve Workbench. Please keep changes focused and local-first.

## Prerequisites

- JDK 21, Maven, Node.js 20+, Docker Desktop (Compose)
- On Windows PowerShell, load UTF-8 before Maven/Java:

```powershell
. .\scripts\use-utf8.ps1
```

- Optional hotspot features need Python 3.11+ venvs under `scripts/article-extract`
  and `scripts/video-transcript`, plus `ffmpeg` on `PATH` (see README)

## Setup

```powershell
docker compose up -d --wait
npm install
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap\Initialize-WorkbenchSeed.ps1
mvn -pl workbench-app -am spring-boot:run
# other terminal
npm run dev:frontend
```

## Tests

```powershell
npm test
mvn -pl workbench-app -am test
```

Integration tests that need Docker/Testcontainers may be skipped in CI; run them
locally when you change persistence or HTTP adapters.

## Pull requests

- Do **not** commit personal learning content under `content/learning-content/`,
  API keys, `.env`, database dumps, or machine-specific paths
- Prefer Apache-2.0 compatible contributions (by submitting a PR you agree your
  contribution is under the same license)
- Match existing COLA module boundaries and Chinese JavaDoc conventions in
  `docs/代码结构与注释规范.md` and `.cursor/skills/workbench-cola-architecture/`
- Keep diffs small; explain *why* in the PR description

## Dual-repo note

Maintainers may develop in a private daily-driver clone that holds personal
course data. The public tree must stay free of that content. Use
`scripts/oss/export-to-public.ps1` when syncing outward.
