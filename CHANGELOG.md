# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
where releases are tagged.

## [Unreleased]

### Changed

- README gallery includes the hot-news radar still (`05-hotnews-radar.png`), above the walkthrough GIF.
- README facade: real UI screenshots, course-only quick start first, accurate module tree, optional hot-news deps called out.
- Hot-news README shot shows populated theme metrics; add short walkthrough GIF (themes → radar → sources).
- Document Spring Boot **3.3.5** pin and upgrade policy (root `pom.xml` `spring-boot.version`; patch vs minor rules).
- Document CI gate: Actions runs Vitest + Maven unit tests; excludes `*IT` / fingerprint goldens (run IT locally with Docker).
- Compose MySQL / app secrets come from `.env` (or `${VAR:-dev-default}`); no longer hardcode credentials as the only source.
- Split large theme wizards into `themeWizard/` (hook + step components); pages/components keep re-exports.
- Theme list `itemCount` / `candidateCount` via `ThemeListMetricsContributor` SPI (hot-news MP impl); drop content-module JdbcTemplate cross-table counts.
- `prod` profile: require `CHENGWENFANG_SECRET_KEY`, disable `crawler-allow-loopback`.
- `server.address` overridable via `SERVER_ADDRESS` (Docker sets `0.0.0.0`).

### Added

- `scripts/start.sh` for macOS / Linux course-only (or `--all-deps`) start.
- Backend `Dockerfile` + compose profile `app` (jar only; Vite still on host).
- `.env.example` lists `MYSQL_*` + `CHENGWENFANG_SECRET_KEY` for Compose substitution.
- `docs/screenshots/hotnews-walkthrough.gif` and `05-hotnews-radar.png`.

### Fixed

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
- Soften bootstrap Skill copy that pointed at private knowledge-base paths.
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

- Product naming **Chengwenfang / 成文坊** (modules, packages, docs, bootstrap Skill names).
- Open-source defaults: backend `:8001`, Vite `:5173`, Redis `database=0`, MySQL database `chengwenfang`.
- Dual-instance beside a private clone via `-BesidePrivate` / env overrides.
