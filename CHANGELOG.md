# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
where releases are tagged.

## [Unreleased]

### Changed

- Rebrand product naming to **Chengwenfang / 成文坊** (modules, packages, docs, bootstrap Skill names).
- Open-source defaults: backend `:8001`, Vite `:5173`, Redis `database=0`; dual-instance via `-BesidePrivate` / env overrides.
- Shared-host friendly: MySQL database name `chengwenfang` (beside private `chengwenfang_private`).
- Remove tracked legacy `.workbench*` runtime assets from the public tree.

### Added

- GitHub Issue / PR templates, Contributor Covenant `CODE_OF_CONDUCT.md`, `.editorconfig`.
- Bootstrap Skill legacy rename:「工作台规则理解」→「成文坊规则理解」on startup / seed import.

## [0.1.0] - 2026-10-26

### Added

- Initial public release under Apache-2.0: Java 21 Spring Boot modular monolith + React/Vite UI.
- Local-first course and hot-news workflows, Docker Compose for MySQL/Redis/(optional) SearXNG/Lightpanda.
- Bootstrap seed script and CI (frontend Vitest + backend Surefire unit tests).
