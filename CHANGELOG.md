# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
where releases are tagged.

## [Unreleased]

### Changed

- README facade: real UI screenshots, course-only quick start first, accurate module tree, optional hot-news deps called out.
- `prod` profile: require `CHENGWENFANG_SECRET_KEY`, disable `crawler-allow-loopback`.

### Added

- `scripts/start.sh` for macOS / Linux course-only (or `--all-deps`) start.

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
